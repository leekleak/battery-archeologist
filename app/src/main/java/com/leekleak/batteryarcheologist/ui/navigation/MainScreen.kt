package com.leekleak.batteryarcheologist.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.data.ReportStore
import com.leekleak.batteryarcheologist.ui.apps.AppsScreen
import com.leekleak.batteryarcheologist.ui.components.AppScaffold
import com.leekleak.batteryarcheologist.ui.components.BottomBar
import com.leekleak.batteryarcheologist.ui.components.NAVBAR_PADDING
import com.leekleak.batteryarcheologist.ui.overview.OverviewScreen
import com.leekleak.batteryarcheologist.ui.reports.ReportsScreen
import com.leekleak.batteryarcheologist.ui.state.AppUiState
import com.leekleak.batteryarcheologist.ui.state.CaptureStatus
import com.leekleak.batteryarcheologist.ui.state.MainViewModel
import com.leekleak.batteryarcheologist.ui.state.UiEffect
import com.leekleak.batteryarcheologist.ui.system.SystemScreen
import com.leekleak.batteryarcheologist.ui.timeline.TimelineTab
import kotlinx.coroutines.flow.collect

private val REPORT_MIME_TYPES = arrayOf(
    "text/plain",
    "application/zip",
    "application/gzip",
    "application/octet-stream",
)

/**
 * Main host once Shizuku is ready: floating scaffold, five tabs, capture action
 * and one-shot effects (snackbars and report sharing).
 */
@Composable
fun MainScreen(
    state: AppUiState,
    viewModel: MainViewModel,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(viewModel::importReport)
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UiEffect.Message -> snackbarHostState.showSnackbar(effect.text)
                is UiEffect.ShareReport -> {
                    if (!shareReport(context, effect.name)) {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.error_share_failed, effect.name),
                        )
                    }
                }
            }
        }
    }

    val capturing = state.captureStatus is CaptureStatus.Running

    Box(Modifier.fillMaxSize()) {
        AppScaffold(
            title = stringResource(state.selectedTab.titleRes),
            modifier = Modifier.testTag("content_screen"),
            scrollState = null,
            actions = {
                TextButton(
                    onClick = viewModel::capture,
                    enabled = !capturing && !state.reportLoading,
                    modifier = Modifier.testTag("btn_capture_top"),
                ) {
                    Text(
                        stringResource(
                            if (capturing) R.string.action_capturing else R.string.action_capture,
                        ),
                    )
                }
            },
            bottomBar = {
                BottomBar(
                    selected = state.selectedTab,
                    onSelect = viewModel::selectTab,
                )
            },
        ) { contentPadding ->
            when (state.selectedTab) {
                ContentTab.OVERVIEW -> OverviewScreen(
                    report = state.report,
                    loading = state.reportLoading,
                    contentPadding = contentPadding,
                    onCapture = viewModel::capture,
                )

                ContentTab.TIMELINE -> TimelineTab(
                    report = state.report,
                    contentPadding = contentPadding,
                    onCapture = viewModel::capture,
                )

                ContentTab.SYSTEM -> SystemScreen(
                    report = state.report,
                    contentPadding = contentPadding,
                    onCapture = viewModel::capture,
                )

                ContentTab.APPS -> AppsScreen(
                    report = state.report,
                    contentPadding = contentPadding,
                    onCapture = viewModel::capture,
                )

                ContentTab.REPORTS -> ReportsScreen(
                    report = state.report,
                    savedReports = state.savedReports,
                    contentPadding = contentPadding,
                    onImport = { importLauncher.launch(REPORT_MIME_TYPES) },
                    onOpen = viewModel::openReport,
                    onShare = viewModel::shareReport,
                    onDelete = viewModel::deleteReport,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = NAVBAR_PADDING + 8.dp),
        )
    }
}

/** Launches the system share chooser for a raw report file. */
private fun shareReport(context: Context, name: String): Boolean {
    val file = ReportStore(context).file(name) ?: return false
    return runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.action_share_report)),
        )
    }.isSuccess
}
