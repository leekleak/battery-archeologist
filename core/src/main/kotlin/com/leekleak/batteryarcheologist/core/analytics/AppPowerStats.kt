package com.leekleak.batteryarcheologist.core.analytics

import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats
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
                val cpu = app.cpu
                val deviceMah = powerUseItem?.computed_power_mah ?: 0f
                val hasDevice = powerUseItem != null
                val cpuMah = (cpu?.power_ma_ms ?: 0f) / MA_MS_PER_MAH
                val hasCpu = cpu?.power_ma_ms != null
                val devicePct = if (capacity > 0f) 100f * deviceMah / capacity else 0f
                val cpuPct = if (capacity > 0f) 100f * cpuMah / capacity else 0f
                AppPowerStat(
                    name = app.name.orEmpty(),
                    uid = app.uid ?: 0,
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
