/*
 * Adapted from Traffic Light's UsagePermissionRequest.kt
 * (https://github.com/leekleak/traffic-light), GPL-3.0, same author.
 */

package com.leekleak.batteryarcheologist.ui.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.core.model.ShizukuState

private const val SHIZUKU_HELP_URL = "https://shizuku.rikka.app/guide/setup/"
private const val SHIZUKU_DOWNLOAD_URL = "https://shizuku.rikka.app/download/"

/**
 * Primary action for the current Shizuku state: icon, label, test tag and the
 * callback that state requires.
 */
private data class PermissionPrimary(
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    val testTag: String,
    val onClick: () -> Unit,
)

/**
 * Full-screen Shizuku permission gate.
 *
 * Shows one centered hero, a state-specific explanation and two actions: help
 * (always) plus the primary action for the current state. The back affordance
 * is overlaid in the top-left corner.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PermissionScreen(
    state: ShizukuState,
    installed: Boolean,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val primary = when {
        state is ShizukuState.Ready -> PermissionPrimary(
            icon = R.drawable.arrow_forward,
            label = R.string.action_continue,
            testTag = "btn_continue",
            onClick = onRefresh,
        )

        state is ShizukuState.PermissionDenied -> PermissionPrimary(
            icon = R.drawable.grant,
            label = R.string.grant,
            testTag = "btn_grant",
            onClick = onRequestPermission,
        )

        state is ShizukuState.NotRunning && installed -> PermissionPrimary(
            icon = R.drawable.open_in_new,
            label = R.string.action_open_shizuku,
            testTag = "btn_open_shizuku",
            onClick = onOpenShizuku,
        )

        state is ShizukuState.NotRunning -> PermissionPrimary(
            icon = R.drawable.open_in_new,
            label = R.string.action_install_shizuku,
            testTag = "btn_install_shizuku",
            onClick = { openUrl(context, SHIZUKU_DOWNLOAD_URL) },
        )

        else -> PermissionPrimary(
            icon = R.drawable.repeat,
            label = R.string.action_refresh_status,
            testTag = "btn_refresh",
            onClick = onRefresh,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("permission_screen"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            PermissionHero(state = state, installed = installed)

            Column(
                modifier = Modifier.padding(top = 26.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(permissionTitle(state, installed)),
                    modifier = Modifier.testTag("shizuku_status"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = permissionDetail(state, installed),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { openUrl(context, SHIZUKU_HELP_URL) },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.testTag("btn_permission_help"),
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.help),
                            contentDescription = null,
                        )
                        Text(stringResource(R.string.help))
                    }
                }
                Button(
                    onClick = primary.onClick,
                    colors = ButtonDefaults.buttonColors(),
                    modifier = Modifier.testTag(primary.testTag),
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            painter = painterResource(primary.icon),
                            contentDescription = null,
                        )
                        Text(stringResource(primary.label))
                    }
                }
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .testTag("btn_permission_back"),
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = stringResource(R.string.action_back),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PermissionHero(
    state: ShizukuState,
    installed: Boolean,
) {
    Box(
        modifier = Modifier
            .size(124.dp)
            .clip(MaterialShapes.Sunny.toShape())
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        val icon = when (state) {
            ShizukuState.Unknown -> null
            ShizukuState.NotRunning ->
                if (installed) R.drawable.play_arrow else R.drawable.open_in_new

            ShizukuState.PermissionDenied -> R.drawable.shield
            is ShizukuState.Ready -> R.drawable.check_circle
        }

        if (icon == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Icon(
                modifier = Modifier.size(62.dp),
                painter = painterResource(icon),
                tint = MaterialTheme.colorScheme.onPrimary,
                contentDescription = null,
            )
        }
    }
}

@StringRes
private fun permissionTitle(
    state: ShizukuState,
    installed: Boolean,
): Int = when (state) {
    ShizukuState.Unknown -> R.string.permission_checking_title
    ShizukuState.NotRunning ->
        if (installed) {
            R.string.permission_not_running_title
        } else {
            R.string.permission_not_installed_title
        }

    ShizukuState.PermissionDenied -> R.string.permission_permission_required_title
    is ShizukuState.Ready -> R.string.permission_connected_title
}

@Composable
private fun permissionDetail(
    state: ShizukuState,
    installed: Boolean,
): String = when (state) {
    ShizukuState.Unknown -> stringResource(R.string.permission_checking_detail)
    ShizukuState.NotRunning ->
        stringResource(
            if (installed) {
                R.string.permission_not_running_detail
            } else {
                R.string.permission_not_installed_detail
            },
        )

    ShizukuState.PermissionDenied ->
        stringResource(R.string.permission_permission_required_detail)

    is ShizukuState.Ready -> stringResource(
        R.string.permission_connected_detail,
        state.uid,
        stringResource(
            if (state.isAdb) R.string.permission_mode_adb else R.string.permission_mode_root,
        ),
    )
}

/**
 * Opens an external URL, showing a toast when no activity can handle it.
 */
private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        Toast.makeText(context, R.string.no_browser_found, Toast.LENGTH_SHORT).show()
    }
}
