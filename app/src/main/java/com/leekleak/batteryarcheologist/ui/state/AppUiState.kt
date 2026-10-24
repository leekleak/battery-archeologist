package com.leekleak.batteryarcheologist.ui.state

import com.leekleak.batteryarcheologist.core.model.ShizukuState
import com.leekleak.batteryarcheologist.data.ReportData
import com.leekleak.batteryarcheologist.data.SavedReport
import com.leekleak.batteryarcheologist.ui.navigation.ContentTab

/** Progress and failure state for the capture/import action. */
sealed interface CaptureStatus {
    data object Idle : CaptureStatus
    data object Running : CaptureStatus
    data class Failed(val message: String) : CaptureStatus
}

/** Single source of truth for the main screen. */
data class AppUiState(
    val shizukuState: ShizukuState = ShizukuState.Unknown,
    val shizukuInstalled: Boolean = false,
    val report: ReportData? = null,
    val captureStatus: CaptureStatus = CaptureStatus.Idle,
    val reportLoading: Boolean = false,
    val savedReports: List<SavedReport> = emptyList(),
    val selectedTab: ContentTab = ContentTab.OVERVIEW,
)

/** One-shot events that must not be replayed after configuration changes. */
sealed interface UiEffect {
    data class ShareReport(val name: String) : UiEffect
    data class Message(val text: String) : UiEffect
}
