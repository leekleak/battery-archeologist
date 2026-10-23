package com.leekleak.batteryarcheologist.ui.timeline

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Semantic series colors for the dense timeline.
 *
 * The traffic-light accents (green charging, amber wakelocks, red CPU) stay
 * recognizable in both themes while the chrome (grid, labels, surfaces) comes
 * from the active Material 3 color scheme.
 */
@Immutable
internal data class TimelinePalette(
    val battery: Color,
    val batteryFill: Color,
    val charging: Color,
    val screen: Color,
    val wifi: Color,
    val wakelock: Color,
    val topApp: Color,
    val cpu: Color,
    val other: Color,
    val grid: Color,
    val axisText: Color,
    val rowText: Color,
    val plotBackground: Color,
    val plotStripe: Color,
    val report: Color,
) {
    fun seriesColor(group: TimelineGroup): Color = when (group) {
        TimelineGroup.BATTERY -> battery
        TimelineGroup.CHARGING -> charging
        TimelineGroup.SCREEN -> screen
        TimelineGroup.WIFI -> wifi
        TimelineGroup.WAKELOCK -> wakelock
        TimelineGroup.TOP_APP -> topApp
        TimelineGroup.CPU -> cpu
        TimelineGroup.OTHER -> other
    }
}

@Composable
internal fun rememberTimelinePalette(): TimelinePalette {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    return remember(
        dark,
        scheme.primary,
        scheme.error,
        scheme.onSurface,
        scheme.onSurfaceVariant,
        scheme.surface,
    ) {
        val battery = scheme.primary
        TimelinePalette(
            battery = battery,
            batteryFill = battery.copy(alpha = 0.16f),
            charging = if (dark) Color(0xFF81C784) else Color(0xFF2E7D32),
            screen = if (dark) Color(0xFF4DB6AC) else Color(0xFF00796B),
            wifi = if (dark) Color(0xFF64B5F6) else Color(0xFF1565C0),
            wakelock = if (dark) Color(0xFFFFB74D) else Color(0xFFB26A00),
            topApp = if (dark) Color(0xFFCE93D8) else Color(0xFF6A1B9A),
            cpu = if (dark) Color(0xFFEF9A9A) else Color(0xFFC62828),
            other = if (dark) Color(0xFF90A4AE) else Color(0xFF546E7A),
            grid = scheme.onSurface.copy(alpha = if (dark) 0.14f else 0.10f),
            axisText = scheme.onSurfaceVariant,
            rowText = scheme.onSurface.copy(alpha = 0.84f),
            plotBackground = scheme.surface.copy(alpha = if (dark) 0.50f else 0.70f),
            plotStripe = scheme.onSurface.copy(alpha = if (dark) 0.05f else 0.035f),
            report = scheme.error,
        )
    }
}
