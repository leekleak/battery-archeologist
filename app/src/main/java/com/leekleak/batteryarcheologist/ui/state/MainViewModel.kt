package com.leekleak.batteryarcheologist.ui.state

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leekleak.batteryarcheologist.core.model.ShizukuState
import com.leekleak.batteryarcheologist.data.ReportData
import com.leekleak.batteryarcheologist.data.ReportRepository
import com.leekleak.batteryarcheologist.data.SavedReport
import com.leekleak.batteryarcheologist.ui.navigation.ContentTab
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns the main screen's data and actions.
 *
 * Reports are never captured automatically. On launch the most recent saved
 * report is loaded once, after Shizuku becomes ready; capture and import are
 * always explicit user actions.
 */
class MainViewModel(
    private val repository: ReportRepository,
    private val shizukuState: StateFlow<ShizukuState>,
    private val shizukuInstalled: () -> Boolean,
    private val requestShizukuPermission: () -> Unit,
    private val openShizuku: () -> Unit,
    private val refreshShizuku: () -> Unit,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AppUiState(
            shizukuState = shizukuState.value,
            shizukuInstalled = runCatching(shizukuInstalled).getOrDefault(false),
            selectedTab = restoredTab(),
        ),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects: Flow<UiEffect> = _effects.receiveAsFlow()

    private var initialLoadStarted = false

    init {
        viewModelScope.launch { refreshSavedReports() }
        viewModelScope.launch {
            shizukuState.collect { current ->
                _state.update {
                    it.copy(
                        shizukuState = current,
                        shizukuInstalled = runCatching(shizukuInstalled)
                            .getOrDefault(it.shizukuInstalled),
                    )
                }
                if (current is ShizukuState.Ready &&
                    !initialLoadStarted &&
                    _state.value.report == null
                ) {
                    initialLoadStarted = true
                    loadInitialReportInternal()
                }
            }
        }
    }

    /**
     * Loads the last opened report, or the newest saved report when there is no
     * persisted name. Does nothing when the device has no saved reports and
     * never captures on its own.
     */
    fun loadInitialReport() {
        viewModelScope.launch { loadInitialReportInternal() }
    }

    fun selectTab(tab: ContentTab) {
        _state.update { it.copy(selectedTab = tab) }
        rememberSelectedTab(tab)
    }

    fun capture() {
        if (_state.value.captureStatus is CaptureStatus.Running || _state.value.reportLoading) return
        _state.update { it.copy(captureStatus = CaptureStatus.Running) }
        viewModelScope.launch {
            val result = repository.capture()
            val report = result.getOrNull()
            if (report != null) {
                _state.update {
                    it.copy(report = report, captureStatus = CaptureStatus.Idle)
                }
                rememberReport(report)
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

    fun openReport(name: String) {
        if (_state.value.reportLoading || _state.value.captureStatus is CaptureStatus.Running) return
        _state.update { it.copy(reportLoading = true) }
        viewModelScope.launch {
            val result = repository.load(name)
            val report = result.getOrNull()
            if (report != null) {
                _state.update {
                    it.copy(
                        report = report,
                        reportLoading = false,
                        selectedTab = ContentTab.OVERVIEW,
                    )
                }
                rememberSelectedTab(ContentTab.OVERVIEW)
                rememberReport(report)
                refreshSavedReports()
            } else {
                val error = result.exceptionOrNull()
                Timber.w(error, "Unable to open report %s", name)
                _state.update { it.copy(reportLoading = false) }
                emitEffect(
                    UiEffect.Message(
                        error?.message?.takeIf { it.isNotBlank() }
                            ?: "Could not open report $name",
                    ),
                )
            }
        }
    }

    fun importReport(uri: Uri) {
        if (_state.value.captureStatus is CaptureStatus.Running || _state.value.reportLoading) return
        _state.update { it.copy(captureStatus = CaptureStatus.Running) }
        viewModelScope.launch {
            val result = repository.`import`(uri)
            val report = result.getOrNull()
            if (report != null) {
                _state.update {
                    it.copy(
                        report = report,
                        captureStatus = CaptureStatus.Idle,
                        selectedTab = ContentTab.OVERVIEW,
                    )
                }
                rememberSelectedTab(ContentTab.OVERVIEW)
                rememberReport(report)
                refreshSavedReports()
            } else {
                val error = result.exceptionOrNull()
                val detail = error?.message?.takeIf { it.isNotBlank() }
                    ?: "The selected file could not be imported"
                Timber.w(error, "Import failed")
                _state.update {
                    it.copy(captureStatus = CaptureStatus.Failed("Import failed: $detail"))
                }
                emitEffect(UiEffect.Message("Import failed: $detail"))
            }
        }
    }

    fun dismissCaptureError() {
        _state.update { current ->
            if (current.captureStatus is CaptureStatus.Failed) {
                current.copy(captureStatus = CaptureStatus.Idle)
            } else {
                current
            }
        }
    }

    fun deleteReport(name: String) {
        viewModelScope.launch {
            val deleted = runCatching { repository.delete(name) }
                .onFailure { Timber.w(it, "Unable to delete report %s", name) }
                .getOrDefault(false)
            if (deleted) {
                if (_state.value.report?.savedName == name) {
                    _state.update { it.copy(report = null) }
                    savedStateHandle.remove<String>(KEY_LAST_REPORT)
                }
            } else {
                emitEffect(UiEffect.Message("Could not delete report $name"))
            }
            refreshSavedReports()
        }
    }

    fun shareReport(name: String) {
        emitEffect(UiEffect.ShareReport(name))
    }

    fun refreshReports() {
        viewModelScope.launch { refreshSavedReports() }
    }

    fun requestShizukuPermission() {
        requestShizukuPermission.invoke()
    }

    fun openShizuku() {
        openShizuku.invoke()
    }

    fun refreshShizuku() {
        refreshShizuku.invoke()
    }

    private suspend fun loadInitialReportInternal() {
        _state.update { it.copy(reportLoading = true) }
        val persistedName = savedStateHandle.get<String>(KEY_LAST_REPORT)
        val targetName = persistedName
            ?: repository.listReports().firstOrNull()?.name

        if (targetName == null) {
            _state.update { it.copy(reportLoading = false) }
            return
        }

        val result = repository.load(targetName)
        val report = result.getOrNull()
        if (report != null) {
            _state.update { it.copy(report = report, reportLoading = false) }
            rememberReport(report)
        } else {
            val error = result.exceptionOrNull()
            Timber.w(error, "Unable to load initial report %s", targetName)
            _state.update { it.copy(reportLoading = false) }
            emitEffect(
                UiEffect.Message(
                    error?.message?.takeIf { it.isNotBlank() }
                        ?: "Could not load report $targetName",
                ),
            )
        }
    }

    private suspend fun refreshSavedReports() {
        val reports: List<SavedReport> = repository.listReports()
        _state.update { it.copy(savedReports = reports) }
    }

    private fun rememberReport(report: ReportData) {
        val savedName = report.savedName
        if (savedName != null) {
            savedStateHandle[KEY_LAST_REPORT] = savedName
        } else {
            savedStateHandle.remove<String>(KEY_LAST_REPORT)
        }
    }

    private fun rememberSelectedTab(tab: ContentTab) {
        savedStateHandle[KEY_SELECTED_TAB] = tab.name
    }

    private fun restoredTab(): ContentTab {
        val savedName = savedStateHandle.get<String>(KEY_SELECTED_TAB) ?: return ContentTab.OVERVIEW
        return ContentTab.entries.firstOrNull { it.name == savedName } ?: ContentTab.OVERVIEW
    }

    private fun emitEffect(effect: UiEffect) {
        _effects.trySend(effect)
    }

    companion object {
        private const val KEY_LAST_REPORT = "last_report_name"
        private const val KEY_SELECTED_TAB = "selected_tab"

        fun factory(
            repository: ReportRepository,
            shizukuState: StateFlow<ShizukuState>,
            shizukuInstalled: () -> Boolean,
            requestShizukuPermission: () -> Unit,
            openShizuku: () -> Unit,
            refreshShizuku: () -> Unit,
            savedStateHandle: SavedStateHandle,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MainViewModel(
                    repository = repository,
                    shizukuState = shizukuState,
                    shizukuInstalled = shizukuInstalled,
                    requestShizukuPermission = requestShizukuPermission,
                    openShizuku = openShizuku,
                    refreshShizuku = refreshShizuku,
                    savedStateHandle = savedStateHandle,
                )
            }
        }
    }
}
