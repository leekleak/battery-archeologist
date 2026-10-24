package com.leekleak.batteryarcheologist.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leekleak.batteryarcheologist.core.model.ShizukuState
import com.leekleak.batteryarcheologist.data.AndroidReportRepository
import com.leekleak.batteryarcheologist.ui.navigation.MainScreen
import com.leekleak.batteryarcheologist.ui.permission.PermissionScreen
import com.leekleak.batteryarcheologist.ui.state.MainViewModel
import com.leekleak.batteryarcheologist.ui.theme.BatteryArcheologistBackground
import com.leekleak.shizuku.ShizukuGateway

/**
 * Application root: owns the repository and view model, then switches between
 * the Shizuku permission gate and the main tab host.
 *
 * Shizuku state itself is only observed through [MainViewModel] so the screens
 * and the view model always render the same snapshot.
 */
@Composable
fun BatteryArcheologistRoot(gateway: ShizukuGateway) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember(appContext) {
        AndroidReportRepository(appContext, gateway)
    }
    val viewModel: MainViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                MainViewModel(
                    repository = repository,
                    shizukuState = gateway.state,
                    shizukuInstalled = { gateway.isShizukuInstalled() },
                    requestShizukuPermission = { gateway.requestPermission() },
                    openShizuku = { gateway.openShizuku() },
                    refreshShizuku = { gateway.refresh() },
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    BatteryArcheologistBackground(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true },
    ) {
        if (state.shizukuState is ShizukuState.Ready) {
            MainScreen(state = state, viewModel = viewModel)
        } else {
            PermissionScreen(
                state = state.shizukuState,
                installed = state.shizukuInstalled,
                onRequestPermission = viewModel::requestShizukuPermission,
                onOpenShizuku = viewModel::openShizuku,
                onRefresh = viewModel::refreshShizuku,
            )
        }
    }
}
