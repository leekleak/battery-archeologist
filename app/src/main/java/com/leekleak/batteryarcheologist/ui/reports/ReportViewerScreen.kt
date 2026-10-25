package com.leekleak.batteryarcheologist.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.data.SharedPreferencesTimelineFilterStore
import com.leekleak.batteryarcheologist.ui.apps.AppLabelResolver
import com.leekleak.batteryarcheologist.ui.apps.AppsScreen
import com.leekleak.batteryarcheologist.ui.components.AppScaffold
import com.leekleak.batteryarcheologist.ui.components.BottomBar
import com.leekleak.batteryarcheologist.ui.components.SectionCard
import com.leekleak.batteryarcheologist.ui.navigation.ContentTab
import com.leekleak.batteryarcheologist.ui.other.OtherScreen
import com.leekleak.batteryarcheologist.ui.state.AppUiState
import com.leekleak.batteryarcheologist.ui.timeline.TimelineFilterButton
import com.leekleak.batteryarcheologist.ui.timeline.TimelineTab
import dev.chrisbanes.haze.rememberHazeState

/**
 * Report viewer destination.
 *
 * Owns the title bar, the tab bar and the three report-backed tabs. Capture and
 * import actions deliberately stay on the reports home behind this screen.
 */
@Composable
fun ReportViewerScreen(
    state: AppUiState,
    onSelectTab: (ContentTab) -> Unit,
    onTogglePin: (String) -> Unit,
    appLabelResolver: AppLabelResolver,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val report = state.report
    // Apps owns a combined title + search blur band, so the scaffold must not
    // stack its usual title blur over the same area.
    val appsOwnTopBlur = state.selectedTab == ContentTab.APPS && report != null
    val viewerHazeState = rememberHazeState()

    val context = LocalContext.current
    val timelineFilterStore = remember(context) {
        SharedPreferencesTimelineFilterStore(context)
    }
    var timelineFilter by remember(timelineFilterStore) {
        mutableStateOf(timelineFilterStore.load())
    }
    var timelineFilterSheetOpen by remember { mutableStateOf(false) }
    val timelineFilterAvailable = report?.history != null

    // The timeline owns the filter sheet, so close it when its tab leaves.
    LaunchedEffect(state.selectedTab) {
        if (state.selectedTab != ContentTab.TIMELINE) {
            timelineFilterSheetOpen = false
        }
    }

    AppScaffold(
        title = stringResource(state.selectedTab.titleRes),
        modifier = Modifier.testTag("content_screen"),
        scrollState = null,
        hazeState = if (appsOwnTopBlur) null else viewerHazeState,
        titleBlurEnabled = !appsOwnTopBlur,
        onBack = onBack,
        actions = {
            if (state.selectedTab == ContentTab.TIMELINE && timelineFilterAvailable) {
                TimelineFilterButton(
                    active = timelineFilter.active,
                    onClick = { timelineFilterSheetOpen = true },
                )
            }
        },
        bottomBar = if (report != null) {
            {
                BottomBar(
                    selected = state.selectedTab,
                    onSelect = onSelectTab,
                )
            }
        } else {
            null
        },
    ) { contentPadding ->
        if (report == null) {
            ViewerPlaceholder(
                loading = state.reportLoading || state.reportError == null,
                error = state.reportError,
                contentPadding = contentPadding,
                onRetry = onRetry,
            )
        } else {
            when (state.selectedTab) {
                ContentTab.TIMELINE -> TimelineTab(
                    report = report,
                    contentPadding = contentPadding,
                    filter = timelineFilter,
                    filterSheetOpen = timelineFilterSheetOpen,
                    onFilterDismiss = { timelineFilterSheetOpen = false },
                    onFilterApply = { applied ->
                        timelineFilter = applied
                        timelineFilterStore.save(applied)
                        timelineFilterSheetOpen = false
                    },
                )
                ContentTab.APPS -> AppsScreen(
                    report = report,
                    contentPadding = contentPadding,
                    pinnedApps = state.pinnedApps,
                    onTogglePin = onTogglePin,
                    appLabelResolver = appLabelResolver,
                )
                ContentTab.OTHER -> OtherScreen(report, contentPadding)
            }
        }
    }
}

/** Loading and retry states shown while the viewer has no parsed report yet. */
@Composable
private fun ViewerPlaceholder(
    loading: Boolean,
    error: String?,
    contentPadding: PaddingValues,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "viewer_state") {
            if (loading) {
                ViewerLoading()
            } else {
                ViewerError(error = error, onRetry = onRetry)
            }
        }
    }
}

@Composable
private fun ViewerLoading() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("viewer_loading"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
        Text(
            text = stringResource(R.string.viewer_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ViewerError(
    error: String?,
    onRetry: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.reports_load_failed_title)) {
        Text(
            text = error.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.testTag("btn_retry_report"),
        ) {
            Text(stringResource(R.string.action_retry))
        }
    }
}
