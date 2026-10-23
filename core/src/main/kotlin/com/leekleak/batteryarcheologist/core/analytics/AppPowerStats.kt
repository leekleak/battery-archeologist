package com.leekleak.batteryarcheologist.core.analytics

import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats
import java.util.Locale

/**
 * Per-app power estimates derived from the checkin report.
 *
 * Port of `presenter.parseAppStats`:
 * - device power: `100 * app.power_use_item.computed_power_mah / capacity_mah`
 * - CPU power: `100 * (app.cpu.power_ma_ms / 3_600_000) / capacity_mah`
 *
 * The raw percentages are kept as floats; formatting happens at the UI
 * boundary. Battery Historian displays two decimals; this app uses three so
 * small emulator/short-session estimates remain visible.
 */
data class AppPowerStat(
    val name: String,
    val uid: Int,
    val devicePowerMah: Float,
    val devicePowerPct: Float,
    val cpuPowerMah: Float,
    val cpuPowerPct: Float,
    val hasDevicePower: Boolean,
    val hasCpuPower: Boolean,
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
        val summaryCapacity = stats.system.powerUseSummary.batteryCapacityMah
        val estimatedCapacity = stats.system.battery.estimatedBatteryCapacityMah.toFloat()
        val capacity = when {
            summaryCapacity > 0f -> summaryCapacity
            estimatedCapacity > 0f -> estimatedCapacity
            else -> 0f
        }
        val estimated = summaryCapacity <= 0f && estimatedCapacity > 0f

        val apps = stats.appList
            .filter { it.name.isNotBlank() }
            .map { app ->
                val deviceMah = app.powerUseItem.computedPowerMah
                val hasDevice = app.hasPowerUseItem()
                val cpuMah = app.cpu.powerMaMs / MA_MS_PER_MAH
                val hasCpu = app.hasCpu() && app.cpu.hasPowerMaMs()
                val devicePct = if (capacity > 0f) 100f * deviceMah / capacity else 0f
                val cpuPct = if (capacity > 0f) 100f * cpuMah / capacity else 0f
                AppPowerStat(
                    name = app.name,
                    uid = app.uid,
                    devicePowerMah = deviceMah,
                    devicePowerPct = devicePct,
                    cpuPowerMah = cpuMah,
                    cpuPowerPct = cpuPct,
                    hasDevicePower = hasDevice,
                    hasCpuPower = hasCpu,
                )
            }
            .sortedWith(
                compareByDescending<AppPowerStat> { it.devicePowerPct }
                    .thenByDescending { it.cpuPowerPct }
                    .thenBy { it.name },
            )

        return AppPowerReport(
            capacityMah = capacity,
            capacityEstimated = estimated,
            apps = apps,
        )
    }

    /** Formats a percentage with three decimal places, e.g. `0.001%`. */
    fun formatPercent(pct: Float): String = String.format(Locale.US, "%.3f%%", pct)

    private const val MA_MS_PER_MAH = 3_600_000f
}
