package com.leekleak.batteryarcheologist.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.core.history.parseutils.ActivitySummary
import com.leekleak.batteryarcheologist.data.ReportData
import com.leekleak.batteryarcheologist.ui.components.EmptyReportState
import com.leekleak.batteryarcheologist.ui.components.ParserNoticeCard
import com.leekleak.batteryarcheologist.ui.components.ReportSummaryCard
import com.leekleak.batteryarcheologist.ui.components.SectionCard
import com.leekleak.batteryarcheologist.ui.components.SummaryRow
import com.leekleak.batteryarcheologist.ui.components.TopAppsCard
import com.leekleak.batteryarcheologist.ui.theme.BatteryArcheologistTheme

/**
 * Landing tab: a compact report identity card, the highest-power apps and a
 * short parser notice. Full diagnostics live behind Report details on System.
 *
 * The screen itself owns the supplied [contentPadding] so its content scrolls
 * behind the floating title and bottom bars instead of stopping below them.
 */
@Composable
fun OverviewScreen(
    report: ReportData?,
    loading: Boolean,
    contentPadding: PaddingValues,
    onCapture: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("overview_screen"),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (report == null) {
            item {
                if (loading) {
                    LoadingReport()
                } else {
                    EmptyReportState(onCapture = onCapture)
                }
            }
        } else {
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
            if (warnings > 0 || errors > 0) {
                item {
                    ParserNoticeCard(
                        title = stringResource(
                            if (errors > 0) {
                                R.string.overview_parser_issues
                            } else {
                                R.string.overview_parser_warnings
                            },
                        ),
                        message = parserNoticeMessage(warnings, errors),
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

@Composable
private fun LoadingReport() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("overview_loading"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
        Text(
            text = stringResource(R.string.overview_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(name = "Overview empty light", showBackground = true)
@Composable
private fun OverviewEmptyLightPreview() {
    BatteryArcheologistTheme {
        OverviewScreen(
            report = null,
            loading = false,
            contentPadding = PaddingValues(16.dp),
            onCapture = {},
        )
    }
}

@Preview(
    name = "Overview empty dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun OverviewEmptyDarkPreview() {
    BatteryArcheologistTheme {
        OverviewScreen(
            report = null,
            loading = false,
            contentPadding = PaddingValues(16.dp),
            onCapture = {},
        )
    }
}
