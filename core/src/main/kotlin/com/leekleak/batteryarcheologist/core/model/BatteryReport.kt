package com.leekleak.batteryarcheologist.core.model

import java.time.Instant

enum class CaptureSource {
}

data class ReportMeta(
    val capturedAt: Instant,
    val source: CaptureSource,
    val sdkVersion: Int? = null,
    val buildFingerprint: String? = null,
    val deviceModel: String? = null,
    val androidId: String? = null,
    val checkinVersion: Int? = null,
)

data class ReportDiagnostics(
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
)

data class CheckinSnapshot(
    val checkinVersion: Int?,
    val totalLines: Int,
    val historyLines: Int,
    val historyStringPoolLines: Int,
    val resetEvents: Int,
    val hasBatteryTime: Boolean,
    val previewLines: List<String>,
)

