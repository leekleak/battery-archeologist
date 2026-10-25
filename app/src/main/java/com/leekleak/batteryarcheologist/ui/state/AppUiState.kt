package com.leekleak.batteryarcheologist.ui.state

import com.leekleak.batteryarcheologist.core.model.ShizukuState
import com.leekleak.batteryarcheologist.data.ReportData
import com.leekleak.batteryarcheologist.data.SavedReport
import com.leekleak.batteryarcheologist.ui.navigation.ContentTab

/** Progress and failure state for the capture/import action. */
sealed interface CaptureStatus {
    data object Idle : CaptureStatus
    data class Running(val importing: Boolean) : CaptureStatus
    data class Failed(val message: String) : CaptureStatus
}

/** Progress and failure state for resetting the system battery stats. */
sealed interface ResetStatus {
    data object Idle : ResetStatus
    data object Running : ResetStatus
    data class Failed(val message: String) : ResetStatus
}

/** Privileged action deferred until Shizuku becomes ready. */
enum class PrivilegedAction { CAPTURE, RESET }

/** Raw checkin text shown in a bottom sheet, with its saved metadata if any. */
data class RawSheetState(
    val raw: String,
    val name: String?,
    val sizeBytes: Long,
    val source: String?,
)

/**
 * Single source of truth for the app root.
 *
 * The reports home is always available; [report] and [selectedTab] describe the
 * report viewer destination that Navigation 3 pushes on top of it.
 */
data class AppUiState(
    val shizukuState: ShizukuState = ShizukuState.Unknown,
    val shizukuInstalled: Boolean = false,
    val report: ReportData? = null,
    val reportLoading: Boolean = false,
    val reportError: String? = null,
    val captureStatus: CaptureStatus = CaptureStatus.Idle,
    val resetStatus: ResetStatus = ResetStatus.Idle,
    val savedReports: List<SavedReport> = emptyList(),
    val pinnedApps: Set<String> = emptySet(),
    val selectedTab: ContentTab = ContentTab.OVERVIEW,
    val pendingAction: PrivilegedAction? = null,
    val rawSheet: RawSheetState? = null,
) {
    val isBusy: Boolean
        get() = captureStatus is CaptureStatus.Running ||
            resetStatus is ResetStatus.Running ||
            reportLoading
}

/** One-shot events that must not be replayed after configuration changes. */
sealed interface UiEffect {
    data class ShareReport(val name: String) : UiEffect
    data class Message(val text: String) : UiEffect
    data object OpenPermission : UiEffect
    data object ClosePermission : UiEffect
}
