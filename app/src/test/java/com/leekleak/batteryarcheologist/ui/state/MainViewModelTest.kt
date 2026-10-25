package com.leekleak.batteryarcheologist.ui.state

import android.net.Uri
import android.os.Parcel
import com.leekleak.batteryarcheologist.core.model.CheckinSnapshot
import com.leekleak.batteryarcheologist.core.model.ShizukuState
import com.leekleak.batteryarcheologist.data.PinnedAppsStore
import com.leekleak.batteryarcheologist.data.ReportData
import com.leekleak.batteryarcheologist.data.ReportOrigin
import com.leekleak.batteryarcheologist.data.ReportRepository
import com.leekleak.batteryarcheologist.data.SavedReport
import com.leekleak.batteryarcheologist.ui.navigation.ContentTab
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import sun.misc.Unsafe

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun launchListsSavedReportsWithoutLoadingOne() = runTest(dispatcher) {
        val repository = loadedRepository()

        val (viewModel, _) = start(repository)

        val state = viewModel.state.value
        assertNull(state.report)
        assertFalse(state.reportLoading)
        assertEquals(listOf("old.txt"), state.savedReports.map { it.name })
        assertEquals(0, repository.loadCalls)
        assertEquals(0, repository.captureCalls)
    }

    @Test
    fun ensureReportLoadsReportAndSelectsOverview() = runTest(dispatcher) {
        val repository = loadedRepository()
        val (viewModel, _) = start(repository)
        viewModel.selectTab(ContentTab.APPS)

        viewModel.ensureReport("old.txt")
        assertTrue(viewModel.state.value.reportLoading)
        assertNull(viewModel.state.value.report)

        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("old capture", state.report?.raw)
        assertEquals("old.txt", state.report?.savedName)
        assertEquals(ContentTab.OVERVIEW, state.selectedTab)
        assertFalse(state.reportLoading)
        assertNull(state.reportError)
        assertEquals(1, repository.loadCalls)
    }

    @Test
    fun ensureReportForAlreadyLoadedReportDoesNotLoadAgain() = runTest(dispatcher) {
        val repository = loadedRepository()
        val (viewModel, _) = start(repository)

        viewModel.ensureReport("old.txt")
        advanceUntilIdle()
        val loaded = viewModel.state.value.report
        assertEquals(1, repository.loadCalls)

        viewModel.ensureReport("old.txt")
        advanceUntilIdle()

        assertEquals(1, repository.loadCalls)
        assertSame(loaded, viewModel.state.value.report)
        assertFalse(viewModel.state.value.reportLoading)
    }

    @Test
    fun ensureReportFailureSetsErrorAndEmitsMessage() = runTest(dispatcher) {
        val repository = loadedRepository()
        val (viewModel, _) = start(repository)

        viewModel.ensureReport("missing.txt")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.report)
        assertFalse(state.reportLoading)
        val error = state.reportError
        assertNotNull(error)
        assertTrue(error.orEmpty().contains("missing.txt"))
        assertEquals(listOf(error), collectEffects(viewModel).messages())
    }

    @Test
    fun closeReportKeepsParsedReportAndResetsViewerState() = runTest(dispatcher) {
        val repository = loadedRepository()
        val (viewModel, _) = start(repository)
        viewModel.ensureReport("old.txt")
        advanceUntilIdle()
        val loaded = viewModel.state.value.report
        viewModel.selectTab(ContentTab.APPS)
        viewModel.requestRaw(null)
        assertNotNull(viewModel.state.value.rawSheet)

        viewModel.closeReport()

        val state = viewModel.state.value
        assertSame(loaded, state.report)
        assertEquals(ContentTab.OVERVIEW, state.selectedTab)
        assertFalse(state.reportLoading)
        assertNull(state.reportError)
        assertNull(state.rawSheet)
    }

    @Test
    fun requestNewReportWhenReadyCapturesAndStaysOnHome() = runTest(dispatcher) {
        val repository = loadedRepository()
        val (viewModel, _) = start(repository)
        viewModel.ensureReport("old.txt")
        advanceUntilIdle()
        assertEquals("old.txt", viewModel.state.value.report?.savedName)

        repository.captureResult = Result.success(
            report(raw = "fresh capture", savedName = "fresh.txt", origin = ReportOrigin.CAPTURE),
        )
        repository.savedReports = listOf(
            savedReport("fresh.txt", epochMillis = 3),
            savedReport("old.txt", epochMillis = 1),
        )

        viewModel.requestNewReport()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.report)
        assertEquals(CaptureStatus.Idle, state.captureStatus)
        assertEquals(listOf("fresh.txt", "old.txt"), state.savedReports.map { it.name })
        assertEquals(1, repository.captureCalls)
        assertEquals(listOf("Report captured"), collectEffects(viewModel).messages())
    }

    @Test
    fun captureWithoutSavedNameKeepsParsedReportAndWarns() = runTest(dispatcher) {
        val repository = loadedRepository()
        val (viewModel, _) = start(repository)
        viewModel.ensureReport("old.txt")
        advanceUntilIdle()

        repository.captureResult = Result.success(
            report(raw = "fresh capture", savedName = null, origin = ReportOrigin.CAPTURE),
        )

        viewModel.requestNewReport()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("fresh capture", state.report?.raw)
        assertNull(state.report?.savedName)
        assertEquals(CaptureStatus.Idle, state.captureStatus)
        assertEquals(1, repository.captureCalls)
        assertEquals(
            listOf("Report captured but could not be saved"),
            collectEffects(viewModel).messages(),
        )
    }

    @Test
    fun captureFailureSetsFailedStatusAndKeepsExistingReport() = runTest(dispatcher) {
        val repository = loadedRepository()
        val (viewModel, _) = start(repository)
        viewModel.ensureReport("old.txt")
        advanceUntilIdle()
        val existing = viewModel.state.value.report

        repository.captureResult = Result.failure(IllegalStateException("dumpsys exited 1"))
        viewModel.requestNewReport()
        advanceUntilIdle()

        assertSame(existing, viewModel.state.value.report)
        val status = viewModel.state.value.captureStatus
        assertTrue(status is CaptureStatus.Failed)
        assertTrue((status as CaptureStatus.Failed).message.contains("dumpsys exited 1"))
        assertEquals(1, repository.captureCalls)
        assertTrue(collectEffects(viewModel).messages().single().contains("dumpsys exited 1"))
    }

    @Test
    fun requestNewReportWhenShizukuNotRunningDefersCapture() = runTest(dispatcher) {
        val repository = loadedRepository()
        val (viewModel, _) = start(repository, ShizukuState.NotRunning)

        viewModel.requestNewReport()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(PrivilegedAction.CAPTURE, state.pendingAction)
        assertEquals(0, repository.captureCalls)
        assertNull(state.report)
        assertEquals(listOf(UiEffect.OpenPermission), collectEffects(viewModel))
    }

    @Test
    fun pendingCaptureRunsOnceWhenShizukuBecomesReady() = runTest(dispatcher) {
        val repository = FakeReportRepository()
        repository.captureResult = Result.success(
            report(raw = "fresh capture", savedName = "fresh.txt", origin = ReportOrigin.CAPTURE),
        )
        repository.savedReports = listOf(savedReport("fresh.txt", epochMillis = 3))
        val (viewModel, shizuku) = start(repository, ShizukuState.NotRunning)

        viewModel.requestNewReport()
        advanceUntilIdle()
        assertEquals(PrivilegedAction.CAPTURE, viewModel.state.value.pendingAction)
        assertEquals(0, repository.captureCalls)

        shizuku.value = ShizukuState.Ready(2000)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.pendingAction)
        assertEquals(ShizukuState.Ready(2000), state.shizukuState)
        assertEquals(1, repository.captureCalls)
        assertNull(state.report)
        assertEquals(listOf("fresh.txt"), state.savedReports.map { it.name })
        assertEquals(
            listOf(
                UiEffect.OpenPermission,
                UiEffect.ClosePermission,
                UiEffect.Message("Report captured"),
            ),
            collectEffects(viewModel),
        )
    }

    @Test
    fun requestResetWhenReadyResetsWithoutCapturing() = runTest(dispatcher) {
        val repository = loadedRepository()
        val (viewModel, _) = start(repository)
        viewModel.ensureReport("old.txt")
        advanceUntilIdle()

        viewModel.requestReset()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, repository.resetCalls)
        assertEquals(0, repository.captureCalls)
        assertEquals(ResetStatus.Idle, state.resetStatus)
        assertEquals("old.txt", state.report?.savedName)
        assertEquals(listOf("System battery stats reset."), collectEffects(viewModel).messages())
    }

    @Test
    fun resetFailureSetsFailedStatusAndEmitsMessage() = runTest(dispatcher) {
        val repository = loadedRepository()
        val (viewModel, _) = start(repository)

        repository.resetResult = Result.failure(IllegalStateException("Permission Denial"))
        viewModel.requestReset()
        advanceUntilIdle()

        val status = viewModel.state.value.resetStatus
        assertTrue(status is ResetStatus.Failed)
        assertTrue((status as ResetStatus.Failed).message.contains("Permission Denial"))
        assertEquals(1, repository.resetCalls)
        assertEquals(0, repository.captureCalls)
        assertEquals(
            listOf("Could not reset system battery stats: Permission Denial"),
            collectEffects(viewModel).messages(),
        )
    }

    @Test
    fun requestResetWhenNotRunningDefersResetUntilReady() = runTest(dispatcher) {
        val repository = FakeReportRepository()
        val (viewModel, shizuku) = start(repository, ShizukuState.NotRunning)

        viewModel.requestReset()
        advanceUntilIdle()

        assertEquals(PrivilegedAction.RESET, viewModel.state.value.pendingAction)
        assertEquals(0, repository.resetCalls)

        shizuku.value = ShizukuState.Ready(2000)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.pendingAction)
        assertEquals(1, repository.resetCalls)
        assertEquals(0, repository.captureCalls)
        assertEquals(ResetStatus.Idle, state.resetStatus)
        assertEquals(
            listOf(
                UiEffect.OpenPermission,
                UiEffect.ClosePermission,
                UiEffect.Message("System battery stats reset."),
            ),
            collectEffects(viewModel),
        )
    }

    @Test
    fun importReportStaysOnHomeAndRefreshesSavedReports() = runTest(dispatcher) {
        val repository = loadedRepository()
        val (viewModel, _) = start(repository)
        viewModel.ensureReport("old.txt")
        advanceUntilIdle()

        repository.importResult = Result.success(
            report(raw = "imported raw", savedName = "imported.txt", origin = ReportOrigin.IMPORT),
        )
        repository.savedReports = listOf(savedReport("imported.txt", epochMillis = 4))

        val uri = fakeUri()
        viewModel.importReport(uri)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.report)
        assertEquals(CaptureStatus.Idle, state.captureStatus)
        assertEquals(listOf("imported.txt"), state.savedReports.map { it.name })
        assertEquals(1, repository.importCalls)
        assertSame(uri, repository.importedUris.single())
        assertEquals(listOf("Report imported"), collectEffects(viewModel).messages())
    }

    @Test
    fun deleteReportClearsMatchingReportAndRefreshesList() = runTest(dispatcher) {
        val repository = loadedRepository()
        val (viewModel, _) = start(repository)
        viewModel.ensureReport("old.txt")
        advanceUntilIdle()
        assertEquals("old.txt", viewModel.state.value.report?.savedName)

        viewModel.deleteReport("old.txt")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.report)
        assertTrue(state.savedReports.isEmpty())
        assertEquals(listOf("old.txt"), repository.deletedNames)
        assertTrue(collectEffects(viewModel).messages().isEmpty())
    }

    @Test
    fun requestRawForSavedReportUsesSavedMetadata() = runTest(dispatcher) {
        val repository = FakeReportRepository(
            savedReports = listOf(
                savedReport(
                    "saved.txt",
                    epochMillis = 1,
                    sizeBytes = 1234,
                    source = "bugreport",
                ),
            ),
        )
        repository.rawByName["saved.txt"] = "raw text"
        val (viewModel, _) = start(repository)

        viewModel.requestRaw("saved.txt")
        advanceUntilIdle()

        assertEquals(listOf("saved.txt"), repository.rawCalls)
        assertEquals(
            RawSheetState(
                raw = "raw text",
                name = "saved.txt",
                sizeBytes = 1234,
                source = "bugreport",
            ),
            viewModel.state.value.rawSheet,
        )
        assertTrue(collectEffects(viewModel).messages().isEmpty())
    }

    @Test
    fun requestRawForUnsavedReportUsesInMemoryReport() = runTest(dispatcher) {
        val repository = loadedRepository()
        val (viewModel, _) = start(repository)
        viewModel.ensureReport("old.txt")
        advanceUntilIdle()
        val loaded = viewModel.state.value.report
        assertNotNull(loaded)

        viewModel.requestRaw(null)

        val sheet = viewModel.state.value.rawSheet
        assertNotNull(sheet)
        assertEquals(loaded?.raw, sheet?.raw)
        assertNull(sheet?.name)
        assertEquals(loaded?.sizeBytes?.toLong(), sheet?.sizeBytes)
        assertNull(sheet?.source)
        assertTrue(repository.rawCalls.isEmpty())
    }

    @Test
    fun requestRawFailureEmitsMessage() = runTest(dispatcher) {
        val repository = FakeReportRepository()
        val (viewModel, _) = start(repository)

        viewModel.requestRaw("missing.txt")
        advanceUntilIdle()

        assertNull(viewModel.state.value.rawSheet)
        assertEquals(listOf("missing.txt"), repository.rawCalls)
        assertEquals(
            listOf("Could not read saved report missing.txt"),
            collectEffects(viewModel).messages(),
        )
    }

    @Test
    fun selectTabUpdatesSelectedTab() = runTest(dispatcher) {
        val (viewModel, _) = start(loadedRepository())

        viewModel.selectTab(ContentTab.SYSTEM)
        assertEquals(ContentTab.SYSTEM, viewModel.state.value.selectedTab)

        viewModel.selectTab(ContentTab.APPS)
        assertEquals(ContentTab.APPS, viewModel.state.value.selectedTab)
    }

    @Test
    fun pinnedAppsAreLoadedOnStart() = runTest(dispatcher) {
        val store = FakePinnedAppsStore(setOf("com.example.app"))

        val (viewModel, _) = start(loadedRepository(), pinnedAppsStore = store)

        assertEquals(setOf("com.example.app"), viewModel.state.value.pinnedApps)
    }

    @Test
    fun togglePinnedAppAddsAndRemovesPersistedPin() = runTest(dispatcher) {
        val store = FakePinnedAppsStore()
        val (viewModel, _) = start(loadedRepository(), pinnedAppsStore = store)

        viewModel.togglePinnedApp("com.example.app")

        assertEquals(setOf("com.example.app"), viewModel.state.value.pinnedApps)
        assertEquals(setOf("com.example.app"), store.pins)

        viewModel.togglePinnedApp("com.example.app")

        assertTrue(viewModel.state.value.pinnedApps.isEmpty())
        assertTrue(store.pins.isEmpty())
        assertEquals(2, store.saveCalls)
    }

    @Test
    fun togglePinnedAppIgnoresBlankNames() = runTest(dispatcher) {
        val store = FakePinnedAppsStore()
        val (viewModel, _) = start(loadedRepository(), pinnedAppsStore = store)

        viewModel.togglePinnedApp("")

        assertTrue(viewModel.state.value.pinnedApps.isEmpty())
        assertEquals(0, store.saveCalls)
    }

    private fun loadedRepository(): FakeReportRepository =
        FakeReportRepository(
            savedReports = listOf(savedReport("old.txt", epochMillis = 1)),
        ).apply {
            reports["old.txt"] = report(raw = "old capture", savedName = "old.txt")
        }

    /** Builds a view model with a controllable Shizuku flow and drains its init work. */
    private fun TestScope.start(
        repository: ReportRepository,
        initialShizuku: ShizukuState = ShizukuState.Ready(2000),
        pinnedAppsStore: PinnedAppsStore = FakePinnedAppsStore(),
    ): Started {
        val shizuku = MutableStateFlow(initialShizuku)
        val viewModel = MainViewModel(
            repository = repository,
            shizukuState = shizuku,
            shizukuInstalled = { true },
            requestShizukuPermission = {},
            openShizuku = {},
            refreshShizuku = {},
            pinnedAppsStore = pinnedAppsStore,
        )
        advanceUntilIdle()
        return Started(viewModel, shizuku)
    }

    /**
     * Drains the one-shot effects buffered by [MainViewModel] after the actions
     * under test have run. A production collector is not needed in the test.
     */
    private fun TestScope.collectEffects(viewModel: MainViewModel): List<UiEffect> {
        val channel = viewModel.effects.produceIn(backgroundScope)
        advanceUntilIdle()
        val effects = buildList {
            while (true) {
                val result = channel.tryReceive()
                if (result.isSuccess) add(result.getOrThrow()) else break
            }
        }
        channel.cancel()
        return effects
    }

    private data class Started(
        val viewModel: MainViewModel,
        val shizuku: MutableStateFlow<ShizukuState>,
    )

    private class FakePinnedAppsStore(
        initial: Set<String> = emptySet(),
    ) : PinnedAppsStore {
        var pins: Set<String> = initial
            private set
        var saveCalls: Int = 0
            private set

        override fun load(): Set<String> = pins

        override fun save(pins: Set<String>) {
            this.pins = pins
            saveCalls++
        }
    }

    private fun List<UiEffect>.messages(): List<String> =
        filterIsInstance<UiEffect.Message>().map { it.text }

    private fun fakeUri(): Uri = allocateWithoutConstructor(FakeUri::class.java) as Uri

    private fun allocateWithoutConstructor(type: Class<*>): Any {
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
            .apply { isAccessible = true }
            .get(null) as Unsafe
        return unsafe.allocateInstance(type)
    }

    /**
     * Stand-in for an Android [Uri] passed to [MainViewModel.importReport].
     *
     * JVM unit tests only see the stubbed `android.jar`, where `Uri.parse`
     * throws and the package-private `Uri()` constructor throws. The view model
     * only forwards the instance to the repository, so an uninitialized double
     * is enough; it is allocated without invoking the stub constructor.
     */
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    private class FakeUri : Uri() {
        override fun buildUpon(): Uri.Builder? = null
        override fun getAuthority(): String? = null
        override fun getEncodedAuthority(): String? = null
        override fun getEncodedFragment(): String? = null
        override fun getEncodedPath(): String? = null
        override fun getEncodedQuery(): String? = null
        override fun getEncodedSchemeSpecificPart(): String? = null
        override fun getEncodedUserInfo(): String? = null
        override fun getFragment(): String? = null
        override fun getHost(): String? = null
        override fun getLastPathSegment(): String? = "imported.txt"
        override fun getPath(): String? = null
        override fun getPathSegments(): MutableList<String>? = null
        override fun getPort(): Int = 0
        override fun getQuery(): String? = null
        override fun getScheme(): String? = "content"
        override fun getSchemeSpecificPart(): String? = null
        override fun getUserInfo(): String? = null
        override fun isHierarchical(): Boolean = true
        override fun isRelative(): Boolean = false
        override fun toString(): String = "content://docs/imported.txt"
        override fun describeContents(): Int = 0
        override fun writeToParcel(dest: Parcel, flags: Int) = Unit
    }

    private class FakeReportRepository(
        var savedReports: List<SavedReport> = emptyList(),
    ) : ReportRepository {
        val reports = mutableMapOf<String, ReportData>()
        val deletedNames = mutableListOf<String>()
        val importedUris = mutableListOf<Uri>()
        val rawByName = mutableMapOf<String, String>()
        val rawCalls = mutableListOf<String>()
        var loadCalls = 0
        var captureCalls = 0
        var importCalls = 0
        var resetCalls = 0
        var captureResult: Result<ReportData> =
            Result.failure(IllegalStateException("capture not configured"))
        var importResult: Result<ReportData> =
            Result.failure(IllegalStateException("import not configured"))
        var resetResult: Result<Unit> = Result.success(Unit)
        var deleteSucceeds = true

        override suspend fun capture(): Result<ReportData> {
            captureCalls++
            return captureResult
        }

        override suspend fun load(name: String): Result<ReportData> {
            loadCalls++
            return reports[name]?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("Could not read saved report $name"))
        }

        override suspend fun `import`(uri: Uri): Result<ReportData> {
            importCalls++
            importedUris += uri
            return importResult
        }

        override suspend fun listReports(): List<SavedReport> = savedReports

        override suspend fun delete(name: String): Boolean {
            deletedNames += name
            savedReports = savedReports.filterNot { it.name == name }
            return deleteSucceeds
        }

        override suspend fun resetBatteryStats(): Result<Unit> {
            resetCalls++
            return resetResult
        }

        override suspend fun raw(name: String): Result<String> {
            rawCalls += name
            return rawByName[name]?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("Could not read saved report $name"))
        }

        override fun file(name: String): File? = null
    }

    private companion object {
        fun report(
            raw: String,
            savedName: String?,
            origin: ReportOrigin = ReportOrigin.SAVED,
        ): ReportData = ReportData(
            raw = raw,
            savedName = savedName,
            origin = origin,
            capturedAt = null,
            durationMs = 0L,
            sizeBytes = raw.toByteArray(Charsets.UTF_8).size,
            preview = raw.lineSequence().take(40).joinToString("\n"),
            snapshot = CheckinSnapshot(
                checkinVersion = null,
                totalLines = 0,
                historyLines = 0,
                historyStringPoolLines = 0,
                resetEvents = 0,
                hasBatteryTime = false,
                previewLines = emptyList(),
            ),
            history = null,
            historyFailure = null,
            checkin = null,
            checkinFailure = null,
            power = null,
            apps = emptyList(),
        )

        fun savedReport(
            name: String,
            epochMillis: Long,
            sizeBytes: Long = 0L,
            source: String = "quick-checkin",
        ): SavedReport = SavedReport(
            name = name,
            capturedAt = Instant.ofEpochMilli(epochMillis),
            source = source,
            originalName = null,
            sizeBytes = sizeBytes,
        )
    }
}
