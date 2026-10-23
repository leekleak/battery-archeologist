package com.leekleak.batteryarcheologist.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.core.model.CheckinSnapshot
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App as ProtoApp
import com.leekleak.batteryarcheologist.core.parse.CheckinInspector
import com.leekleak.batteryarcheologist.core.analytics.AppPowerStats
import com.leekleak.batteryarcheologist.core.analytics.AppPowerStat
import com.leekleak.batteryarcheologist.core.bugreport.BugreportUtils
import com.leekleak.batteryarcheologist.core.checkin.checkinparse.CheckinParser
import com.leekleak.batteryarcheologist.core.checkin.checkinparse.CheckinResult
import com.leekleak.batteryarcheologist.core.history.parseutils.HistoryParser
import com.leekleak.batteryarcheologist.core.history.parseutils.HistoryResult
import com.leekleak.batteryarcheologist.core.history.parseutils.HistorySummaryFormat
import com.leekleak.batteryarcheologist.data.ReportStore
import com.leekleak.batteryarcheologist.ui.timeline.TimelineScreen
import com.leekleak.batteryarcheologist.ui.theme.BatteryArcheologistBackground
import com.leekleak.shizuku.ShizukuGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.leekleak.batteryarcheologist.core.model.ShizukuState
import androidx.compose.ui.platform.testTag

private sealed interface CaptureState {
    data object Idle : CaptureState
    data object Running : CaptureState
    data class Success(
        val snapshot: CheckinSnapshot,
        val sizeBytes: Int,
        val durationMs: Long,
        val preview: String,
        val savedName: String? = null,
        val history: HistoryResult? = null,
        val historyFailure: String? = null,
        val checkin: CheckinResult? = null,
        val checkinFailure: String? = null,
        val sourceLabel: String = "Current capture",
    ) : CaptureState
    data class Error(val message: String) : CaptureState
}

private enum class ContentTab(val title: String) {
    OVERVIEW("Overview"),
    TIMELINE("Timeline"),
    SYSTEM("System"),
    APPS("Apps"),
    REPORTS("Reports"),
}

private fun ContentTab.iconRes(): Int = when (this) {
    ContentTab.OVERVIEW -> R.drawable.ic_overview
    ContentTab.TIMELINE -> R.drawable.ic_timeline
    ContentTab.SYSTEM -> R.drawable.ic_system
    ContentTab.APPS -> R.drawable.ic_apps
    ContentTab.REPORTS -> R.drawable.ic_reports
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveBottomBar(selectedTab: Int, onSelect: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors().copy(
                toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            content = {
                ContentTab.entries.forEachIndexed { index, tab ->
                    Button(
                        onClick = { onSelect(index) },
                        colors = if (selectedTab == index) {
                            ButtonDefaults.filledTonalButtonColors()
                        } else {
                            ButtonDefaults.textButtonColors()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("tab_${tab.name.lowercase()}"),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(tab.iconRes()),
                                contentDescription = tab.title,
                                modifier = Modifier.size(20.dp),
                            )
                            AnimatedVisibility(visible = selectedTab == index) {
                                Text(
                                    text = tab.title,
                                    modifier = Modifier.padding(start = 4.dp),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
fun BatteryArcheologistRoot(gateway: ShizukuGateway) {
    val state by gateway.state.collectAsStateWithLifecycle()

    BatteryArcheologistBackground(
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true },
    ) {
        when (state) {
            is ShizukuState.Ready -> ContentScreen(gateway, state as ShizukuState.Ready)
            else -> PermissionScreen(gateway, state)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PermissionScreen(gateway: ShizukuGateway, state: ShizukuState) {
    val context = LocalContext.current
    val installed = remember(state) { gateway.isShizukuInstalled() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .testTag("permission_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(40.dp))
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(MaterialShapes.Cookie12Sided.toShape())
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "BA",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Battery Archeologist",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Battery Historian on your phone. Shizuku is required to read privileged battery stats.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))
        StatusCard(state = state, installed = installed)
        Spacer(Modifier.height(20.dp))

        when (state) {
            ShizukuState.PermissionDenied -> {
                Button(
                    onClick = { gateway.requestPermission() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_grant"),
                ) { Text("Grant Shizuku access") }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { gateway.openShizuku() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_open_shizuku"),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                ) { Text("Open Shizuku") }
            }

            ShizukuState.NotRunning -> {
                if (installed) {
                    Button(
                        onClick = { gateway.openShizuku() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_open_shizuku"),
                    ) { Text("Open Shizuku and start the service") }
                } else {
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://shizuku.rikka.app/download/"),
                                ),
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_install_shizuku"),
                    ) { Text("Install Shizuku") }
                }
            }

            else -> Unit
        }

        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = { gateway.refresh() },
            modifier = Modifier.testTag("btn_refresh"),
        ) { Text("Refresh status") }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "ADB mode only. No root required.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusCard(state: ShizukuState, installed: Boolean) {
    val (title, detail) = when (state) {
        ShizukuState.Unknown -> "Checking Shizuku…" to "Looking for the Shizuku binder."
        ShizukuState.NotRunning ->
            if (installed) "Shizuku is not running" to "Open Shizuku and start it via ADB or wireless debugging."
            else "Shizuku is not installed" to "Install Shizuku, start it, then come back here."
        ShizukuState.PermissionDenied -> "Permission required" to "Shizuku is running. Grant this app access."
        is ShizukuState.Ready -> "Connected" to "uid ${state.uid} · ${if (state.isAdb) "ADB" else "root"}"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("shizuku_status"),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                is ShizukuState.Ready -> MaterialTheme.colorScheme.tertiaryContainer
                ShizukuState.PermissionDenied -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentScreen(gateway: ShizukuGateway, ready: ShizukuState.Ready) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reportStore = remember { ReportStore(context) }
    var reportNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var captureState by remember { mutableStateOf<CaptureState>(CaptureState.Idle) }
    var autoCaptured by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(captureState) {
        reportNames = withContext(Dispatchers.IO) {
            reportStore.list().map { it.name }
        }
    }

    suspend fun buildSuccess(
        raw: String,
        snapshot: CheckinSnapshot,
        durationMs: Long,
        savedName: String?,
        sourceLabel: String,
    ): CaptureState.Success {
        val history = withContext(Dispatchers.Default) {
            runCatching {
                HistoryParser.analyze(
                    rawCheckin = raw,
                    format = HistorySummaryFormat.BATTERY_LEVEL,
                    packages = emptyList(),
                )
            }
        }
        val checkin = withContext(Dispatchers.Default) {
            runCatching { CheckinParser.parse(raw, emptyList()) }
        }
        history.getOrNull()?.let { parsed ->
            Timber.i(
                "HistoryParser: entries=%d summaries=%d errors=%d",
                parsed.entries.size,
                parsed.summaries.size,
                parsed.errors.size,
            )
        } ?: Timber.w(history.exceptionOrNull(), "HistoryParser failed")
        checkin.getOrNull()?.let { parsed ->
            Timber.i(
                "CheckinParser: apps=%d warnings=%d errors=%d",
                parsed.stats?.app?.size ?: 0,
                parsed.warnings.size,
                parsed.errors.size,
            )
        } ?: Timber.w(checkin.exceptionOrNull(), "CheckinParser failed")
        return CaptureState.Success(
            snapshot = snapshot,
            sizeBytes = raw.toByteArray(Charsets.UTF_8).size,
            durationMs = durationMs,
            preview = raw.lineSequence().take(40).joinToString("\n"),
            savedName = savedName,
            history = history.getOrNull(),
            historyFailure = history.exceptionOrNull()?.message,
            checkin = checkin.getOrNull(),
            checkinFailure = checkin.exceptionOrNull()?.message,
            sourceLabel = sourceLabel,
        )
    }

    fun capture() {
        if (captureState is CaptureState.Running) return
        captureState = CaptureState.Running
        scope.launch {
            val result = gateway.captureCheckin()
            if (!result.succeeded) {
                captureState = CaptureState.Error(
                    result.stderr.ifBlank { "Capture failed with exit code ${result.exitCode}" },
                )
                return@launch
            }
            val snapshot = CheckinInspector.inspect(result.stdout)
            val savedName = runCatching {
                withContext(Dispatchers.IO) {
                    ReportStore(context).saveQuickCapture(result.stdout, snapshot).name
                }
            }.getOrNull()
            captureState = buildSuccess(
                raw = result.stdout,
                snapshot = snapshot,
                durationMs = result.durationMs,
                savedName = savedName,
                sourceLabel = "Current capture",
            )
        }
    }

    fun openReport(name: String) {
        if (captureState is CaptureState.Running) return
        captureState = CaptureState.Running
        scope.launch {
            val raw = withContext(Dispatchers.IO) { reportStore.read(name) }
            if (raw == null) {
                captureState = CaptureState.Error("Could not read saved report $name")
                return@launch
            }
            val snapshot = CheckinInspector.inspect(raw)
            captureState = buildSuccess(
                raw = raw,
                snapshot = snapshot,
                durationMs = 0,
                savedName = name,
                sourceLabel = "Saved report",
            )
            selectedTab = ContentTab.TIMELINE.ordinal
        }
    }

    fun shareReport(name: String) {
        val file = reportStore.file(name) ?: return
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share report"))
        }
    }

    suspend fun importReport(uri: Uri) {
        if (captureState is CaptureState.Running) return
        captureState = CaptureState.Running
        val name = uri.lastPathSegment ?: "imported-report"
        val bytes = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
        }
        if (bytes == null || bytes.isEmpty()) {
            captureState = CaptureState.Error("Could not read the selected report")
            return
        }

        val checkin = withContext(Dispatchers.Default) {
            runCatching {
                val files = runCatching { BugreportUtils.contents(name, bytes) }
                    .getOrElse { mapOf(name to bytes) }
                val texts = files.values.map { String(it, Charsets.UTF_8) }
                val bugreport = texts.firstOrNull { BugreportUtils.isBugReport(it) }
                bugreport?.let { BugreportUtils.extractBatterystatsCheckin(it) }
                    ?: texts.firstOrNull { it.contains(",i,vers,") }
            }.getOrNull()
        }
        if (checkin.isNullOrBlank()) {
            captureState = CaptureState.Error("No Battery Historian checkin data found in this file")
            return
        }

        val savedName = withContext(Dispatchers.IO) {
            runCatching { reportStore.saveImported(checkin, name).name }.getOrNull()
        }
        captureState = buildSuccess(
            raw = checkin,
            snapshot = CheckinInspector.inspect(checkin),
            durationMs = 0,
            savedName = savedName,
            sourceLabel = "Imported report",
        )
        selectedTab = ContentTab.TIMELINE.ordinal
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch { importReport(uri) }
        }
    }

    LaunchedEffect(Unit) {
        if (!autoCaptured && captureState is CaptureState.Idle) {
            autoCaptured = true
            capture()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Battery Archeologist", fontWeight = FontWeight.SemiBold) },
                actions = {
                    TextButton(
                        onClick = { capture() },
                        enabled = captureState !is CaptureState.Running,
                        modifier = Modifier.testTag("btn_capture_top"),
                    ) {
                        Text(if (captureState is CaptureState.Running) "Capturing…" else "Capture")
                    }
                },
            )
        },
        modifier = Modifier.testTag("content_screen"),
        containerColor = Color.Transparent,
        bottomBar = {
            if (captureState is CaptureState.Success) {
                ExpressiveBottomBar(
                    selectedTab = selectedTab,
                    onSelect = { selectedTab = it },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        (captureState as? CaptureState.Success)?.sourceLabel ?: "Current capture",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Live data from dumpsys batterystats",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(ready)
            }

            when (val current = captureState) {
                CaptureState.Idle, CaptureState.Running, is CaptureState.Error -> {
                    CapturePanel(
                        state = current,
                        uid = ready.uid,
                        onCapture = { capture() },
                    )
                }

                is CaptureState.Success -> {
                    when (ContentTab.entries[selectedTab]) {
                        ContentTab.OVERVIEW -> OverviewTab(
                            success = current,
                            reportNames = reportNames,
                            onCapture = { capture() },
                        )

                        ContentTab.TIMELINE -> TimelineTab(current)
                        ContentTab.SYSTEM -> SystemTab(current)
                        ContentTab.APPS -> AppsTab(current)
                        ContentTab.REPORTS -> ReportsTab(
                            success = current,
                            reportNames = reportNames,
                            onOpenReport = ::openReport,
                            onShareReport = ::shareReport,
                            onImport = {
                                importLauncher.launch(
                                    arrayOf(
                                        "text/plain",
                                        "application/zip",
                                        "application/gzip",
                                        "application/octet-stream",
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CapturePanel(state: CaptureState, uid: Int, onCapture: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(
            onClick = onCapture,
            enabled = state !is CaptureState.Running,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("btn_capture"),
        ) {
            Text(if (state is CaptureState.Running) "Capturing…" else "Capture battery stats")
        }

        when (state) {
            CaptureState.Idle -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ready to capture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Reads dumpsys batterystats -c through Shizuku as uid $uid.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            CaptureState.Running -> {
                Card(Modifier.fillMaxWidth().testTag("capture_running")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Running dumpsys batterystats -c as uid $uid…")
                    }
                }
            }

            is CaptureState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().testTag("capture_error"),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Capture failed", fontWeight = FontWeight.SemiBold)
                        Text(state.message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            is CaptureState.Success -> Unit
        }
    }
}

@Composable
private fun OverviewTab(
    success: CaptureState.Success,
    reportNames: List<String>,
    onCapture: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CaptureMetaCard(success)
        HistoryStatusCard(success.history, success.historyFailure)
        CheckinSummaryCard(success.checkin, success.checkinFailure)
        SavedReportsCard(reportNames)
        Button(onClick = onCapture, modifier = Modifier.fillMaxWidth().testTag("btn_capture")) {
            Text("Capture again")
        }
    }
}

@Composable
private fun TimelineTab(success: CaptureState.Success) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val history = success.history
        if (history != null) {
            TimelineScreen(
                entries = history.entries,
                title = "Timeline",
                reportTakenMs = history.summaries.lastOrNull()?.endTimeMs,
                modifier = Modifier.testTag("parsed_timeline"),
            )
            DischargeIntervalsCard(history)
            HistoryStatusCard(history, null)
        } else {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    success.historyFailure ?: "Timeline data is not available for this capture.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SystemTab(success: CaptureState.Success) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CheckinSummaryCard(success.checkin, success.checkinFailure)
        HistoryStatusCard(success.history, success.historyFailure)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppsTab(success: CaptureState.Success) {
    val stats = success.checkin?.stats
    if (stats == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    success.checkinFailure ?: "App data is not available for this capture.",
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        return
    }

    val apps = remember(stats) {
        stats.app
            .filter { !it.name.isNullOrBlank() }
            .sortedByDescending {
                (it.cpu?.user_time_ms ?: 0f) + (it.cpu?.system_time_ms ?: 0f)
            }
    }
    var selectedApp by remember { mutableStateOf<ProtoApp?>(null) }
    val powerReport = remember(stats) { AppPowerStats.calculate(stats) }
    val powerByKey = remember(powerReport) {
        powerReport.apps.associateBy { "${it.uid}:${it.name}" }
    }
    val sortedApps = remember(apps, powerByKey) {
        apps.sortedWith(
            compareByDescending<ProtoApp> {
                powerByKey["${it.uid}:${it.name.orEmpty()}"]?.devicePowerPct ?: 0f
            }.thenByDescending {
                (it.cpu?.user_time_ms ?: 0f) + (it.cpu?.system_time_ms ?: 0f)
            }.thenBy { it.name.orEmpty() },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Apps by power", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "${sortedApps.size} apps · capacity ${"%.0f".format(powerReport.capacityMah)} mAh" +
                    if (powerReport.capacityEstimated) " (estimated)" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(sortedApps, key = { "${it.uid}:${it.name.orEmpty()}" }) { app ->
            AppRow(
                app = app,
                power = powerByKey["${app.uid}:${app.name.orEmpty()}"],
                onClick = { selectedApp = app },
            )
        }
    }

    selectedApp?.let { app ->
        ModalBottomSheet(
            onDismissRequest = { selectedApp = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            AppDetailSheet(
                app = app,
                power = powerByKey["${app.uid}:${app.name.orEmpty()}"],
                capacityMah = powerReport.capacityMah,
                capacityEstimated = powerReport.capacityEstimated,
            )
        }
    }
}

@Composable
private fun AppDetailSheet(
    app: ProtoApp,
    power: AppPowerStat?,
    capacityMah: Float,
    capacityEstimated: Boolean,
) {
    val cpu = app.cpu
    val cpuUser = cpu?.user_time_ms ?: 0f
    val cpuSystem = cpu?.system_time_ms ?: 0f
    val cpuMs = cpuUser + cpuSystem
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .testTag("app_detail"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            app.name.orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "uid ${app.uid ?: 0}" +
                if (!app.version_name.isNullOrBlank()) " · ${app.version_name}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        if (power != null && capacityMah > 0f) {
            SummaryRow(
                "Device power",
                "${AppPowerStats.formatPercent(power.devicePowerPct)} · ${"%.4f".format(power.devicePowerMah)} mAh",
            )
            SummaryRow(
                "CPU power",
                "${AppPowerStats.formatPercent(power.cpuPowerPct)} · ${"%.4f".format(power.cpuPowerMah)} mAh",
            )
            SummaryRow(
                "Capacity",
                "%.0f mAh%s".format(capacityMah, if (capacityEstimated) " (estimated)" else ""),
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
        } else {
            SummaryRow("Device power", "unavailable (no capacity)")
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
        }
        SummaryRow("CPU total", "%.1f s".format(cpuMs / 1000.0))
        SummaryRow("CPU user", "%.1f s".format(cpuUser / 1000.0))
        SummaryRow("CPU system", "%.1f s".format(cpuSystem / 1000.0))
        SummaryRow(
            "Foreground",
            "%.1f s".format((app.foreground?.total_time_msec ?: 0f) / 1000.0),
        )
        SummaryRow("Wakeups", (app.apk?.wakeups ?: 0f).toInt().toString())
        SummaryRow(
            "Mobile traffic",
            formatByteCount(
                (app.network?.mobile_bytes_rx ?: 0f) + (app.network?.mobile_bytes_tx ?: 0f),
            ),
        )
        SummaryRow(
            "Wi-Fi traffic",
            formatByteCount((app.network?.wifi_bytes_rx ?: 0f) + (app.network?.wifi_bytes_tx ?: 0f)),
        )
        SummaryRow("Camera", "%.1f s".format((app.camera?.total_time_msec ?: 0f) / 1000.0))
        SummaryRow("Audio", "%.1f s".format((app.audio?.total_time_msec ?: 0f) / 1000.0))
        SummaryRow(
            "Flashlight",
            "%.1f s".format((app.flashlight?.total_time_msec ?: 0f) / 1000.0),
        )
        Spacer(Modifier.height(12.dp))
    }
}

private fun formatByteCount(bytes: Float): String {
    if (bytes <= 0f) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes
    var unit = 0
    while (value >= 1024f && unit < units.lastIndex) {
        value /= 1024f
        unit++
    }
    return "%.1f %s".format(value, units[unit])
}

@Composable
private fun ReportsTab(
    success: CaptureState.Success,
    reportNames: List<String>,
    onOpenReport: (String) -> Unit,
    onShareReport: (String) -> Unit,
    onImport: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth().testTag("btn_import_report"),
        ) {
            Text("Import bugreport or checkin")
        }
        Card(Modifier.fillMaxWidth().testTag("saved_reports")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Saved reports", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${reportNames.size} raw captures stored on this device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (reportNames.isEmpty()) {
                    Text("No reports yet.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    reportNames.take(20).forEach { name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                name,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = { onShareReport(name) }) {
                                Text("Share", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = { onOpenReport(name) }) {
                                Text("Open", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        RawPreviewCard(success)
    }
}

@Composable
private fun SavedReportsCard(reportNames: List<String>) {
    Card(Modifier.fillMaxWidth().testTag("saved_reports")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Saved reports", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    reportNames.firstOrNull() ?: "Captures stored on this device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                reportNames.size.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AppRow(app: ProtoApp, power: AppPowerStat?, onClick: () -> Unit) {
    val cpuMs = (app.cpu?.user_time_ms ?: 0f) + (app.cpu?.system_time_ms ?: 0f)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    app.name.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "uid ${app.uid ?: 0}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    power?.let { AppPowerStats.formatPercent(it.devicePowerPct) } ?: "n/a",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "%.1f s CPU".format(cpuMs / 1000.0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RawPreviewCard(success: CaptureState.Success) {
    Card(Modifier.fillMaxWidth().testTag("raw_capture")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Raw capture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = success.preview.ifBlank { "(empty capture)" },
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                maxLines = 24,
                overflow = TextOverflow.Clip,
                modifier = Modifier.testTag("raw_preview"),
            )
        }
    }
}

@Composable
private fun StatusChip(ready: ShizukuState.Ready) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                modifier = Modifier.size(8.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
            ) {}
            Text(
                text = if (ready.isAdb) "ADB · uid 2000" else "uid ${ready.uid}",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun CaptureMetaCard(success: CaptureState.Success) {
    val snapshot = success.snapshot
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("capture_summary"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Capture validated", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${success.durationMs} ms",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SummaryRow("Checkin version", snapshot.checkinVersion?.toString() ?: "unknown")
            SummaryRow("History lines", snapshot.historyLines.toString())
            SummaryRow("History string pool", snapshot.historyStringPoolLines.toString())
            SummaryRow("Resets", snapshot.resetEvents.toString())
            SummaryRow("Total lines", snapshot.totalLines.toString())
            SummaryRow("Size", "%,d bytes".format(success.sizeBytes))
            success.savedName?.let { SummaryRow("Saved locally", it) }
        }
    }
}

@Composable
private fun HistoryStatusCard(history: HistoryResult?, failure: String?) {
    if (history == null) {
        if (failure != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Timeline parser failed", fontWeight = FontWeight.SemiBold)
                    Text(failure, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        return
    }

    Card(Modifier.fillMaxWidth().testTag("history_parser")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("History parser", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${history.entries.size} timeline events · ${history.summaries.size} discharge intervals",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (history.errors.isNotEmpty()) {
                Text(
                    "${history.errors.size} new-format history lines skipped (compatibility backlog)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun DischargeIntervalsCard(history: HistoryResult) {
    val drains = history.summaries.filter { it.initialBatteryLevel != it.finalBatteryLevel }
    if (drains.isEmpty()) return

    Card(Modifier.fillMaxWidth().testTag("discharge_intervals")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Discharge intervals",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            drains.forEach { summary ->
                val durationMinutes = ((summary.endTimeMs - summary.startTimeMs) / 60_000L).coerceAtLeast(0)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${summary.initialBatteryLevel}% → ${summary.finalBatteryLevel}%",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "${durationMinutes / 60}h ${durationMinutes % 60}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckinSummaryCard(checkin: CheckinResult?, failure: String?) {
    val stats = checkin?.stats
    if (stats == null) {
        if (failure != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("System parser failed", fontWeight = FontWeight.SemiBold)
                    Text(failure, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        return
    }

    val battery = stats.system?.battery
    Card(Modifier.fillMaxWidth().testTag("system_summary")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("System stats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Report version ${stats.report_version ?: 0} · ${stats.app.size} apps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SummaryRow(
                "Battery capacity",
                "${battery?.estimated_battery_capacity_mah ?: 0L} mAh",
            )
            SummaryRow(
                "Battery realtime",
                "%.1f h".format((battery?.battery_realtime_msec ?: 0f) / 3_600_000.0),
            )
            SummaryRow(
                "Total realtime",
                "%.1f h".format((battery?.total_realtime_msec ?: 0f) / 3_600_000.0),
            )
            if (checkin.warnings.isNotEmpty()) {
                Text(
                    "${checkin.warnings.size} checkin warnings (newer Android fields; parsing continues)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }

    val powerReport = remember(stats) { AppPowerStats.calculate(stats) }
    val topApps = powerReport.apps.take(6)
    if (topApps.isNotEmpty()) {
        Card(Modifier.fillMaxWidth().testTag("top_apps")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Top apps by power", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Capacity %.0f mAh%s".format(
                        powerReport.capacityMah,
                        if (powerReport.capacityEstimated) " (estimated)" else "",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                topApps.forEach { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            app.name,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            AppPowerStats.formatPercent(app.devicePowerPct),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
