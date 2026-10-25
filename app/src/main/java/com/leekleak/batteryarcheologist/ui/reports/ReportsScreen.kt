package com.leekleak.batteryarcheologist.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.core.model.ShizukuState
import com.leekleak.batteryarcheologist.data.SavedReport
import com.leekleak.batteryarcheologist.ui.components.AppScaffold
import com.leekleak.batteryarcheologist.ui.components.EmptyState
import com.leekleak.batteryarcheologist.ui.components.SectionCard
import com.leekleak.batteryarcheologist.ui.components.formatBytes
import com.leekleak.batteryarcheologist.ui.components.formatTimestamp
import com.leekleak.batteryarcheologist.ui.components.reportSourceLabel
import com.leekleak.batteryarcheologist.ui.state.AppUiState
import com.leekleak.batteryarcheologist.ui.state.CaptureStatus
import com.leekleak.batteryarcheologist.ui.state.ResetStatus

/**
 * Reports home: the navigation root.
 *
 * Capture, import, system-stats reset and every saved report live here, so the
 * screen is usable before a report has been loaded and without Shizuku. The
 * report viewer is a separate destination pushed on top of this one.
 */
@Composable
fun ReportsHomeScreen(
    state: AppUiState,
    onNewReport: () -> Unit,
    onImport: () -> Unit,
    onSetupShizuku: () -> Unit,
    onOpenReport: (String) -> Unit,
    onViewRaw: (String?) -> Unit,
    onShare: (String) -> Unit,
    onDelete: (String) -> Unit,
    onReset: () -> Unit,
    onDismissRaw: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<SavedReport?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    AppScaffold(
        title = stringResource(R.string.reports_home_title),
        scrollState = null,
        bottomBar = null,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("reports_home"),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.shizukuState !is ShizukuState.Ready) {
                item(key = "shizuku_banner") {
                    ShizukuBanner(
                        state = state.shizukuState,
                        installed = state.shizukuInstalled,
                        onSetupShizuku = onSetupShizuku,
                    )
                }
            }

            item(key = "actions") {
                ReportActions(
                    captureStatus = state.captureStatus,
                    enabled = !state.isBusy,
                    onNewReport = onNewReport,
                    onImport = onImport,
                )
            }

            item(key = "system_stats") {
                SystemStatsCard(
                    resetStatus = state.resetStatus,
                    enabled = !state.isBusy,
                    onReset = { showResetDialog = true },
                )
            }

            item(key = "saved_reports_header") {
                SavedReportsHeader(reports = state.savedReports)
            }

            if (state.savedReports.isEmpty()) {
                item(key = "saved_reports_empty") {
                    EmptyState(
                        title = stringResource(R.string.reports_no_saved_title),
                        body = stringResource(R.string.reports_no_saved_body),
                    )
                }
            } else {
                items(
                    items = state.savedReports,
                    key = { it.name },
                ) { saved ->
                    SavedReportCard(
                        saved = saved,
                        onOpen = { onOpenReport(saved.name) },
                        onViewRaw = { onViewRaw(saved.name) },
                        onShare = { onShare(saved.name) },
                        onDelete = { pendingDelete = saved },
                    )
                }
            }

            val latest = state.report
            if (latest != null && latest.savedName == null) {
                item(key = "latest_capture") {
                    LatestCaptureCard(onViewRaw = { onViewRaw(null) })
                }
            }
        }

        state.rawSheet?.let { sheet ->
            RawReportSheet(
                rawText = sheet.raw,
                savedName = sheet.name,
                sizeBytes = sheet.sizeBytes,
                source = sheet.source,
                onShare = sheet.name?.let { name -> { onShare(name) } },
                onDismiss = onDismissRaw,
            )
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                modifier = Modifier.testTag("reset_dialog"),
                title = { Text(stringResource(R.string.reports_reset_dialog_title)) },
                text = { Text(stringResource(R.string.reports_reset_dialog_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showResetDialog = false
                            onReset()
                        },
                        modifier = Modifier.testTag("btn_reset_confirm"),
                    ) {
                        Text(stringResource(R.string.action_reset_stats))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showResetDialog = false },
                        modifier = Modifier.testTag("btn_reset_cancel"),
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        pendingDelete?.let { saved ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text(stringResource(R.string.reports_delete_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.reports_delete_body,
                            saved.originalName ?: saved.name,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingDelete = null
                            onDelete(saved.name)
                        },
                        modifier = Modifier.testTag("btn_delete_confirm"),
                    ) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { pendingDelete = null },
                        modifier = Modifier.testTag("btn_delete_cancel"),
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

/** Shizuku status banner with the single setup action for every non-ready state. */
@Composable
private fun ShizukuBanner(
    state: ShizukuState,
    installed: Boolean,
    onSetupShizuku: () -> Unit,
) {
    SectionCard(
        modifier = Modifier.testTag("shizuku_banner"),
        title = stringResource(R.string.reports_shizuku_title),
    ) {
        Text(
            text = stringResource(
                when {
                    state is ShizukuState.Unknown -> R.string.reports_shizuku_checking
                    state is ShizukuState.PermissionDenied -> R.string.reports_shizuku_permission
                    installed -> R.string.reports_shizuku_not_running
                    else -> R.string.reports_shizuku_not_installed
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(
            onClick = onSetupShizuku,
            modifier = Modifier.testTag("btn_setup_shizuku"),
        ) {
            Text(stringResource(R.string.action_setup_shizuku))
        }
    }
}

/** Capture and import entry points, disabled while any privileged work runs. */
@Composable
private fun ReportActions(
    captureStatus: CaptureStatus,
    enabled: Boolean,
    onNewReport: () -> Unit,
    onImport: () -> Unit,
) {
    val running = captureStatus as? CaptureStatus.Running

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onNewReport,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("btn_capture"),
        ) {
            Icon(
                painter = painterResource(R.drawable.add),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(
                    if (running != null && !running.importing) {
                        R.string.action_capturing
                    } else {
                        R.string.action_new_report
                    },
                ),
            )
        }
        FilledTonalButton(
            onClick = onImport,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("btn_import_report"),
        ) {
            Text(
                stringResource(
                    if (running?.importing == true) {
                        R.string.action_importing
                    } else {
                        R.string.action_import_report
                    },
                ),
            )
        }
        Text(
            text = stringResource(R.string.reports_import_support),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Destructive device maintenance, separated from report capture. */
@Composable
private fun SystemStatsCard(
    resetStatus: ResetStatus,
    enabled: Boolean,
    onReset: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.reports_system_stats_title)) {
        Text(
            text = stringResource(R.string.reports_system_stats_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onReset,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("btn_reset_battery_stats"),
        ) {
            Icon(
                painter = painterResource(R.drawable.reset_wrench),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(
                    if (resetStatus is ResetStatus.Running) {
                        R.string.action_resetting
                    } else {
                        R.string.action_reset_battery_stats
                    },
                ),
            )
        }
    }
}

/** Heading for the saved list, including the total space used on the device. */
@Composable
private fun SavedReportsHeader(reports: List<SavedReport>) {
    val totalBytes = remember(reports) { reports.sumOf { it.sizeBytes } }
    val summary = if (reports.isEmpty()) {
        stringResource(R.string.reports_nothing_saved)
    } else {
        stringResource(
            R.string.reports_summary,
            pluralStringResource(
                R.plurals.reports_saved_report_count,
                reports.size,
                reports.size,
            ),
            formatBytes(totalBytes),
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .testTag("saved_reports_header"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.reports_saved_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One report stored on the device; tapping the card opens the viewer. */
@Composable
private fun SavedReportCard(
    saved: SavedReport,
    onOpen: () -> Unit,
    onViewRaw: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("saved_report_${saved.name}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = saved.originalName ?: saved.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(
                    R.string.reports_saved_subtitle,
                    formatTimestamp(
                        saved.capturedAt,
                        stringResource(R.string.label_unknown_time),
                    ),
                    reportSourceLabel(saved.source),
                    formatBytes(saved.sizeBytes),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onOpen,
                    modifier = Modifier.testTag("btn_open_${saved.name}"),
                ) {
                    Text(stringResource(R.string.action_open))
                }
                TextButton(
                    onClick = onViewRaw,
                    modifier = Modifier.testTag("btn_view_raw_${saved.name}"),
                ) {
                    Text(stringResource(R.string.action_view_raw))
                }
                TextButton(
                    onClick = onShare,
                    modifier = Modifier.testTag("btn_share_${saved.name}"),
                ) {
                    Text(stringResource(R.string.action_share))
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("btn_delete_${saved.name}"),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            }
        }
    }
}

/** Explains why the newest capture is not in the saved list but keeps it inspectable. */
@Composable
private fun LatestCaptureCard(onViewRaw: () -> Unit) {
    SectionCard(
        modifier = Modifier.testTag("latest_capture"),
        title = stringResource(R.string.reports_latest_capture_title),
    ) {
        Text(
            text = stringResource(R.string.reports_latest_capture_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onViewRaw,
            modifier = Modifier.testTag("btn_view_raw_latest"),
        ) {
            Text(stringResource(R.string.action_view_raw))
        }
    }
}
