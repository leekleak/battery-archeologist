package com.leekleak.batteryarcheologist.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.leekleak.batteryarcheologist.data.AndroidReportRepository
import com.leekleak.batteryarcheologist.ui.navigation.MainScreen
import com.leekleak.batteryarcheologist.ui.state.MainViewModel
import com.leekleak.batteryarcheologist.ui.theme.BatteryArcheologistBackground
import com.leekleak.shizuku.ShizukuGateway

/**
 * Application root: owns the repository and view model, then renders the main
 * Navigation 3 host. The permission screen is a destination inside that host,
 * so the reports home is reachable without Shizuku.
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
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME ->
                    viewModel.refreshShizuku()

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BatteryArcheologistBackground(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true },
    ) {
        MainScreen(state = state, viewModel = viewModel)
    }
}
