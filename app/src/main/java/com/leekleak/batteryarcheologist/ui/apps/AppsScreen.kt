package com.leekleak.batteryarcheologist.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
 * Apps tab: power-ranked apps, pinned apps and a search field.
 *
 * Power estimates are already paired with each app by the repository, so this
 * screen never recalculates [AppPowerStats]. Battery capacity lives in the
 * System tab and is deliberately absent here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    report: ReportData,
    contentPadding: PaddingValues,
    pinnedApps: Set<String>,
    onTogglePin: (String) -> Unit,
    appLabelResolver: AppLabelResolver,
) {
    var selectedPackage by remember(report.savedName) { mutableStateOf<String?>(null) }
    var query by rememberSaveable(report.savedName ?: report.raw.hashCode()) {
        mutableStateOf("")
    }
    val labelsByPackage by produceState(
        initialValue = emptyMap<String, String>(),
        appLabelResolver,
        report,
    ) {
        value = appLabelResolver.resolve(report.apps.mapNotNull { it.app.name })
    }
    val sections = remember(report, pinnedApps, query, labelsByPackage) {
        buildAppListSections(
            apps = report.apps,
            pinnedNames = pinnedApps,
            query = query,
            labelsByPackage = labelsByPackage,
        )
    }
    val selectedApp = selectedPackage?.let { name ->
        report.apps.firstOrNull { it.app.name == name }
    }
    val listState = rememberLazyListState()
    val pinnedInReport = remember(report, pinnedApps) {
        report.apps.count { it.app.name.orEmpty() in pinnedApps }
    }
    var previousPinnedInReport by remember(report) {
        mutableIntStateOf(pinnedInReport)
    }
    LaunchedEffect(pinnedInReport) {
        if (pinnedInReport > previousPinnedInReport) {
            // Show the new pinned entry instead of keeping the old scroll offset,
            // which would hide the pinned section above the viewport.
            listState.animateScrollToItem(0)
        }
        previousPinnedInReport = pinnedInReport
    }
    val layoutDirection = LocalLayoutDirection.current
    val horizontalStart = contentPadding.calculateStartPadding(layoutDirection)
    val horizontalEnd = contentPadding.calculateEndPadding(layoutDirection)

    Column(Modifier.fillMaxSize()) {
        if (report.apps.isNotEmpty()) {
            AppsSearchHeader(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier.padding(
                    start = horizontalStart,
                    top = contentPadding.calculateTopPadding(),
                    end = horizontalEnd,
                ),
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("app_list"),
            state = listState,
            contentPadding = if (report.apps.isEmpty()) {
                contentPadding
            } else {
                PaddingValues(
                    start = horizontalStart,
                    top = 8.dp,
                    end = horizontalEnd,
                    bottom = contentPadding.calculateBottomPadding(),
                )
            },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                report.apps.isEmpty() -> item(key = "apps_empty") {
                    NoAppDataCard(failure = report.checkinFailure)
                }

                sections.isEmpty -> item(key = "apps_no_matches") {
                    NoMatchesCard(query = query, onClear = { query = "" })
                }

                else -> {
                    if (sections.pinned.isNotEmpty()) {
                        item(key = "pinned_header") {
                            PinnedSectionHeader()
                        }
                        itemsIndexed(
                            items = sections.pinned,
                            key = { _, entry ->
                                "pinned:${entry.usage.app.uid}:${entry.usage.app.name}"
                            },
                        ) { index, entry ->
                            AppRow(
                                entry = entry,
                                rowIndex = index,
                                onClick = { selectedPackage = entry.usage.app.name },
                            )
                        }
                        if (sections.apps.isNotEmpty()) {
                            item(key = "pinned_separator") {
                                HorizontalDivider(
                                    modifier = Modifier
                                        .padding(vertical = 4.dp)
                                        .testTag("pinned_separator"),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }

                    itemsIndexed(
                        items = sections.apps,
                        key = { _, entry ->
                            "app:${entry.usage.app.uid}:${entry.usage.app.name}"
                        },
                    ) { index, entry ->
                        AppRow(
                            entry = entry,
                            rowIndex = sections.pinned.size + index,
                            onClick = { selectedPackage = entry.usage.app.name },
                        )
                    }
                }
            }
        }
    }

    selectedApp?.let { usage ->
        val packageName = usage.app.name.orEmpty()
        ModalBottomSheet(
            onDismissRequest = { selectedPackage = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            AppDetailSheet(
                usage = usage,
                displayLabel = labelsByPackage[packageName],
                pinned = packageName in pinnedApps,
                onTogglePin = { onTogglePin(packageName) },
            )
        }
    }
}

@Composable
private fun AppsSearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 4.dp),
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("app_search"),
            placeholder = { Text(stringResource(R.string.apps_search_hint)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                )
            },
            trailingIcon = if (query.isEmpty()) {
                null
            } else {
                {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.testTag("app_search_clear"),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.apps_search_clear),
                        )
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        )
    }
}

@Composable
private fun PinnedSectionHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .testTag("pinned_apps_header"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_push_pin_filled),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.apps_pinned),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun AppRow(
    entry: AppListEntry,
    rowIndex: Int,
    onClick: () -> Unit,
) {
    val app = entry.usage.app
    val cpuMs = (app.cpu?.user_time_ms ?: 0f) + (app.cpu?.system_time_ms ?: 0f)
    val powerText = entry.usage.power
        ?.let { AppPowerStats.formatPercent(it.devicePowerPct) }
        ?: stringResource(R.string.label_na)

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_row_$rowIndex"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(
                packageName = app.name.orEmpty(),
                label = entry.displayName,
                size = 44.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.displayName,
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (entry.displayName == app.name.orEmpty()) {
                            appUidSubtitle(app)
                        } else {
                            appRowSubtitle(app)
                        },
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

@Composable
private fun NoMatchesCard(query: String, onClear: () -> Unit) {
    SectionCard(title = stringResource(R.string.apps_no_matches_title)) {
        Text(
            text = stringResource(R.string.apps_no_matches_body, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onClear,
            modifier = Modifier.testTag("btn_clear_search"),
        ) {
            Text(stringResource(R.string.apps_search_clear))
        }
    }
}

@Preview(name = "App row light", showBackground = true, widthDp = 411)
@Composable
private fun AppRowPreview() {
    BatteryArcheologistTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) {
                AppRow(
                    entry = AppListEntry(
                        usage = previewAppUsage(),
                        displayName = "Example Video",
                    ),
                    rowIndex = 0,
                    onClick = {},
                )
            }
        }
    }
}
