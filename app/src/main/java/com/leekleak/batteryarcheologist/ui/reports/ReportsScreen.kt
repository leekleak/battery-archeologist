package com.leekleak.batteryarcheologist.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.data.ReportData
import com.leekleak.batteryarcheologist.data.SavedReport
import com.leekleak.batteryarcheologist.ui.components.EmptyState
import com.leekleak.batteryarcheologist.ui.components.SectionCard
import com.leekleak.batteryarcheologist.ui.components.SummaryRow
import com.leekleak.batteryarcheologist.ui.components.formatBytes
import com.leekleak.batteryarcheologist.ui.components.formatTimestamp
import com.leekleak.batteryarcheologist.ui.components.reportOriginLabel
import com.leekleak.batteryarcheologist.ui.components.reportSourceLabel

/**
 * Reports tab: import action, the report currently open in the app and every
 * report saved on the device.
 *
 * The screen never requires a current report: imports and saved reports stay
 * reachable before the first capture. The host title bar already names the tab,
 * so the sections here are only "Import" and "Saved reports".
 */
@Composable
fun ReportsScreen(
    report: ReportData?,
    savedReports: List<SavedReport>,
    contentPadding: PaddingValues,
    onImport: () -> Unit,
    onOpen: (String) -> Unit,
    onShare: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<SavedReport?>(null) }
    var showRawSheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("saved_reports"),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "import") {
            ImportCard(onImport = onImport)
        }

        if (report != null) {
            item(key = "current_report") {
                CurrentReportCard(
                    report = report,
                    onShare = report.savedName?.let { name -> { onShare(name) } },
                    onViewRaw = { showRawSheet = true },
                )
            }
        }

        item(key = "saved_reports_header") {
            SavedReportsHeader(reports = savedReports)
        }

        if (savedReports.isEmpty()) {
            item(key = "saved_reports_empty") {
                EmptyState(
                    title = stringResource(R.string.reports_no_saved_title),
                    body = stringResource(R.string.reports_no_saved_body),
                )
            }
        } else {
            items(
                items = savedReports,
                key = { it.name },
            ) { saved ->
                SavedReportCard(
                    saved = saved,
                    onOpen = { onOpen(saved.name) },
                    onShare = { onShare(saved.name) },
                    onDelete = { pendingDelete = saved },
                )
            }
        }
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
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showRawSheet && report != null) {
        RawReportSheet(
            report = report,
            onShare = report.savedName?.let { name ->
                {
                    showRawSheet = false
                    onShare(name)
                }
            },
            onDismiss = { showRawSheet = false },
        )
    }
}

/** Full-width import entry point with a short explanation of accepted files. */
@Composable
private fun ImportCard(onImport: () -> Unit) {
    SectionCard(title = stringResource(R.string.action_import)) {
        Button(
            onClick = onImport,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("btn_import_report"),
        ) {
            Text(stringResource(R.string.action_import_report))
        }
        Text(
            text = stringResource(R.string.reports_import_support),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Summary of the report currently parsed by the app, with raw-text actions. */
@Composable
private fun CurrentReportCard(
    report: ReportData,
    onShare: (() -> Unit)?,
    onViewRaw: () -> Unit,
) {
    SectionCard(
        modifier = Modifier.testTag("current_report"),
        title = stringResource(R.string.reports_current_title),
        subtitle = stringResource(
            R.string.reports_current_subtitle,
            reportOriginLabel(report.origin),
            formatTimestamp(
                report.capturedAt,
                stringResource(R.string.label_unknown_time),
            ),
        ),
    ) {
        report.savedName?.let {
            SummaryRow(stringResource(R.string.reports_saved_file), it)
        }
        SummaryRow(
            stringResource(R.string.reports_size),
            formatBytes(report.sizeBytes.toLong()),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onShare != null) {
                FilledTonalButton(
                    onClick = onShare,
                    modifier = Modifier.testTag("btn_share_raw"),
                ) {
                    Text(stringResource(R.string.action_share_raw))
                }
            }
            Button(
                onClick = onViewRaw,
                modifier = Modifier.testTag("btn_view_raw"),
            ) {
                Text(stringResource(R.string.action_view_raw))
            }
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

/** One report stored on the device, with open/share/delete actions. */
@Composable
private fun SavedReportCard(
    saved: SavedReport,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard(
        modifier = Modifier.testTag("saved_report_${saved.name}"),
        title = saved.originalName ?: saved.name,
        subtitle = stringResource(
            R.string.reports_saved_subtitle,
            formatTimestamp(
                saved.capturedAt,
                stringResource(R.string.label_unknown_time),
            ),
            reportSourceLabel(saved.source),
            formatBytes(saved.sizeBytes),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        ) {
            TextButton(onClick = onOpen) { Text(stringResource(R.string.action_open)) }
            TextButton(onClick = onShare) { Text(stringResource(R.string.action_share)) }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
        }
    }
}
