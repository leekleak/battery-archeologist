package com.leekleak.batteryarcheologist.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.data.ReportData
import com.leekleak.batteryarcheologist.data.SharedPreferencesTimelineFilterStore
import com.leekleak.batteryarcheologist.ui.components.NAVBAR_PADDING
import com.leekleak.batteryarcheologist.ui.components.SectionCard

/**
 * Timeline tab.
 *
 * The whole page scrolls vertically. The chart is a single item and only
 * claims horizontal drags for its inspection cursor, so vertical drags scroll
 * the page instead of fighting the chart. The cursor readout is an overlay
 * above the navigation bar, so its values stay visible while the (very tall,
 * all-metrics) chart scrolls behind it.
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

    val context = LocalContext.current
    val filterStore = remember(context) { SharedPreferencesTimelineFilterStore(context) }
    var filter by remember { mutableStateOf(filterStore.load()) }
    var filterSheetOpen by remember { mutableStateOf(false) }
    var readoutHeightPx by remember { mutableIntStateOf(0) }

    val reportEndMs = remember(history) {
        history.summaries.maxOfOrNull { it.endTimeMs }
    }
    val data = remember(history.entries, reportEndMs, filter) {
        TimelineData.build(
            entries = history.entries,
            reportTakenMs = reportEndMs,
            visibleMetrics = if (filter.active) filter.metrics else null,
        )
    }
    var cursorMs by remember(history) { mutableStateOf<Long?>(null) }
    val rawCursor = cursorMs ?: data.lastChangeMs ?: data.range.endMs
    val cursor = data.clampCursor(rawCursor) ?: rawCursor
    val palette = rememberTimelinePalette()

    val layoutDirection = LocalLayoutDirection.current
    val horizontalStart = contentPadding.calculateStartPadding(layoutDirection)
    val horizontalEnd = contentPadding.calculateEndPadding(layoutDirection)
    val navigationInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val readoutBottomGap = navigationInset + NAVBAR_PADDING + 16.dp
    val readoutHeight = with(LocalDensity.current) { readoutHeightPx.toDp() }
    val chartPadding = PaddingValues(
        start = horizontalStart,
        top = contentPadding.calculateTopPadding(),
        end = horizontalEnd,
        bottom = readoutBottomGap + readoutHeight + 8.dp,
    )

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = chartPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "timeline_chart") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("parsed_timeline"),
                ) {
                    TimelineScreen(
                        data = data,
                        cursorMs = cursor,
                        onCursorMsChange = {
                            cursorMs = data.clampCursor(it) ?: it
                        },
                    )
                }
            }
        }

        TimelineReadout(
            data = data,
            cursorMs = cursor,
            palette = palette,
            filterActive = filter.active,
            onCursorMsChange = {
                cursorMs = data.clampCursor(it) ?: it
            },
            onFilterClick = { filterSheetOpen = true },
            onHeightChanged = { readoutHeightPx = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    start = horizontalStart,
                    end = horizontalEnd,
                    bottom = NAVBAR_PADDING + 16.dp,
                ),
        )

        if (filterSheetOpen) {
            TimelineFilterSheet(
                availableMetrics = data.availableMetrics,
                initialFilter = filter,
                onDismiss = { filterSheetOpen = false },
                onApply = { applied ->
                    filter = applied
                    filterStore.save(applied)
                    filterSheetOpen = false
                },
            )
        }
    }
}
