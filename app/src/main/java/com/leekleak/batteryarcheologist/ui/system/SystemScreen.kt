package com.leekleak.batteryarcheologist.ui.system

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.data.ReportData
import com.leekleak.batteryarcheologist.ui.components.ParserNoticeCard
import com.leekleak.batteryarcheologist.ui.components.SectionCard
import com.leekleak.batteryarcheologist.ui.components.SummaryRow
import com.leekleak.batteryarcheologist.ui.components.formatBytes

/**
 * System tab: one battery card plus the parser/file internals most users only
 * need when something looks wrong. Those internals start collapsed.
 */
@Composable
fun SystemScreen(
    report: ReportData,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("system_screen"),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { BatteryCard(report) }

        val hasFailures =
            report.historyFailure != null || report.checkinFailure != null
        if (hasFailures) {
            item {
                val failures = listOfNotNull(
                    report.historyFailure?.let {
                        stringResource(R.string.system_history_failure, it)
                    },
                    report.checkinFailure?.let {
                        stringResource(R.string.system_checkin_failure, it)
                    },
                )
                ParserNoticeCard(
                    title = stringResource(R.string.system_parsing_failed),
                    message = failures.joinToString("\n"),
                    isError = true,
                )
            }
        }

        item { ReportDetailsCard(report) }
    }
}

/** The single authority for battery capacity in the app. */
@Composable
private fun BatteryCard(report: ReportData) {
    val stats = report.checkin?.stats
    val battery = stats?.system?.battery
    val powerCapacity = report.power?.capacityMah?.takeIf { it > 0f }
    val estimatedCapacity = battery?.estimated_battery_capacity_mah ?: 0L

    val capacityText = when {
        powerCapacity != null -> {
            val capacity = "%.0f mAh".format(powerCapacity)
            if (report.capacityEstimated) {
                stringResource(R.string.system_capacity_estimated, capacity)
            } else {
                capacity
            }
        }
        estimatedCapacity > 0L -> stringResource(
            R.string.system_capacity_estimated,
            "$estimatedCapacity mAh",
        )
        else -> stringResource(R.string.label_unknown)
    }

    SectionCard(
        modifier = Modifier.testTag("system_summary"),
        title = stringResource(R.string.system_battery),
        subtitle = if (stats != null) {
            pluralStringResource(
                R.plurals.system_apps_in_report,
                stats.app.size,
                stats.app.size,
            )
        } else {
            stringResource(R.string.system_stats_unavailable)
        },
    ) {
        SummaryRow(stringResource(R.string.system_capacity), capacityText)
        if (battery != null) {
            SummaryRow(
                stringResource(R.string.system_battery_realtime),
                formatRealtimeHours(battery.battery_realtime_msec),
            )
            SummaryRow(
                stringResource(R.string.system_total_realtime),
                formatRealtimeHours(battery.total_realtime_msec),
            )
        }
    }
}

/** Collapsed-by-default parser and file metadata for the current report. */
@Composable
private fun ReportDetailsCard(report: ReportData) {
    var expanded by remember(report.savedName, report.capturedAt) { mutableStateOf(false) }
    val snapshot = report.snapshot
    val warnings = (report.checkin?.warnings?.size ?: 0) +
        (report.history?.warnings?.size ?: 0)
    val errors = (report.checkin?.errors?.size ?: 0) +
        (report.history?.errors?.size ?: 0) +
        (if (report.checkinFailure != null) 1 else 0) +
        (if (report.historyFailure != null) 1 else 0)
    val checkinVersion = snapshot.checkinVersion
        ?: report.checkin?.stats?.report_version

    SectionCard(
        title = stringResource(R.string.system_report_details),
        subtitle = stringResource(R.string.system_report_details_subtitle),
    ) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.testTag("btn_report_details"),
        ) {
            Text(
                stringResource(
                    if (expanded) {
                        R.string.action_hide_details
                    } else {
                        R.string.action_show_details
                    },
                ),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryRow(
                    stringResource(R.string.system_checkin_version),
                    checkinVersion?.toString() ?: stringResource(R.string.label_unknown),
                )
                SummaryRow(
                    stringResource(R.string.system_total_lines),
                    "%,d".format(snapshot.totalLines),
                )
                SummaryRow(
                    stringResource(R.string.system_history_lines),
                    "%,d".format(snapshot.historyLines),
                )
                SummaryRow(
                    stringResource(R.string.system_history_string_pool),
                    "%,d".format(snapshot.historyStringPoolLines),
                )
                SummaryRow(
                    stringResource(R.string.system_resets),
                    snapshot.resetEvents.toString(),
                )
                SummaryRow(
                    stringResource(R.string.system_size),
                    formatBytes(report.sizeBytes.toLong()),
                )
                report.savedName?.let {
                    SummaryRow(stringResource(R.string.system_saved_as), it)
                }
                SummaryRow(
                    stringResource(R.string.system_parser_warnings),
                    warnings.toString(),
                )
                SummaryRow(
                    stringResource(R.string.system_parser_errors),
                    errors.toString(),
                )
            }
        }
    }
}

private fun formatRealtimeHours(millis: Float?): String =
    "%.1f h".format((millis ?: 0f) / 3_600_000.0)
