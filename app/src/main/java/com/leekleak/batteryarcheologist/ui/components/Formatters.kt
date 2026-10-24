package com.leekleak.batteryarcheologist.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.data.ReportOrigin
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Saved-report timestamps are always shown in the device's local time zone,
 * including reports captured in another zone or imported from another device.
 */
private val REPORT_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm").withZone(ZoneId.systemDefault())

private val BYTE_UNITS = listOf("B", "KB", "MB", "GB", "TB")

/**
 * Human-readable local timestamp for saved reports; falls back to [unknownLabel]
 * for legacy files without a capture time.
 */
fun formatTimestamp(instant: Instant?, unknownLabel: String): String =
    instant?.let(REPORT_TIME_FORMAT::format) ?: unknownLabel

private fun formatByteValue(bytes: Double): String {
    if (!bytes.isFinite() || bytes <= 0.0) return "0 B"
    var value = bytes
    var unit = 0
    while (value >= 1024.0 && unit < BYTE_UNITS.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0) {
        "${value.toLong()} B"
    } else {
        "%.1f %s".format(value, BYTE_UNITS[unit])
    }
}

/** Formats a byte count with binary units. */
fun formatByteCount(bytes: Float): String = formatByteValue(bytes.toDouble())

/** Convenience overload for file lengths and report sizes. */
fun formatBytes(bytes: Long): String = formatByteValue(bytes.toDouble())

/** Formats a capture duration, omitting zero-length saved-report durations. */
fun formatDurationMs(durationMs: Long): String = when {
    durationMs <= 0L -> "—"
    durationMs < 1_000L -> "$durationMs ms"
    else -> "%.1f s".format(durationMs / 1_000.0)
}

/** Label for a report origin stored by [com.leekleak.batteryarcheologist.data.ReportStore]. */
@Composable
fun reportOriginLabel(origin: ReportOrigin): String = when (origin) {
    ReportOrigin.CAPTURE -> stringResource(R.string.report_origin_captured)
    ReportOrigin.SAVED -> stringResource(R.string.report_origin_saved)
    ReportOrigin.IMPORT -> stringResource(R.string.report_origin_imported)
}

/** Label for a saved report's metadata source. */
@Composable
fun reportSourceLabel(source: String?): String = when (source) {
    "quick-checkin" -> stringResource(R.string.report_origin_captured)
    "imported" -> stringResource(R.string.report_origin_imported)
    null -> stringResource(R.string.report_origin_saved)
    else -> source
}
