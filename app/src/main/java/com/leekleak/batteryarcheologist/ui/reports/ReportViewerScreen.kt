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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.ui.apps.AppsScreen
import com.leekleak.batteryarcheologist.ui.components.AppScaffold
import com.leekleak.batteryarcheologist.ui.components.BottomBar
import com.leekleak.batteryarcheologist.ui.components.SectionCard
import com.leekleak.batteryarcheologist.ui.navigation.ContentTab
import com.leekleak.batteryarcheologist.ui.overview.OverviewScreen
import com.leekleak.batteryarcheologist.ui.state.AppUiState
import com.leekleak.batteryarcheologist.ui.system.SystemScreen
import com.leekleak.batteryarcheologist.ui.timeline.TimelineTab

/**
 * Report viewer destination.
 *
 * Owns the title bar, the tab bar and the four report-backed tabs. Capture and
 * import actions deliberately stay on the reports home behind this screen.
 */
@Composable
fun ReportViewerScreen(
    state: AppUiState,
    onSelectTab: (ContentTab) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val report = state.report

    AppScaffold(
        title = stringResource(state.selectedTab.titleRes),
        modifier = Modifier.testTag("content_screen"),
        scrollState = null,
        onBack = onBack,
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
                ContentTab.OVERVIEW -> OverviewScreen(report, contentPadding)
                ContentTab.TIMELINE -> TimelineTab(report, contentPadding)
                ContentTab.SYSTEM -> SystemScreen(report, contentPadding)
                ContentTab.APPS -> AppsScreen(report, contentPadding)
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
            text = stringResource(R.string.overview_loading),
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
