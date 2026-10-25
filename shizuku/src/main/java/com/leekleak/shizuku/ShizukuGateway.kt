package com.leekleak.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.leekleak.batteryarcheologist.core.model.ShizukuState
import com.leekleak.batteryarcheologist.core.model.ShellResult
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

/**
 * Single entry point for Shizuku state and privileged shell execution.
 *
 * Commands run inside a Shizuku user service (process UID 2000 in ADB mode),
 * not inside the app process. Output is streamed back in binder-safe chunks.
 */
class ShizukuGateway(
    context: Context,
    debuggable: Boolean,
    versionCode: Int,
    private val maxOutputBytes: Int = 32 * 1024 * 1024,
) {
    private val appContext = context.applicationContext

    private val _state = MutableStateFlow<ShizukuState>(ShizukuState.Unknown)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private val _serviceConnected = MutableStateFlow(false)

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(appContext.packageName, BatteryArcheologistService::class.java.name),
    )
        .processNameSuffix("battery_archeologist")
        .debuggable(debuggable)
        .version(versionCode)
        .daemon(false)

    private var service: IBatteryArcheologistService? = null
    private var serviceDeferred = CompletableDeferred<IBatteryArcheologistService>()
    @Volatile
    private var binding = false
    @Volatile
    private var closed = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null || !binder.pingBinder()) {
                _serviceConnected.value = false
                return
            }
            val connected = IBatteryArcheologistService.Stub.asInterface(binder)
            service = connected
            binding = false
            _serviceConnected.value = true
            if (!serviceDeferred.isCompleted) serviceDeferred.complete(connected)
            Timber.i("Shizuku user service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            binding = false
            _serviceConnected.value = false
            if (serviceDeferred.isCompleted) {
                serviceDeferred = CompletableDeferred()
            }
            Timber.w("Shizuku user service disconnected")
            if (!closed) refresh()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refresh()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        service = null
        binding = false
        _serviceConnected.value = false
        if (serviceDeferred.isCompleted) serviceDeferred = CompletableDeferred()
        _state.value = ShizukuState.NotRunning
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == PERMISSION_REQUEST_CODE) {
            Timber.i("Shizuku permission result: %d", grantResult)
            refresh()
        }
    }

    init {
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionListener)
        }.onFailure { Timber.w(it, "Unable to register Shizuku listeners") }
        refresh()
    }

    fun refresh() {
        val next = runCatching {
            when {
                !Shizuku.pingBinder() -> ShizukuState.NotRunning
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ->
                    ShizukuState.Ready(Shizuku.getUid())
                else -> ShizukuState.PermissionDenied
            }
        }.getOrElse {
            Timber.w(it, "Unable to read Shizuku state")
            ShizukuState.NotRunning
        }
        _state.value = next
        if (next is ShizukuState.Ready) bindService()
    }

    fun isShizukuInstalled(): Boolean = runCatching {
        appContext.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun requestPermission(): Boolean {
        return runCatching {
            if (!Shizuku.pingBinder()) {
                false
            } else {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
                true
            }
        }.getOrElse {
            Timber.w(it, "Unable to request Shizuku permission")
            false
        }
    }

    fun openShizuku(): Boolean {
        val intent = appContext.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    suspend fun captureCheckin(): ShellResult = runShell(CHECKIN_COMMAND, timeoutSeconds = 180)

    suspend fun resetBatteryStats(): ShellResult = runShell(RESET_COMMAND, timeoutSeconds = 60)

    suspend fun runShell(
        command: String,
        timeoutSeconds: Long = 60,
    ): ShellResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val service = awaitService()
        if (service == null) {
            return@withContext ShellResult(
                command = command,
                exitCode = -1,
                stdout = "",
                stderr = if (_state.value is ShizukuState.PermissionDenied) {
                    "Shizuku permission is not granted"
                } else {
                    "Shizuku user service is not connected"
                },
                durationMs = System.currentTimeMillis() - started,
            )
        }

        val completion = CompletableDeferred<Int>()
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val lock = Any()
        var truncated = false

        val callback = object : IOutputCallback.Stub() {
            override fun onOutput(chunk: String?) {
                if (chunk == null) return
                synchronized(lock) {
                    val remaining = maxOutputBytes - stdout.length
                    if (remaining <= 0) {
                        truncated = true
                    } else if (chunk.length > remaining) {
                        stdout.append(chunk, 0, remaining)
                        truncated = true
                    } else {
                        stdout.append(chunk)
                    }
                }
            }

            override fun onError(chunk: String?) {
                if (chunk == null) return
                synchronized(lock) {
                    if (stderr.length < MAX_STDERR_CHARS) {
                        stderr.append(chunk.take(MAX_STDERR_CHARS - stderr.length))
                    }
                }
            }

            override fun onComplete(exitCode: Int) {
                if (!completion.isCompleted) completion.complete(exitCode)
            }
        }

        val result = runCatching { service.runShell(command, callback) }
        if (result.isFailure) {
            val error = result.exceptionOrNull()
            return@withContext ShellResult(
                command = command,
                exitCode = -1,
                stdout = "",
                stderr = error?.message ?: "Failed to start user service command",
                durationMs = System.currentTimeMillis() - started,
            )
        }

        val exitCode = withTimeoutOrNull(TimeUnit.SECONDS.toMillis(timeoutSeconds).milliseconds) {
            completion.await()
        }
        if (exitCode == null) {
            return@withContext ShellResult(
                command = command,
                exitCode = -2,
                stdout = synchronized(lock) { stdout.toString() },
                stderr = "Timed out after ${timeoutSeconds}s",
                durationMs = System.currentTimeMillis() - started,
                truncated = truncated,
            )
        }

        ShellResult(
            command = command,
            exitCode = exitCode,
            stdout = synchronized(lock) { stdout.toString() },
            stderr = synchronized(lock) { stderr.toString() }.trim(),
            durationMs = System.currentTimeMillis() - started,
            truncated = truncated,
        )
    }

    private fun bindService() {
        if (closed || binding || service != null) return
        binding = true
        runCatching {
            Shizuku.bindUserService(userServiceArgs, connection)
        }.onFailure {
            binding = false
            Timber.w(it, "Unable to bind Shizuku user service")
        }
    }

    private suspend fun awaitService(): IBatteryArcheologistService? {
        service?.let { return it }
        if (_state.value !is ShizukuState.Ready) return null
        bindService()
        return withTimeoutOrNull(SERVICE_BIND_TIMEOUT_MS) { serviceDeferred.await() }
    }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val PERMISSION_REQUEST_CODE = 42117
        private val SERVICE_BIND_TIMEOUT_MS = 15_000L.milliseconds
        private const val MAX_STDERR_CHARS = 1_000_000
        private const val CHECKIN_COMMAND = "dumpsys batterystats -c"
        private const val RESET_COMMAND = "dumpsys batterystats --reset"
    }
}
