package com.leekleak.batteryarcheologist.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.core.analytics.AppPowerStats
import com.leekleak.batteryarcheologist.data.AppUsage
import com.leekleak.batteryarcheologist.data.ReportData
import com.leekleak.batteryarcheologist.ui.components.SectionCard
import com.leekleak.batteryarcheologist.ui.theme.BatteryArcheologistTheme

/**
 * Apps tab: the report's apps already paired with their power estimates and
 * sorted by device power in the repository.
 *
 * This screen never recalculates [AppPowerStats]. Battery capacity lives in the
 * System tab, so it is deliberately absent here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    report: ReportData,
    contentPadding: PaddingValues,
) {
    var selectedApp by remember(report) { mutableStateOf<AppUsage?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_list"),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "apps_header") {
            AppsHeader(appCount = report.apps.size)
        }

        if (report.apps.isEmpty()) {
            item(key = "apps_empty") {
                NoAppDataCard(failure = report.checkinFailure)
            }
        } else {
            items(
                items = report.apps,
                key = { "${it.app.uid}:${it.app.name.orEmpty()}" },
            ) { usage ->
                AppRow(
                    usage = usage,
                    onClick = { selectedApp = usage },
                )
            }
        }
    }

    selectedApp?.let { usage ->
        ModalBottomSheet(
            onDismissRequest = { selectedApp = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            AppDetailSheet(usage = usage)
        }
    }
}

@Composable
private fun AppsHeader(appCount: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(R.string.apps_by_power),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = pluralStringResource(R.plurals.apps_count, appCount, appCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppRow(
    usage: AppUsage,
    onClick: () -> Unit,
) {
    val app = usage.app
    val cpuMs = (app.cpu?.user_time_ms ?: 0f) + (app.cpu?.system_time_ms ?: 0f)
    val powerText = usage.power
        ?.let { AppPowerStats.formatPercent(it.devicePowerPct) }
        ?: stringResource(R.string.label_na)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = app.name.orEmpty(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = powerText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = appIdentity(app),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.apps_cpu_value, formatSeconds(cpuMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun NoAppDataCard(failure: String?) {
    SectionCard(title = stringResource(R.string.apps_no_data_title)) {
        Text(
            text = failure ?: stringResource(R.string.apps_no_data_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(name = "App row light", showBackground = true, widthDp = 411)
@Composable
private fun AppRowPreview() {
    BatteryArcheologistTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) {
                AppRow(usage = previewAppUsage(), onClick = {})
            }
        }
    }
}
