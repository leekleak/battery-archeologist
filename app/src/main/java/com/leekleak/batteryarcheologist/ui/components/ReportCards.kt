package com.leekleak.batteryarcheologist.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Compact notice for non-fatal parser warnings or a failed parse.
 *
 * Only the issue count is needed here; the full diagnostics live in the
 * always-expanded report details on the Other tab. The card uses the same
 * [FancySectionCard] style as the other Other-tab cards, with the container and
 * text colors swapped for the error variant.
 */
@Composable
fun ParserNoticeCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    FancySectionCard(
        title = title,
        subtitle = message,
        modifier = modifier.testTag(if (isError) "parser_error" else "parser_warning"),
        containerColor = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        titleColor = if (isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.primary
        },
        subtitleColor = if (isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        // The notice only has a title and a message, both handled above.
    }
}
