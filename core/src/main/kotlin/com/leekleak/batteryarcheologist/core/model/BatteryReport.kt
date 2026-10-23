package com.leekleak.batteryarcheologist.core.model

import java.time.Instant

enum class CaptureSource {
    QUICK_CHECKIN,
    FULL_BUGREPORT,
    IMPORTED_FILE,
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

data class RawCapture(
    val meta: ReportMeta,
    val checkin: String,
    val packageList: String = "",
    val batteryDump: String = "",
    val systemProperties: Map<String, String> = emptyMap(),
) {
    val sizeBytes: Int get() = checkin.toByteArray(Charsets.UTF_8).size
}

data class CheckinSnapshot(
    val checkinVersion: Int?,
    val totalLines: Int,
    val historyLines: Int,
    val historyStringPoolLines: Int,
    val resetEvents: Int,
    val hasBatteryTime: Boolean,
    val previewLines: List<String>,
)

data class BatteryReport(
    val meta: ReportMeta,
    val snapshot: CheckinSnapshot,
    val diagnostics: ReportDiagnostics = ReportDiagnostics(),
    val packages: Map<Int, String> = emptyMap(),
)
