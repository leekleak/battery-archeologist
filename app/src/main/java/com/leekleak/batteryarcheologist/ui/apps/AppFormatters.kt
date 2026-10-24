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

/** Duration in seconds, e.g. `83.2 s`. */
internal fun formatSeconds(timeMs: Float): String =
    String.format(Locale.US, "%.1f s", timeMs / 1_000f)

/** Estimated energy in milliamp-hours, e.g. `0.0209 mAh`. */
internal fun formatMah(mah: Float): String =
    String.format(Locale.US, "%.4f mAh", mah)

/** Whole-number count used for wakeups and similar counters. */
internal fun formatCount(value: Float): String =
    String.format(Locale.US, "%.0f", value)

/** `uid 10123 · 1.3` identity line shared by rows and the detail sheet. */
@Composable
internal fun appIdentity(app: BatteryStats.App): String {
    val uid = app.uid ?: 0
    val version = app.version_name?.trim().orEmpty()
    return if (version.isEmpty()) {
        stringResource(R.string.apps_identity, uid)
    } else {
        stringResource(R.string.apps_identity_version, uid, version)
    }
}
