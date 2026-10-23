package com.leekleak.shizuku

import android.os.RemoteException
import kotlin.system.exitProcess

/**
 * Runs inside the Shizuku user-service process, which has shell (or root) UID.
 * It executes shell commands and streams the output back to the app in chunks.
 */
class BatteryArcheologistService : IBatteryArcheologistService.Stub() {
    @Volatile
    private var running = true

    override fun destroy() {
        running = false
        exitProcess(0)
    }

    override fun runShell(command: String, callback: IOutputCallback?) {
        if (callback == null) return
        Thread({ execute(command, callback) }, "battery-shell").start()
    }

    private fun execute(command: String, callback: IOutputCallback) {
        try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(false)
                .start()

            val stdoutThread = Thread { pump(process.inputStream, callback, output = true) }
                .apply { start() }
            val stderrThread = Thread { pump(process.errorStream, callback, output = false) }
                .apply { start() }

            val exitCode = process.waitFor()
            stdoutThread.join()
            stderrThread.join()
            safeComplete(callback, exitCode)
        } catch (t: Throwable) {
            safeError(callback, t.message ?: t.javaClass.simpleName)
            safeComplete(callback, -1)
        }
    }

    private fun pump(
        stream: java.io.InputStream,
        callback: IOutputCallback,
        output: Boolean,
    ) {
        val buffer = CharArray(16 * 1024)
        try {
            java.io.InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                while (running) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    val text = String(buffer, 0, read)
                    if (output) safeOutput(callback, text) else safeError(callback, text)
                }
            }
        } catch (_: Throwable) {
            // The app may have gone away; nothing useful to do in this process.
        }
    }

    private fun safeOutput(callback: IOutputCallback, text: String) {
        try {
            callback.onOutput(text)
        } catch (_: RemoteException) {
            running = false
        }
    }

    private fun safeError(callback: IOutputCallback, text: String) {
        try {
            callback.onError(text)
        } catch (_: RemoteException) {
            running = false
        }
    }

    private fun safeComplete(callback: IOutputCallback, exitCode: Int) {
        try {
            callback.onComplete(exitCode)
        } catch (_: RemoteException) {
            // App is gone.
        }
    }
}
