package com.leekleak.batteryarcheologist.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.core.analytics.AppPowerStats
import com.leekleak.batteryarcheologist.data.AppUsage
import com.leekleak.batteryarcheologist.data.ReportData

/**
 * Concise identity card for the report currently shown in the app.
 *
 * Parser internals (checkin version, line counts, resets, byte sizes) belong
 * to the System tab's expandable report details instead of this overview.
 */
@Composable
fun ReportSummaryCard(
    report: ReportData,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        modifier = modifier.testTag("capture_summary"),
        title = stringResource(R.string.overview_report_title),
        subtitle = reportOriginLabel(report.origin),
    ) {
        SummaryRow(
            stringResource(R.string.overview_captured),
            formatTimestamp(report.capturedAt, stringResource(R.string.label_unknown_time)),
        )
        if (report.durationMs > 0L) {
            SummaryRow(
                stringResource(R.string.overview_capture_duration),
                formatDurationMs(report.durationMs),
            )
        }
        report.savedName?.let {
            SummaryRow(stringResource(R.string.overview_saved_as), it)
        }
    }
}

/**
 * Highest-power apps in the report, already ordered by the repository.
 *
 * Capacity is deliberately omitted here; the Battery card on System is the
 * single place that reports battery capacity.
 */
@Composable
fun TopAppsCard(
    apps: List<AppUsage>,
    modifier: Modifier = Modifier,
) {
    if (apps.isEmpty()) return

    SectionCard(
        modifier = modifier.testTag("top_apps"),
        title = stringResource(R.string.overview_top_apps_by_power),
    ) {
        apps.forEach { usage ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = usage.app.name.orEmpty(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = usage.power
                        ?.let { AppPowerStats.formatPercent(it.devicePowerPct) }
                        ?: stringResource(R.string.label_na),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * Compact notice for non-fatal parser warnings or a failed parse.
 *
 * The full diagnostics stay on the System tab, so overview screens only need
 * the issue count and a hint about where to look.
 */
@Composable
fun ParserNoticeCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(if (isError) "parser_error" else "parser_warning"),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            contentColor = if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
