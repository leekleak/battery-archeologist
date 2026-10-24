package com.leekleak.batteryarcheologist.ui.permission

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.core.model.ShizukuState

private const val SHIZUKU_DOWNLOAD_URL = "https://shizuku.rikka.app/download/"

/**
 * First-run gate shown until Shizuku is running and authorized.
 *
 * The screen keeps every Shizuku state visible (checking, not running,
 * permission denied, ready) and exposes a single primary action for the
 * current state plus refresh/open fallbacks.
 */
@Composable
fun PermissionScreen(
    state: ShizukuState,
    installed: Boolean,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .testTag("permission_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.permission_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        StatusCard(state = state, installed = installed)

        PermissionAction(
            state = state,
            installed = installed,
            onRequestPermission = onRequestPermission,
            onOpenShizuku = onOpenShizuku,
        )

        Column {
            if (state is ShizukuState.PermissionDenied) {
                TextButton(
                    onClick = onOpenShizuku,
                    modifier = Modifier.testTag("btn_open_shizuku"),
                ) {
                    Text(stringResource(R.string.action_open_shizuku))
                }
            }
            TextButton(
                onClick = onRefresh,
                modifier = Modifier.testTag("btn_refresh"),
            ) {
                Text(stringResource(R.string.action_refresh_status))
            }
        }

        Text(
            text = stringResource(R.string.permission_adb_only),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusCard(
    state: ShizukuState,
    installed: Boolean,
) {
    val title = when (state) {
        ShizukuState.Unknown -> stringResource(R.string.permission_checking_title)
        ShizukuState.NotRunning ->
            if (installed) {
                stringResource(R.string.permission_not_running_title)
            } else {
                stringResource(R.string.permission_not_installed_title)
            }
        ShizukuState.PermissionDenied ->
            stringResource(R.string.permission_permission_required_title)
        is ShizukuState.Ready -> stringResource(R.string.permission_connected_title)
    }
    val detail = when (state) {
        ShizukuState.Unknown -> stringResource(R.string.permission_checking_detail)
        ShizukuState.NotRunning ->
            if (installed) {
                stringResource(R.string.permission_not_running_detail)
            } else {
                stringResource(R.string.permission_not_installed_detail)
            }
        ShizukuState.PermissionDenied ->
            stringResource(R.string.permission_permission_required_detail)
        is ShizukuState.Ready -> {
            val mode = stringResource(
                if (state.isAdb) R.string.permission_mode_adb else R.string.permission_mode_root,
            )
            stringResource(R.string.permission_connected_detail, state.uid, mode)
        }
    }

    val containerColor = when (state) {
        is ShizukuState.Ready -> MaterialTheme.colorScheme.tertiaryContainer
        ShizukuState.PermissionDenied -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("shizuku_status"),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PermissionAction(
    state: ShizukuState,
    installed: Boolean,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
) {
    val context = LocalContext.current

    when (state) {
        ShizukuState.Unknown -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = stringResource(R.string.permission_waiting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ShizukuState.PermissionDenied -> {
            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_grant"),
            ) {
                Text(stringResource(R.string.action_grant_shizuku_access))
            }
        }

        ShizukuState.NotRunning -> {
            if (installed) {
                Button(
                    onClick = onOpenShizuku,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_open_shizuku"),
                ) {
                    Text(stringResource(R.string.action_open_shizuku_and_start))
                }
            } else {
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(SHIZUKU_DOWNLOAD_URL),
                                ),
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_install_shizuku"),
                ) {
                    Text(stringResource(R.string.action_install_shizuku))
                }
            }
        }

        is ShizukuState.Ready -> Unit
    }
}
