package com.leekleak.batteryarcheologist.ui.state

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leekleak.batteryarcheologist.core.model.ShizukuState
import com.leekleak.batteryarcheologist.data.PinnedAppsStore
import com.leekleak.batteryarcheologist.data.ReportRepository
import com.leekleak.batteryarcheologist.data.SavedReport
import com.leekleak.batteryarcheologist.ui.navigation.ContentTab
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns the app's data and actions.
 *
 * The reports home is the navigation root and needs no privileged access.
 * Capture and import are explicit actions that stay on the home screen; the
 * view model only loads report contents when Navigation 3 opens the viewer
 * destination via [ensureReport].
 */
class MainViewModel(
    private val repository: ReportRepository,
    private val shizukuState: StateFlow<ShizukuState>,
    private val shizukuInstalled: () -> Boolean,
    private val requestShizukuPermission: () -> Unit,
    private val openShizuku: () -> Unit,
    private val refreshShizuku: () -> Unit,
    private val pinnedAppsStore: PinnedAppsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AppUiState(
            shizukuState = shizukuState.value,
            shizukuInstalled = installedOr(false),
            pinnedApps = pinnedAppsStore.load(),
        ),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects: Flow<UiEffect> = _effects.receiveAsFlow()

    private var loadJob: Job? = null
    private var loadingName: String? = null

    init {
        viewModelScope.launch { refreshSavedReports() }
        viewModelScope.launch {
            shizukuState.collect { current ->
                _state.update {
                    it.copy(
                        shizukuState = current,
                        shizukuInstalled = installedOr(it.shizukuInstalled),
                    )
                }
                if (current is ShizukuState.Ready) {
                    // The host ignores this when the permission destination is
                    // not on top of the back stack.
                    emitEffect(UiEffect.ClosePermission)
                    val pending = _state
                        .getAndUpdate { it.copy(pendingAction = null) }
                        .pendingAction
                    when (pending) {
                        PrivilegedAction.CAPTURE -> runCapture()
                        PrivilegedAction.RESET -> runReset()
                        null -> Unit
                    }
                }
            }
        }
    }

    /** Selects the tab shown by the report viewer. */
    fun selectTab(tab: ContentTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    /**
     * Pins or unpins [packageName] across every report.
     *
     * The pin set is global rather than per report so the same apps stay at the
     * top when the user switches recordings and compares their power values.
     */
    fun togglePinnedApp(packageName: String) {
        if (packageName.isBlank()) return
        val pinned = _state.updateAndGet { state ->
            val pins = if (packageName in state.pinnedApps) {
                state.pinnedApps - packageName
            } else {
                state.pinnedApps + packageName
            }
            state.copy(pinnedApps = pins)
        }.pinnedApps
        pinnedAppsStore.save(pinned)
    }

    /**
     * Loads the saved report [name] for the viewer destination.
     *
     * Re-entering the same destination is a no-op while its report is already
     * loaded (or loading), so navigation recomposition does not re-parse.
     */
    fun ensureReport(name: String) {
        val current = _state.value
        val alreadyLoaded = current.report?.savedName == name &&
            current.reportError == null &&
            !(current.reportLoading && loadingName != name)
        if (alreadyLoaded) return
        if (current.reportLoading && loadingName == name) return

        loadJob?.cancel()
        loadingName = name
        _state.update {
            it.copy(
                report = null,
                reportLoading = true,
                reportError = null,
                rawSheet = null,
            )
        }
        loadJob = viewModelScope.launch {
            val result = repository.load(name)
            loadingName = null
            val report = result.getOrNull()
            if (report != null) {
                _state.update {
                    it.copy(
                        report = report,
                        reportLoading = false,
                        selectedTab = ContentTab.TIMELINE,
                    )
                }
            } else {
                val error = result.exceptionOrNull()
                val message = error?.message?.takeIf { it.isNotBlank() }
                    ?: "Could not open report $name"
                Timber.w(error, "Unable to open report %s", name)
                _state.update { it.copy(reportLoading = false, reportError = message) }
                emitEffect(UiEffect.Message(message))
            }
        }
    }

    /**
     * Finishes the viewer destination.
     *
     * The parsed report is deliberately kept: the pop transition still renders
     * the outgoing viewer for a frame, and re-opening the same report should be
     * instant. [ensureReport] replaces it when a different report is opened.
     */
    fun closeReport() {
        loadJob?.cancel()
        loadJob = null
        loadingName = null
        _state.update {
            it.copy(
                reportLoading = false,
                reportError = null,
                rawSheet = null,
                selectedTab = ContentTab.TIMELINE,
            )
        }
    }

    /** Captures a new report, or asks for Shizuku first when it is not ready. */
    fun requestNewReport() {
        if (_state.value.isBusy) return
        if (_state.value.shizukuState is ShizukuState.Ready) {
            runCapture()
        } else {
            _state.update { it.copy(pendingAction = PrivilegedAction.CAPTURE) }
            emitEffect(UiEffect.OpenPermission)
        }
    }

    /** Resets the system battery stats, or asks for Shizuku first. */
    fun requestReset() {
        if (_state.value.isBusy) return
        if (_state.value.shizukuState is ShizukuState.Ready) {
            runReset()
        } else {
            _state.update { it.copy(pendingAction = PrivilegedAction.RESET) }
            emitEffect(UiEffect.OpenPermission)
        }
    }

    /** Opens the permission destination without a deferred action. */
    fun requestPermissionScreen() {
        _state.update { it.copy(pendingAction = null) }
        emitEffect(UiEffect.OpenPermission)
    }

    /** Drops a deferred privileged action when the permission screen is left. */
    fun cancelPermission() {
        _state.update { it.copy(pendingAction = null) }
    }

    fun importReport(uri: Uri) {
        runImport(uri)
    }

    /** Loads the raw checkin for the sheet; `null` means the unsaved latest report. */
    fun requestRaw(name: String?) {
        if (name == null) {
            val report = _state.value.report ?: return
            _state.update {
                it.copy(
                    rawSheet = RawSheetState(
                        raw = report.raw,
                        name = null,
                        sizeBytes = report.sizeBytes.toLong(),
                        source = null,
                    ),
                )
            }
            return
        }

        viewModelScope.launch {
            val result = repository.raw(name)
            val raw = result.getOrNull()
            if (raw == null) {
                val error = result.exceptionOrNull()
                val message = error?.message?.takeIf { it.isNotBlank() }
                    ?: "Could not read saved report $name"
                Timber.w(error, "Unable to read raw report %s", name)
                emitEffect(UiEffect.Message(message))
                return@launch
            }

            val saved = _state.value.savedReports.firstOrNull { it.name == name }
            _state.update {
                it.copy(
                    rawSheet = RawSheetState(
                        raw = raw,
                        name = name,
                        sizeBytes = saved?.sizeBytes
                            ?: raw.toByteArray(Charsets.UTF_8).size.toLong(),
                        source = saved?.source,
                    ),
                )
            }
        }
    }

    fun dismissRawSheet() {
        _state.update { it.copy(rawSheet = null) }
    }

    fun deleteReport(name: String) {
        viewModelScope.launch {
            val deleted = repository.delete(name)
            if (!deleted) {
                Timber.w("Unable to delete report %s", name)
                emitEffect(UiEffect.Message("Could not delete report $name"))
            } else if (_state.value.report?.savedName == name) {
                _state.update { it.copy(report = null) }
            }
            refreshSavedReports()
        }
    }

    fun shareReport(name: String) {
        emitEffect(UiEffect.ShareReport(name))
    }

    fun requestShizukuPermission() {
        requestShizukuPermission.invoke()
    }

    fun openShizuku() {
        openShizuku.invoke()
    }

    /**
     * Re-reads Shizuku availability as well as its state, so installing or
     * starting Shizuku outside the app updates the banner even when the
     * gateway's flow stays on the same value.
     */
    fun refreshShizuku() {
        refreshShizuku.invoke()
        _state.update {
            it.copy(shizukuInstalled = installedOr(it.shizukuInstalled))
        }
    }

    private fun runCapture() {
        if (_state.value.isBusy) return
        _state.update {
            it.copy(captureStatus = CaptureStatus.Running(importing = false))
        }
        viewModelScope.launch {
            val result = repository.capture()
            val report = result.getOrNull()
            if (report != null) {
                if (report.savedName != null) {
                    _state.update {
                        it.copy(report = null, captureStatus = CaptureStatus.Idle)
                    }
                    emitEffect(UiEffect.Message("Report captured"))
                } else {
                    // Keep the parsed raw text available even when the copy on
                    // disk failed, so the home can still show it.
                    _state.update {
                        it.copy(report = report, captureStatus = CaptureStatus.Idle)
                    }
                    emitEffect(
                        UiEffect.Message("Report captured but could not be saved"),
                    )
                }
                refreshSavedReports()
            } else {
                val error = result.exceptionOrNull()
                val message = error?.message?.takeIf { it.isNotBlank() }
                    ?: "Capture failed"
                Timber.w(error, "Capture failed")
                _state.update { it.copy(captureStatus = CaptureStatus.Failed(message)) }
                emitEffect(UiEffect.Message(message))
            }
        }
    }

    private fun runImport(uri: Uri) {
        if (_state.value.isBusy) return
        _state.update {
            it.copy(captureStatus = CaptureStatus.Running(importing = true))
        }
        viewModelScope.launch {
            val result = repository.`import`(uri)
            val report = result.getOrNull()
            if (report != null) {
                if (report.savedName != null) {
                    _state.update {
                        it.copy(report = null, captureStatus = CaptureStatus.Idle)
                    }
                    emitEffect(UiEffect.Message("Report imported"))
                } else {
                    _state.update {
                        it.copy(report = report, captureStatus = CaptureStatus.Idle)
                    }
                    emitEffect(
                        UiEffect.Message("Report imported but could not be saved"),
                    )
                }
                refreshSavedReports()
            } else {
                val error = result.exceptionOrNull()
                val message = error?.message?.takeIf { it.isNotBlank() }
                    ?: "Import failed"
                Timber.w(error, "Import failed")
                _state.update { it.copy(captureStatus = CaptureStatus.Failed(message)) }
                emitEffect(UiEffect.Message(message))
            }
        }
    }

    private fun runReset() {
        if (_state.value.isBusy) return
        _state.update { it.copy(resetStatus = ResetStatus.Running) }
        viewModelScope.launch {
            val result = repository.resetBatteryStats()
            val error = result.exceptionOrNull()
            if (error == null) {
                _state.update { it.copy(resetStatus = ResetStatus.Idle) }
                emitEffect(UiEffect.Message("System battery stats reset."))
            } else {
                val message = error.message?.takeIf { it.isNotBlank() }
                    ?: "Reset failed"
                Timber.w(error, "Battery stats reset failed")
                _state.update { it.copy(resetStatus = ResetStatus.Failed(message)) }
                emitEffect(
                    UiEffect.Message("Could not reset system battery stats: $message"),
                )
            }
        }
    }

    private suspend fun refreshSavedReports() {
        val reports: List<SavedReport> = repository.listReports()
        _state.update { it.copy(savedReports = reports) }
    }

    private fun installedOr(fallback: Boolean): Boolean =
        runCatching(shizukuInstalled).getOrDefault(fallback)

    private fun emitEffect(effect: UiEffect) {
        _effects.trySend(effect)
    }
}
