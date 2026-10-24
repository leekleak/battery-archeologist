package com.leekleak.batteryarcheologist.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.core.model.TimelineEntry
import com.leekleak.batteryarcheologist.ui.theme.BatteryArcheologistTheme

/**
 * Full-report timeline surface with a draggable inspection cursor.
 *
 * The chart uses a fixed full-report viewport: time runs horizontally, rows are
 * stacked vertically, and the page itself scrolls vertically. Horizontal drags
 * move the cursor; vertical drags are left to the enclosing scroll container.
 *
 * The cursor readout spells out the value of every row at the selected moment,
 * so the report is not readable only by decoding colors.
 */
@Composable
fun TimelineScreen(
    entries: List<TimelineEntry>,
    modifier: Modifier = Modifier,
    batteryEntries: List<TimelineEntry> = emptyList(),
    reportTakenMs: Long? = null,
) {
    val data = remember(entries, batteryEntries, reportTakenMs) {
        TimelineData.build(
            entries = entries,
            batteryEntries = batteryEntries,
            reportTakenMs = reportTakenMs,
        )
    }
    val palette = rememberTimelinePalette()
    var cursorMs by remember(data) {
        mutableStateOf<Long?>(data.reportTakenMs ?: data.range.endMs)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("timeline_screen"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.timeline_range_summary,
                    TimelineMath.formatClockTime(data.range.startMs, data.range.spanMs),
                    TimelineMath.formatClockTime(data.range.endMs, data.range.spanMs),
                    TimelineMath.formatSpan(data.range.spanMs),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (data.isEmpty) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp)
                        .testTag("timeline_canvas"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.timeline_no_entries),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                TimelineCanvas(
                    data = data,
                    cursorMs = cursorMs,
                    onCursorMsChange = { cursorMs = it },
                    palette = palette,
                )
                CursorReadout(
                    data = data,
                    cursorMs = cursorMs ?: data.range.endMs,
                    palette = palette,
                )
                Text(
                    text = stringResource(R.string.timeline_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Textual values at the cursor. Every row is listed, including rows with no
 * active event, so the user can tell the difference between "off" and "no
 * data for this row".
 */
@Composable
private fun CursorReadout(
    data: TimelineData,
    cursorMs: Long,
    palette: TimelinePalette,
) {
    val battery = batteryLevelAt(data.batteryPoints, cursorMs)
    val rows = data.rows.map { row ->
        row.group to row.series.entryAt(cursorMs)
    }
    val hasValue = battery != null || rows.any { it.second != null }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("timeline_cursor_readout"),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.timeline_selected_time,
                    TimelineMath.formatClockDateTime(cursorMs),
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )

            if (battery != null) {
                CursorValueRow(
                    color = palette.battery,
                    label = stringResource(R.string.timeline_group_battery),
                    value = "${battery.toInt()}%",
                )
            }
            rows.forEach { (group, entry) ->
                CursorValueRow(
                    color = palette.seriesColor(group),
                    label = stringResource(group.labelRes),
                    value = cursorValue(entry),
                )
            }

            if (!hasValue) {
                Text(
                    text = stringResource(R.string.timeline_cursor_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun cursorValue(entry: TimelineEntry?): String {
    val raw = entry?.value?.trim().orEmpty()
    if (raw.isEmpty()) return stringResource(R.string.timeline_no_active_event)
    return when {
        raw.equals("true", ignoreCase = true) -> stringResource(R.string.timeline_value_on)
        raw.equals("false", ignoreCase = true) -> stringResource(R.string.timeline_value_off)
        raw.length > 28 -> raw.take(27) + "…"
        else -> raw
    }
}

@Composable
private fun CursorValueRow(
    color: Color,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(
    name = "Timeline light",
    showBackground = true,
    widthDp = 411,
    heightDp = 891,
)
@Composable
private fun TimelineScreenLightPreview() {
    BatteryArcheologistTheme {
        TimelineScreenPreviewContent()
    }
}

@Preview(
    name = "Timeline dark",
    showBackground = true,
    widthDp = 411,
    heightDp = 891,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun TimelineScreenDarkPreview() {
    BatteryArcheologistTheme {
        TimelineScreenPreviewContent()
    }
}

@Composable
private fun TimelineScreenPreviewContent() {
    val fixture = remember { TimelineFixtures.build() }
    TimelineScreen(
        entries = fixture.entries,
        batteryEntries = fixture.batteryEntries,
        reportTakenMs = fixture.reportTakenMs,
        modifier = Modifier.padding(12.dp),
    )
}
