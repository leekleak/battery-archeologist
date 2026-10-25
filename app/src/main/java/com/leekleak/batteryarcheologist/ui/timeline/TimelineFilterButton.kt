package com.leekleak.batteryarcheologist.ui.timeline

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.leekleak.batteryarcheologist.R

/**
 * Page title bar action that opens the timeline metric filter.
 *
 * It lives in the title bar instead of the cursor readout so it stays reachable
 * while the chart scrolls. The icon is tinted while a filter hides metrics.
 */
@Composable
internal fun TimelineFilterButton(
    active: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.testTag("btn_timeline_filter"),
    ) {
        Icon(
            painter = painterResource(R.drawable.filter_list),
            contentDescription = stringResource(
                if (active) {
                    R.string.timeline_filter_icon_active
                } else {
                    R.string.timeline_filter_icon
                },
            ),
            tint = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
