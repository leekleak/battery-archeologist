package com.leekleak.batteryarcheologist.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.leekleak.batteryarcheologist.core.model.TimelineEntry
import com.leekleak.batteryarcheologist.ui.theme.BatteryArcheologistTheme

private const val ZOOM_FACTOR = 1.7f

/**
 * First end-to-end Timeline surface for a parsed Battery Historian report.
 *
 * [entries] may contain battery rows; they are automatically lifted into the
 * overlay. Passing [batteryEntries] explicitly is useful when the parser keeps
 * the battery series separate. [reportTakenMs] draws a vertical marker and
 * becomes the zero point for the relative time axis.
 *
 * The component owns its viewport state and exposes it only through the
 * canvas/controls, so the host screen does not need to wire pan or zoom.
 */
@Composable
fun TimelineScreen(
    entries: List<TimelineEntry>,
    title: String,
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
    var viewport by remember(data) { mutableStateOf(data.initialViewport) }

    val legendGroups = remember(data) {
        buildList {
            if (data.batteryPoints.isNotEmpty()) add(TimelineGroup.BATTERY)
            addAll(data.rows.map { it.group })
        }
    }
    val legendCounts = remember(data) {
        buildMap {
            if (data.batteryPoints.isNotEmpty()) {
                put(TimelineGroup.BATTERY, data.batteryPoints.size)
            }
            for (row in data.rows) {
                put(row.group, row.series.size)
            }
        }
    }

    val minViewportSpan = TimelineMath.MIN_VIEWPORT_MS.coerceAtMost(data.range.spanMs)
    val canZoomIn = viewport.spanMs > minViewportSpan
    val canZoomOut = viewport.spanMs < data.range.spanMs
    val canReset = viewport != data.initialViewport

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("timeline_screen")
            .semantics { testTagsAsResourceId = true },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = timelineSummary(data),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    TimelineControlButton(
                        label = "−",
                        description = "Zoom out",
                        testTag = "timeline_zoom_out",
                        enabled = canZoomOut,
                        onClick = {
                            viewport = TimelineMath.zoomViewport(
                                viewport = viewport,
                                range = data.range,
                                zoomFactor = 1f / ZOOM_FACTOR,
                            )
                        },
                    )
                    TimelineControlButton(
                        label = "+",
                        description = "Zoom in",
                        testTag = "timeline_zoom_in",
                        enabled = canZoomIn,
                        onClick = {
                            viewport = TimelineMath.zoomViewport(
                                viewport = viewport,
                                range = data.range,
                                zoomFactor = ZOOM_FACTOR,
                            )
                        },
                    )
                    TimelineControlButton(
                        label = "Reset",
                        description = "Reset zoom",
                        testTag = "timeline_reset",
                        enabled = canReset,
                        onClick = { viewport = data.initialViewport },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (group in legendGroups) {
                    LegendChip(
                        label = group.label,
                        count = legendCounts[group] ?: 0,
                        color = palette.seriesColor(group),
                    )
                }
                if (reportTakenMs != null) {
                    ReportChip(color = palette.report)
                }
            }

            if (data.isEmpty) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .testTag("timeline_canvas"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No timeline entries",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                TimelineCanvas(
                    data = data,
                    viewport = viewport,
                    onViewportChange = { viewport = it },
                    palette = palette,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Pinch or drag to explore",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${TimelineMath.formatSpan(viewport.spanMs)} visible",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun timelineSummary(data: TimelineData): String {
    val groups = data.rows.size
    val groupLabel = if (groups == 1) "1 row" else "$groups rows"
    return "${TimelineMath.formatSpan(data.range.spanMs)} · ${data.eventCount} entries · $groupLabel"
}

@Composable
private fun TimelineControlButton(
    label: String,
    description: String,
    testTag: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = Modifier
            .height(32.dp)
            .testTag(testTag)
            .semantics { contentDescription = description },
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun LegendChip(
    label: String,
    count: Int,
    color: Color,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, CircleShape),
            )
            Text(text = label, style = MaterialTheme.typography.labelSmall)
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReportChip(color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
        contentColor = color,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, CircleShape),
            )
            Text(text = "Report taken", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 411,
    heightDp = 891,
    backgroundColor = 0xFF101418L,
)
@Composable
private fun TimelineScreenPreview() {
    BatteryArcheologistTheme {
        val fixture = remember { TimelineFixtures.build() }
        TimelineScreen(
            entries = fixture.entries,
            title = fixture.title,
            batteryEntries = fixture.batteryEntries,
            reportTakenMs = fixture.reportTakenMs,
            modifier = Modifier.padding(12.dp),
        )
    }
}
