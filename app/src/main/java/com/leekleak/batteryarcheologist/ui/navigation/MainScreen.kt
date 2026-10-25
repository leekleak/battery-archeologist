package com.leekleak.batteryarcheologist.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.data.ReportStore
import com.leekleak.batteryarcheologist.ui.apps.AppLabelResolver
import com.leekleak.batteryarcheologist.ui.components.NAVBAR_PADDING
import com.leekleak.batteryarcheologist.ui.permission.PermissionScreen
import com.leekleak.batteryarcheologist.ui.reports.ReportViewerScreen
import com.leekleak.batteryarcheologist.ui.reports.ReportsHomeScreen
import com.leekleak.batteryarcheologist.ui.state.AppUiState
import com.leekleak.batteryarcheologist.ui.state.MainViewModel
import com.leekleak.batteryarcheologist.ui.state.UiEffect
import kotlinx.coroutines.flow.collect

private val REPORT_MIME_TYPES = arrayOf(
    "text/plain",
    "application/zip",
    "application/gzip",
    "application/octet-stream",
)

/**
 * Navigation 3 host: the reports home is the root, the report viewer and the
 * Shizuku permission screen are pushed on top of it.
 *
 * The host owns the shared snackbar, the document picker used by the home's
 * import action, and the one-shot effect handling that turns view-model events
 * into navigation or snackbars.
 */
@Composable
fun MainScreen(
    state: AppUiState,
    viewModel: MainViewModel,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val navigator = rememberAppNavigator()
    val appLabelResolver = remember(context.applicationContext) {
        AppLabelResolver(context.applicationContext)
    }

    // NavDisplay remembers entries per back stack, so entry content must read
    // the latest state through a State instead of capturing this composition's
    // value.
    val currentState = rememberUpdatedState(state)

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

                UiEffect.OpenPermission -> navigator.goTo(PermissionKey)
                UiEffect.ClosePermission -> {
                    if (navigator.current == PermissionKey) {
                        navigator.goBack()
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = navigator.backStack,
            onBack = {
                when (navigator.current) {
                    is ReportViewerKey -> viewModel.closeReport()
                    PermissionKey -> viewModel.cancelPermission()
                    else -> Unit
                }
                navigator.goBack()
            },
            entryProvider = entryProvider<NavKey> {
                entry<ReportsKey> {
                    ReportsHomeScreen(
                        state = currentState.value,
                        onNewReport = viewModel::requestNewReport,
                        onImport = { importLauncher.launch(REPORT_MIME_TYPES) },
                        onSetupShizuku = viewModel::requestPermissionScreen,
                        onOpenReport = { navigator.goTo(ReportViewerKey(it)) },
                        onViewRaw = viewModel::requestRaw,
                        onShare = viewModel::shareReport,
                        onDelete = viewModel::deleteReport,
                        onReset = viewModel::requestReset,
                        onDismissRaw = viewModel::dismissRawSheet,
                    )
                }

                entry<ReportViewerKey> { key ->
                    LaunchedEffect(key.reportName) {
                        viewModel.ensureReport(key.reportName)
                    }
                    ReportViewerScreen(
                        state = currentState.value,
                        onSelectTab = viewModel::selectTab,
                        onTogglePin = viewModel::togglePinnedApp,
                        appLabelResolver = appLabelResolver,
                        onBack = {
                            viewModel.closeReport()
                            navigator.goBack()
                        },
                        onRetry = { viewModel.ensureReport(key.reportName) },
                    )
                }

                entry<PermissionKey> {
                    PermissionScreen(
                        state = currentState.value.shizukuState,
                        installed = currentState.value.shizukuInstalled,
                        onRequestPermission = viewModel::requestShizukuPermission,
                        onOpenShizuku = viewModel::openShizuku,
                        onRefresh = viewModel::refreshShizuku,
                        onBack = {
                            viewModel.cancelPermission()
                            navigator.goBack()
                        },
                    )
                }
            },
            transitionSpec = {
                if (navigator.backStack.size == 1) {
                    fadeIn(tween()) togetherWith fadeOut(tween())
                } else {
                    slideInHorizontally { it } togetherWith
                        (slideOutHorizontally { -it / 2 } + scaleOut(targetScale = 0.7f) + fadeOut())
                }
            },
            popTransitionSpec = {
                slideInHorizontally { -it / 2 } +
                    scaleIn(initialScale = 0.7f) +
                    fadeIn(tween()) togetherWith
                    slideOutHorizontally { it }
            },
            predictivePopTransitionSpec = {
                slideInHorizontally { -it / 2 } +
                    scaleIn(initialScale = 0.7f) +
                    fadeIn(tween()) togetherWith
                    slideOutHorizontally { it }
            },
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(
                    bottom = if (navigator.current is ReportViewerKey) {
                        NAVBAR_PADDING + 8.dp
                    } else {
                        8.dp
                    },
                ),
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
