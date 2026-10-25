package com.leekleak.batteryarcheologist.core.analytics

import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats
import java.util.Locale

/**
 * Per-app power estimates derived from the checkin report.
 *
 * Port of `presenter.parseAppStats`:
 * - device power: `100 * app.power_use_item.computed_power_mah / capacity_mah`
 *
 * Per-app CPU power is intentionally not modelled: Android 8.0+ hard-codes
 * the checkin CPU power field to 0, and `computed_power_mah` already includes
 * the CPU attribution for the app.
 *
 * The raw percentages are kept as floats; formatting happens at the UI
 * boundary. Battery Historian displays two decimals; this app keeps up to
 * three and drops trailing zeros so small estimates stay visible without
 * value noise.
 */
data class AppPowerStat(
    val name: String,
    val uid: Int,
    val devicePowerMah: Float,
    val devicePowerPct: Float,
    val hasDevicePower: Boolean,
)

data class AppPowerReport(
    val capacityMah: Float,
    val capacityEstimated: Boolean,
    val apps: List<AppPowerStat>,
) {
    val isAvailable: Boolean get() = capacityMah > 0f
}

object AppPowerStats {
    fun calculate(stats: BatteryStats): AppPowerReport {
        val system = stats.system
        val summaryCapacity = system?.power_use_summary?.battery_capacity_mah ?: 0f
        val estimatedCapacity = system?.battery?.estimated_battery_capacity_mah?.toFloat() ?: 0f
        val capacity = when {
            summaryCapacity > 0f -> summaryCapacity
            estimatedCapacity > 0f -> estimatedCapacity
            else -> 0f
        }
        val estimated = summaryCapacity <= 0f && estimatedCapacity > 0f

        val apps = stats.app
            .filter { !it.name.isNullOrBlank() }
            .map { app ->
                val powerUseItem = app.power_use_item
                val deviceMah = powerUseItem?.computed_power_mah ?: 0f
                val hasDevice = powerUseItem != null
                val devicePct = if (capacity > 0f) 100f * deviceMah / capacity else 0f
                AppPowerStat(
                    name = app.name.orEmpty(),
                    uid = app.uid ?: 0,
                    devicePowerMah = deviceMah,
                    devicePowerPct = devicePct,
                    hasDevicePower = hasDevice,
                )
            }
            .sortedWith(
                compareByDescending<AppPowerStat> { it.devicePowerPct }
                    .thenBy { it.name },
            )

        return AppPowerReport(
            capacityMah = capacity,
            capacityEstimated = estimated,
            apps = apps,
        )
    }

    /**
     * Formats a percentage with at most three decimal places, dropping trailing
     * zeros: `1.000%` becomes `1%` while `0.614%` is unchanged.
     *
     * A non-zero value too small to round at this precision is shown as
     * `<0.001%` so it stays distinguishable from an exact zero.
     */
    fun formatPercent(pct: Float): String {
        if (pct == 0f) return "0%"
        val formatted = String.format(Locale.US, "%.3f", pct)
        val trimmed = formatted.trimEnd('0').trimEnd('.')
        return if (trimmed == "0" || trimmed == "-0") "<0.001%" else "$trimmed%"
    }
}
