package com.leekleak.batteryarcheologist.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Container and outline shared by floating surfaces, so the bottom toolbar and
 * overlays such as the timeline readout read as one layer above the content.
 */
object FloatingSurfaceDefaults {
    @Composable
    fun containerColor(): Color = MaterialTheme.colorScheme.background

    @Composable
    fun border(): BorderStroke = BorderStroke(
        width = 1.5.dp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
    )
}
