package com.leekleak.batteryarcheologist.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.core.history.parseutils.ActivitySummary
import com.leekleak.batteryarcheologist.data.ReportData
import com.leekleak.batteryarcheologist.ui.components.ParserNoticeCard
import com.leekleak.batteryarcheologist.ui.components.ReportSummaryCard
import com.leekleak.batteryarcheologist.ui.components.SectionCard
import com.leekleak.batteryarcheologist.ui.components.SummaryRow
import com.leekleak.batteryarcheologist.ui.components.TopAppsCard

/**
 * Landing tab: a compact report identity card, the highest-power apps and a
 * short parser notice. Full diagnostics live behind Report details on System.
 *
 * The screen itself owns the supplied [contentPadding] so its content scrolls
 * behind the floating title and bottom bars instead of stopping below them.
 */
@Composable
fun OverviewScreen(
    report: ReportData,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("overview_screen"),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ReportSummaryCard(report) }

        if (report.apps.isNotEmpty()) {
            item { TopAppsCard(apps = report.apps.take(5)) }
        }

        val warnings = (report.history?.warnings?.size ?: 0) +
            (report.checkin?.warnings?.size ?: 0)
        val errors = (report.history?.errors?.size ?: 0) +
            (report.checkin?.errors?.size ?: 0) +
            (if (report.historyFailure != null) 1 else 0) +
            (if (report.checkinFailure != null) 1 else 0)
        val unsupported = report.history?.unsupportedKeys.orEmpty()
        if (warnings > 0 || errors > 0 || unsupported.isNotEmpty()) {
            item {
                val title = when {
                    errors > 0 -> R.string.overview_parser_issues
                    warnings > 0 -> R.string.overview_parser_warnings
                    else -> R.string.overview_parser_unsupported
                }
                val message = if (warnings > 0 || errors > 0) {
                    parserNoticeMessage(warnings, errors)
                } else {
                    stringResource(
                        R.string.overview_parser_unsupported_notice,
                        unsupported.size,
                    )
                }
                ParserNoticeCard(
                    title = stringResource(title),
                    message = message,
                    isError = errors > 0,
                )
            }
        }

        report.history?.let { history ->
            val drains = history.summaries.filter {
                it.initialBatteryLevel >= 0 &&
                    it.finalBatteryLevel >= 0 &&
                    it.initialBatteryLevel != it.finalBatteryLevel
            }
            if (drains.isNotEmpty()) {
                item { DischargeSummaryCard(drains) }
            }
        }
    }
}

/** Two-or-three-value discharge summary; detailed intervals stay on Timeline. */
@Composable
private fun DischargeSummaryCard(drains: List<ActivitySummary>) {
    val last = drains.lastOrNull() ?: return
    val durationMinutes =
        ((last.endTimeMs - last.startTimeMs) / 60_000L).coerceAtLeast(0)

    SectionCard(title = stringResource(R.string.overview_discharge)) {
        SummaryRow(stringResource(R.string.overview_intervals), drains.size.toString())
        SummaryRow(
            stringResource(R.string.overview_last_change),
            "${last.initialBatteryLevel}% → ${last.finalBatteryLevel}%",
        )
        SummaryRow(
            stringResource(R.string.overview_last_interval),
            "${durationMinutes / 60}h ${durationMinutes % 60}m",
        )
    }
}

@Composable
private fun parserNoticeMessage(warnings: Int, errors: Int): String {
    val errorText = if (errors > 0) {
        pluralStringResource(R.plurals.overview_parser_error_count, errors, errors)
    } else {
        null
    }
    val warningText = if (warnings > 0) {
        pluralStringResource(R.plurals.overview_parser_warning_count, warnings, warnings)
    } else {
        null
    }
    val separator = " ${stringResource(R.string.common_and)} "
    val issues = listOfNotNull(errorText, warningText).joinToString(separator)
    return stringResource(R.string.overview_parser_notice, issues)
}
