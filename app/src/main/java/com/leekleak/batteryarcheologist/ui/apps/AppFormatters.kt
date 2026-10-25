package com.leekleak.batteryarcheologist.ui.apps

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats
import java.util.Locale

/*
 * Fixed-locale formatters for per-app technical values. Using Locale.US keeps
 * durations, mAh estimates and counts stable across locales (no decimal
 * commas) and matches AppPowerStats.formatPercent.
 */

/** Duration in seconds, e.g. `83.2 s`; trailing zeros are dropped (`83 s`). */
internal fun formatSeconds(timeMs: Float): String =
    formatDecimal(timeMs / 1_000f, 1, " s")

/** Estimated energy in milliamp-hours, e.g. `0.0209 mAh`; `1423.0000` becomes `1423`. */
internal fun formatMah(mah: Float): String =
    formatDecimal(mah, 4, " mAh")

/** Whole-number count used for wakeups and similar counters. */
internal fun formatCount(value: Float): String =
    String.format(Locale.US, "%.0f", value)

/**
 * Formats [value] with at most [decimals] decimal places and drops trailing
 * zeros. Non-zero values that round to zero are prefixed with `<` instead of
 * being reported as an exact zero.
 */
private fun formatDecimal(value: Float, decimals: Int, suffix: String): String {
    if (value == 0f) return "0$suffix"
    val formatted = String.format(Locale.US, "%.${decimals}f", value)
    val trimmed = formatted.trimEnd('0').trimEnd('.')
    if (trimmed != "0" && trimmed != "-0") return "$trimmed$suffix"
    val threshold = if (decimals <= 0) "1" else "0.${"0".repeat(decimals - 1)}1"
    return "<$threshold$suffix"
}

/** Package name and UID line for a list row; pseudo-apps only expose their UID. */
@Composable
internal fun appRowSubtitle(app: BatteryStats.App): String {
    val uid = app.uid ?: 0
    val packageName = app.name.orEmpty()
    return if (packageName.contains('.')) {
        stringResource(R.string.apps_identity_package, packageName, uid)
    } else {
        stringResource(R.string.apps_identity, uid)
    }
}

/** UID and version line used when the package name would otherwise repeat. */
@Composable
internal fun appUidSubtitle(app: BatteryStats.App): String {
    val uid = app.uid ?: 0
    val version = app.version_name?.trim().orEmpty()
    return if (version.isEmpty()) {
        stringResource(R.string.apps_identity, uid)
    } else {
        stringResource(R.string.apps_identity_version, uid, version)
    }
}

/** Package name, UID and version line for the detail sheet. */
@Composable
internal fun appDetailSubtitle(app: BatteryStats.App): String {
    val uid = app.uid ?: 0
    val packageName = app.name.orEmpty()
    val version = app.version_name?.trim().orEmpty()
    return when {
        packageName.contains('.') && version.isNotEmpty() ->
            stringResource(R.string.apps_identity_package_version, packageName, uid, version)

        packageName.contains('.') ->
            stringResource(R.string.apps_identity_package, packageName, uid)

        version.isNotEmpty() ->
            stringResource(R.string.apps_identity_version, uid, version)

        else -> stringResource(R.string.apps_identity, uid)
    }
}
