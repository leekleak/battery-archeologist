package com.leekleak.batteryarcheologist.ui.state

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.leekleak.batteryarcheologist.core.model.CheckinSnapshot
import com.leekleak.batteryarcheologist.core.model.ShizukuState
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
    fun initialLoadPicksMostRecentReportWithoutCapturing() = runTest(dispatcher) {
        val repository = FakeReportRepository(
            savedReports = listOf(
                savedReport("20261023-120000-quick.txt", epochMillis = 2),
                savedReport("20261022-120000-quick.txt", epochMillis = 1),
            ),
        )
        repository.reports["20261023-120000-quick.txt"] = report(
            raw = "newest",
            savedName = "20261023-120000-quick.txt",
        )
        repository.reports["20261022-120000-quick.txt"] = report(
            raw = "older",
            savedName = "20261022-120000-quick.txt",
        )

        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        assertEquals("20261023-120000-quick.txt", viewModel.state.value.report?.savedName)
        assertEquals(0, repository.captureCalls)
        assertEquals(false, viewModel.state.value.reportLoading)
    }

    @Test
    fun captureSuccessReplacesReportAndRefreshesSavedReports() = runTest(dispatcher) {
        val repository = loadedRepository()
        val handle = SavedStateHandle()
        val viewModel = createViewModel(repository, handle)
        advanceUntilIdle()

        repository.captureResult = Result.success(
            report(raw = "fresh capture", savedName = "fresh.txt", origin = ReportOrigin.CAPTURE),
        )
        repository.savedReports = listOf(
            savedReport("fresh.txt", epochMillis = 3),
            savedReport("old.txt", epochMillis = 1),
        )

        viewModel.capture()
        advanceUntilIdle()

        assertEquals("fresh capture", viewModel.state.value.report?.raw)
        assertEquals(CaptureStatus.Idle, viewModel.state.value.captureStatus)
        assertEquals("fresh.txt", handle.get<String>("last_report_name"))
        assertEquals(listOf("fresh.txt", "old.txt"), viewModel.state.value.savedReports.map { it.name })
        assertEquals(1, repository.captureCalls)
    }

    @Test
    fun captureFailureKeepsExistingReportAndReportsFailure() = runTest(dispatcher) {
        val repository = loadedRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        val existing = viewModel.state.value.report

        repository.captureResult = Result.failure(IllegalStateException("dumpsys exited 1"))
        viewModel.capture()
        advanceUntilIdle()

        assertSame(existing, viewModel.state.value.report)
        val status = viewModel.state.value.captureStatus
        assertTrue(status is CaptureStatus.Failed)
        assertTrue((status as CaptureStatus.Failed).message.contains("dumpsys exited 1"))
        assertEquals(1, repository.captureCalls)
    }

    @Test
    fun selectTabUpdatesStateAndSavedStateHandle() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val viewModel = createViewModel(loadedRepository(), handle)

        viewModel.selectTab(ContentTab.APPS)

        assertEquals(ContentTab.APPS, viewModel.state.value.selectedTab)
        assertEquals("APPS", handle.get<String>("selected_tab"))
    }

    @Test
    fun selectedTabIsRestoredFromSavedStateHandle() = runTest(dispatcher) {
        val handle = SavedStateHandle(mapOf("selected_tab" to "SYSTEM"))

        val viewModel = createViewModel(loadedRepository(), handle)

        assertEquals(ContentTab.SYSTEM, viewModel.state.value.selectedTab)
    }

    @Test
    fun openReportSwitchesToOverviewAndPersistsTab() = runTest(dispatcher) {
        val repository = loadedRepository()
        val handle = SavedStateHandle(mapOf("selected_tab" to "APPS"))
        val viewModel = createViewModel(repository, handle)
        advanceUntilIdle()
        assertEquals(ContentTab.APPS, viewModel.state.value.selectedTab)

        viewModel.openReport("old.txt")
        advanceUntilIdle()

        assertEquals(ContentTab.OVERVIEW, viewModel.state.value.selectedTab)
        assertEquals("OVERVIEW", handle.get<String>("selected_tab"))
    }

    @Test
    fun dismissCaptureErrorResetsFailedStatus() = runTest(dispatcher) {
        val repository = loadedRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        repository.captureResult = Result.failure(IllegalStateException("capture failed"))
        viewModel.capture()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.captureStatus is CaptureStatus.Failed)

        viewModel.dismissCaptureError()

        assertEquals(CaptureStatus.Idle, viewModel.state.value.captureStatus)
    }

    @Test
    fun deleteReportClearsCurrentReportWhenNameMatches() = runTest(dispatcher) {
        val repository = loadedRepository()
        val handle = SavedStateHandle()
        val viewModel = createViewModel(repository, handle)
        advanceUntilIdle()
        assertEquals("old.txt", viewModel.state.value.report?.savedName)

        viewModel.deleteReport("old.txt")
        advanceUntilIdle()

        assertNull(viewModel.state.value.report)
        assertNull(handle.get<String>("last_report_name"))
        assertTrue(viewModel.state.value.savedReports.isEmpty())
        assertEquals(listOf("old.txt"), repository.deletedNames)
    }

    private fun loadedRepository(): FakeReportRepository =
        FakeReportRepository(
            savedReports = listOf(savedReport("old.txt", epochMillis = 1)),
        ).apply {
            reports["old.txt"] = report(raw = "old capture", savedName = "old.txt")
        }

    private fun createViewModel(
        repository: ReportRepository,
        handle: SavedStateHandle = SavedStateHandle(),
        shizukuState: StateFlow<ShizukuState> = MutableStateFlow(ShizukuState.Ready(2000)),
    ): MainViewModel = MainViewModel(
        repository = repository,
        shizukuState = shizukuState,
        shizukuInstalled = { true },
        requestShizukuPermission = {},
        openShizuku = {},
        refreshShizuku = {},
        savedStateHandle = handle,
    )

    private class FakeReportRepository(
        var savedReports: List<SavedReport> = emptyList(),
    ) : ReportRepository {
        val reports = mutableMapOf<String, ReportData>()
        val deletedNames = mutableListOf<String>()
        var captureCalls = 0
        var captureResult: Result<ReportData> =
            Result.failure(IllegalStateException("capture not configured"))
        var importResult: Result<ReportData> =
            Result.failure(IllegalStateException("import not configured"))
        var deleteSucceeds = true

        override suspend fun capture(): Result<ReportData> {
            captureCalls++
            return captureResult
        }

        override suspend fun load(name: String): Result<ReportData> =
            reports[name]?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("Could not read saved report $name"))

        override suspend fun `import`(uri: Uri): Result<ReportData> = importResult

        override suspend fun listReports(): List<SavedReport> = savedReports

        override suspend fun delete(name: String): Boolean {
            deletedNames += name
            savedReports = savedReports.filterNot { it.name == name }
            return deleteSucceeds
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

        fun savedReport(name: String, epochMillis: Long): SavedReport = SavedReport(
            name = name,
            capturedAt = Instant.ofEpochMilli(epochMillis),
            source = "quick-checkin",
            originalName = null,
            sizeBytes = 0L,
        )
    }
}
