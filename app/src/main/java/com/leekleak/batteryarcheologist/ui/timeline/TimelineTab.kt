package com.leekleak.batteryarcheologist.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.core.history.parseutils.ActivitySummary
import com.leekleak.batteryarcheologist.data.ReportData
import com.leekleak.batteryarcheologist.ui.components.SectionCard

/**
 * Timeline tab.
 *
 * The whole page scrolls vertically. The chart is a single item and only
 * claims horizontal drags for its inspection cursor, so vertical drags scroll
 * the page instead of fighting the chart.
 */
@Composable
fun TimelineTab(
    report: ReportData,
    contentPadding: PaddingValues,
) {
    val history = report.history
    if (history == null) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item {
                SectionCard(title = stringResource(R.string.timeline_unavailable)) {
                    Text(
                        text = report.historyFailure
                            ?: stringResource(R.string.timeline_unavailable_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }

    val reportEndMs = remember(history) {
        history.summaries.maxOfOrNull { it.endTimeMs }
    }
    val intervals = remember(history) {
        history.summaries.filter {
            it.initialBatteryLevel >= 0 &&
                it.finalBatteryLevel >= 0 &&
                it.initialBatteryLevel != it.finalBatteryLevel
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "timeline_chart") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("parsed_timeline"),
            ) {
                TimelineScreen(
                    entries = history.entries,
                    reportTakenMs = reportEndMs,
                )
            }
        }

        if (intervals.isNotEmpty()) {
            item(key = "timeline_intervals") {
                DischargeIntervalsCard(intervals)
            }
        }
    }
}

/**
 * Discharge intervals are part of the vertically scrolling page now; the
 * bottom sheet was removed together with the manual zoom controls.
 */
@Composable
private fun DischargeIntervalsCard(intervals: List<ActivitySummary>) {
    SectionCard(
        modifier = Modifier.testTag("discharge_intervals"),
        title = stringResource(R.string.timeline_discharge_intervals),
    ) {
        intervals.forEach { summary ->
            val durationMs = (summary.endTimeMs - summary.startTimeMs).coerceAtLeast(0L)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(
                            R.string.timeline_battery_range,
                            summary.initialBatteryLevel,
                            summary.finalBatteryLevel,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = TimelineMath.formatSpan(durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(
                        R.string.timeline_interval_clock_range,
                        TimelineMath.formatClockTime(summary.startTimeMs, durationMs),
                        TimelineMath.formatClockTime(summary.endTimeMs, durationMs),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
