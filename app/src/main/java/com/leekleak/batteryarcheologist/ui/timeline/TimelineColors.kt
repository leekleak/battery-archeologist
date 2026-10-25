package com.leekleak.batteryarcheologist.ui.timeline

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Semantic series colors built entirely from the active Material 3 scheme.
 *
 * There is no custom brand palette: each series maps to a Material role. The
 * cursor readout carries the exact values, so color is a secondary cue rather
 * than the only way to read the chart.
 */
@Immutable
internal data class TimelinePalette(
    val battery: Color,
    val batteryFill: Color,
    val charging: Color,
    val screen: Color,
    val wifi: Color,
    val radio: Color,
    val gps: Color,
    val wakelock: Color,
    val topApp: Color,
    val cpu: Color,
    val apps: Color,
    val device: Color,
    val media: Color,
    val system: Color,
    val other: Color,
    val grid: Color,
    val axisText: Color,
    val rowText: Color,
    val plotBackground: Color,
    val plotStripe: Color,
    val cursor: Color,
) {
    fun seriesColor(group: TimelineGroup): Color = when (group) {
        TimelineGroup.BATTERY -> battery
        TimelineGroup.CHARGING -> charging
        TimelineGroup.SCREEN -> screen
        TimelineGroup.WIFI -> wifi
        TimelineGroup.RADIO -> radio
        TimelineGroup.GPS -> gps
        TimelineGroup.WAKELOCK -> wakelock
        TimelineGroup.TOP_APP -> topApp
        TimelineGroup.CPU -> cpu
        TimelineGroup.APPS -> apps
        TimelineGroup.DEVICE -> device
        TimelineGroup.MEDIA -> media
        TimelineGroup.SYSTEM -> system
        TimelineGroup.OTHER -> other
    }
}

@Composable
internal fun rememberTimelinePalette(): TimelinePalette {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    return remember(dark, scheme) {
        val battery = scheme.primary
        TimelinePalette(
            battery = battery,
            batteryFill = battery.copy(alpha = 0.16f),
            charging = scheme.tertiary,
            screen = scheme.secondary,
            wifi = scheme.primary,
            radio = scheme.tertiary,
            gps = scheme.secondary,
            wakelock = scheme.error,
            topApp = scheme.tertiary,
            cpu = scheme.error,
            apps = scheme.primary,
            device = scheme.onSurfaceVariant,
            media = scheme.tertiary,
            system = scheme.onSurfaceVariant,
            other = scheme.onSurfaceVariant,
            grid = scheme.onSurface.copy(alpha = if (dark) 0.16f else 0.10f),
            axisText = scheme.onSurfaceVariant,
            rowText = scheme.onSurface.copy(alpha = 0.86f),
            plotBackground = scheme.surfaceContainerLow,
            plotStripe = scheme.onSurface.copy(alpha = if (dark) 0.05f else 0.035f),
            cursor = scheme.onSurface,
        )
    }
}
