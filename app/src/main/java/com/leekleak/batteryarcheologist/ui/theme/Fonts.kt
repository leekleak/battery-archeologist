/*
 * Adapted from Traffic Light's ui/theme/Fonts.kt
 * (https://github.com/leekleak/traffic-light), GPL-3.0, same author.
 * The bundled `res/font/google_sans_flex.ttf` comes from the same source; see
 * THIRD_PARTY_NOTICES.md.
 */

package com.leekleak.batteryarcheologist.ui.theme

import androidx.annotation.FloatRange
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import com.leekleak.batteryarcheologist.R

/**
 * Google Sans Flex, the display face used by Traffic Light.
 *
 * The bundled file is the variable font, so every call selects an instance of
 * the same resource instead of shipping one file per weight. Callers should
 * wrap this in `remember` when they use it during composition.
 */
fun googleSans(
    @FloatRange(100.0, 1000.0) weight: Float = 400f,
    @FloatRange(0.0, 100.0) grade: Float = 0f,
    @FloatRange(-10.0, 0.0) slant: Float = 0f,
    @FloatRange(25.0, 151.0) width: Float = 100f,
    @FloatRange(0.0, 100.0) roundness: Float = 0f,
): FontFamily {
    return FontFamily(
        Font(
            R.font.google_sans_flex,
            variationSettings = FontVariation.Settings(
                FontVariation.Setting("wght", weight),
                FontVariation.Setting("GRAD", grade),
                FontVariation.Setting("slnt", slant),
                FontVariation.Setting("wdth", width),
                FontVariation.Setting("ROND", roundness),
            ),
        ),
    )
}
