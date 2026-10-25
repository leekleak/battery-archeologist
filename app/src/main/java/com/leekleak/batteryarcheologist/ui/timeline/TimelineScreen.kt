package com.leekleak.batteryarcheologist.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.ui.theme.BatteryArcheologistTheme

/**
 * Full-report timeline surface with a draggable inspection cursor.
 *
 * The chart uses a fixed full-report viewport: time runs horizontally, rows are
 * stacked vertically, and the page itself scrolls vertically. Horizontal drags
 * move the cursor; vertical drags are left to the enclosing scroll container.
 *
 * The value readout is a separate overlay owned by [TimelineTab], so it stays
 * visible while this (potentially very tall) chart scrolls.
 */
@Composable
fun TimelineScreen(
    data: TimelineData,
    cursorMs: Long?,
    onCursorMsChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberTimelinePalette()

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
                    onCursorMsChange = onCursorMsChange,
                    palette = palette,
                )
            }
        }
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
    val data = remember(fixture) {
        TimelineData.build(
            entries = fixture.entries,
            batteryEntries = fixture.batteryEntries,
            reportTakenMs = fixture.reportTakenMs,
        )
    }
    TimelineScreen(
        data = data,
        cursorMs = data.changeTimesMs.lastOrNull() ?: fixture.reportTakenMs,
        onCursorMsChange = {},
        modifier = Modifier.padding(12.dp),
    )
}
