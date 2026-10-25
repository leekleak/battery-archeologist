package com.leekleak.batteryarcheologist.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.data.TimelineFilter
import com.leekleak.batteryarcheologist.ui.components.FancyBottomSheet
import com.leekleak.batteryarcheologist.ui.components.FancySheetHeader
import com.leekleak.batteryarcheologist.ui.components.fancyCard

/**
 * Metric filter for the timeline.
 *
 * The selection is applied live only when the user presses Apply; the chart
 * behind the sheet therefore stays stable while the list is edited.
 */
@Composable
internal fun TimelineFilterSheet(
    availableMetrics: List<TimelineMetricSpec>,
    initialFilter: TimelineFilter,
    onDismiss: () -> Unit,
    onApply: (TimelineFilter) -> Unit,
) {
    val allMetrics = remember(availableMetrics) {
        availableMetrics.map { it.metric }.toSet()
    }
    var selected by remember(initialFilter, availableMetrics) {
        mutableStateOf(
            if (initialFilter.active) initialFilter.metrics else allMetrics,
        )
    }

    FancyBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("timeline_filter_sheet"),
        header = {
            FancySheetHeader(
                title = stringResource(R.string.timeline_filter_title),
                subtitle = stringResource(R.string.timeline_filter_description),
                leading = {
                    Icon(
                        painter = painterResource(R.drawable.ic_filter),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                },
            )
        },
        footer = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_timeline_filter_cancel"),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
            Button(
                onClick = {
                    val applied = when {
                        availableMetrics.isEmpty() -> TimelineFilter()
                        selected.containsAll(allMetrics) -> TimelineFilter()
                        else -> TimelineFilter(active = true, metrics = selected)
                    }
                    onApply(applied)
                },
                modifier = Modifier.testTag("btn_timeline_filter_apply"),
            ) {
                Text(stringResource(R.string.timeline_filter_apply))
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = { selected = allMetrics },
                modifier = Modifier.testTag("btn_timeline_filter_all"),
            ) {
                Text(stringResource(R.string.timeline_filter_select_all))
            }
            TextButton(
                onClick = { selected = emptySet() },
                modifier = Modifier.testTag("btn_timeline_filter_none"),
            ) {
                Text(stringResource(R.string.timeline_filter_clear_all))
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(availableMetrics, key = { it.metric }) { spec ->
                val checked = spec.metric in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fancyCard()
                        .clickable {
                            selected = if (checked) {
                                selected - spec.metric
                            } else {
                                selected + spec.metric
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { isChecked ->
                            selected = if (isChecked) {
                                selected + spec.metric
                            } else {
                                selected - spec.metric
                            }
                        },
                    )
                    Text(
                        text = spec.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
