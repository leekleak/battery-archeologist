package com.leekleak.batteryarcheologist.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leekleak.batteryarcheologist.R
import kotlinx.coroutines.launch

/**
 * Sticky cursor readout for the timeline.
 *
 * It stays above the navigation bar while the chart scrolls, so the selected
 * time, the data-change arrows and the values at the cursor remain visible.
 * The value list is capped and scrolls internally when many metrics are active.
 */
@Composable
internal fun TimelineReadout(
    data: TimelineData,
    cursorMs: Long,
    palette: TimelinePalette,
    filterActive: Boolean,
    onCursorMsChange: (Long) -> Unit,
    onFilterClick: () -> Unit,
    onHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val battery = batteryLevelAt(data.batteryPoints, cursorMs)
    val rows = data.rows.mapNotNull { row ->
        row.series.entryAt(cursorMs)?.let { entry -> row to entry }
    }
    val hasValue = battery != null || rows.isNotEmpty()
    val canPrevious = data.previousChange(cursorMs) != null
    val canNext = data.nextChange(cursorMs) != null

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { onHeightChanged(it.height) }
            .testTag("timeline_cursor_readout"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        data.previousChange(cursorMs)?.let(onCursorMsChange)
                    },
                    enabled = canPrevious,
                    modifier = Modifier.testTag("timeline_prev_change"),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = stringResource(R.string.timeline_previous_change),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = TimelineMath.formatClockDate(cursorMs),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = TimelineMath.formatClockTimeSeconds(cursorMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
                IconButton(
                    onClick = onFilterClick,
                    modifier = Modifier.testTag("btn_timeline_filter"),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_filter),
                        contentDescription = stringResource(
                            if (filterActive) {
                                R.string.timeline_filter_icon_active
                            } else {
                                R.string.timeline_filter_icon
                            },
                        ),
                        tint = if (filterActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(
                    onClick = {
                        data.nextChange(cursorMs)?.let(onCursorMsChange)
                    },
                    enabled = canNext,
                    modifier = Modifier.testTag("timeline_next_change"),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_forward),
                        contentDescription = stringResource(R.string.timeline_next_change),
                    )
                }
            }

            if (!hasValue) {
                Text(
                    text = stringResource(R.string.timeline_cursor_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            } else {
                val rowCount = rows.size + if (battery != null) 1 else 0
                val scrollState = rememberScrollState()
                Column(
                    modifier = if (rowCount > 7) {
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(scrollState)
                    } else {
                        Modifier.fillMaxWidth()
                    },
                ) {
                    if (battery != null) {
                        CursorValueRow(
                            color = palette.battery,
                            label = stringResource(R.string.timeline_group_battery),
                            value = "${battery.toInt()}%",
                        )
                    }
                    rows.forEach { (row, entry) ->
                        CursorValueRow(
                            color = palette.seriesColor(row.spec.group),
                            label = row.spec.label,
                            value = formatTimelineValue(row.spec, entry),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CursorValueRow(
    color: Color,
    label: String,
    value: String,
) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    val fullText = if (value.isBlank()) label else "$label: $value"

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(
                    text = fullText,
                    modifier = Modifier.widthIn(max = 280.dp),
                )
            }
        },
        state = tooltipState,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(fullText) {
                    detectTapGestures(
                        onLongPress = {
                            scope.launch { tooltipState.show() }
                        },
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(color, CircleShape),
            )
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
