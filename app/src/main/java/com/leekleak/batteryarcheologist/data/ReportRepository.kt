package com.leekleak.batteryarcheologist.data

import android.content.Context
import android.net.Uri
import com.leekleak.batteryarcheologist.core.analytics.AppPowerReport
import com.leekleak.batteryarcheologist.core.analytics.AppPowerStats
import com.leekleak.batteryarcheologist.core.bugreport.BugreportUtils
import com.leekleak.batteryarcheologist.core.checkin.checkinparse.CheckinParser
import com.leekleak.batteryarcheologist.core.checkin.checkinparse.CheckinResult
import com.leekleak.batteryarcheologist.core.history.parseutils.HistoryParser
import com.leekleak.batteryarcheologist.core.history.parseutils.HistoryResult
import com.leekleak.batteryarcheologist.core.history.parseutils.HistorySummaryFormat
import com.leekleak.batteryarcheologist.core.model.CheckinSnapshot
import com.leekleak.batteryarcheologist.core.parse.CheckinInspector
import com.leekleak.shizuku.ShizukuGateway
import java.io.File
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Loads, captures and imports battery reports.
 *
 * All methods return [Result] and never throw: failures are reported with a
 * message the UI can show directly. Parsing runs on [Dispatchers.Default] and
 * disk or content-provider access on [Dispatchers.IO].
 */
interface ReportRepository {
    suspend fun capture(): Result<ReportData>
    suspend fun load(name: String): Result<ReportData>
    suspend fun `import`(uri: Uri): Result<ReportData>
    suspend fun listReports(): List<SavedReport>
    suspend fun delete(name: String): Boolean
    suspend fun resetBatteryStats(): Result<Unit>
    suspend fun raw(name: String): Result<String>
    fun file(name: String): File?
}

/**
 * Android implementation backed by [ReportStore] and [ShizukuGateway].
 *
 * @param store injectable for tests; the default points at the app's private
 *   reports directory.
 */
class AndroidReportRepository(
    context: Context,
    private val gateway: ShizukuGateway,
    private val store: ReportStore = ReportStore(context),
) : ReportRepository {
    private val appContext = context.applicationContext

    override suspend fun capture(): Result<ReportData> = resultOf {
        val result = gateway.captureCheckin()
        if (!result.succeeded) {
            error(
                result.stderr.ifBlank {
                    "Capture failed with exit code ${result.exitCode}"
                },
            )
        }

        val snapshot = withContext(Dispatchers.Default) {
            CheckinInspector.inspect(result.stdout)
        }
        val savedName = runCatching {
            withContext(Dispatchers.IO) {
                store.saveQuickCapture(result.stdout, snapshot).name
            }
        }.onFailure {
            // A storage failure must not discard a good capture.
            Timber.w(it, "Unable to save quick capture")
        }.getOrNull()

        buildReport(
            raw = result.stdout,
            snapshot = snapshot,
            savedName = savedName,
            origin = ReportOrigin.CAPTURE,
            capturedAt = Instant.now(),
            durationMs = result.durationMs,
        )
    }

    override suspend fun load(name: String): Result<ReportData> = resultOf {
        val raw = withContext(Dispatchers.IO) { store.read(name) }
            ?: error("Could not read saved report $name")
        val metadata = withContext(Dispatchers.IO) { store.metadata(name) }
        val snapshot = withContext(Dispatchers.Default) { CheckinInspector.inspect(raw) }

        buildReport(
            raw = raw,
            snapshot = snapshot,
            savedName = name,
            origin = ReportOrigin.SAVED,
            capturedAt = metadata?.capturedAt,
            durationMs = 0L,
        )
    }

    override suspend fun `import`(uri: Uri): Result<ReportData> = resultOf {
        val name = uri.lastPathSegment ?: "imported-report"
        val bytes = withContext(Dispatchers.IO) {
            appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }

        if (bytes == null || bytes.isEmpty()) {
            error("Could not read the selected report")
        }

        val checkinRaw = withContext(Dispatchers.Default) {
            val files = runCatching { BugreportUtils.contents(name, bytes) }
                .getOrElse { mapOf(name to bytes) }
            val texts = files.values.map { String(it, Charsets.UTF_8) }
            val bugreport = texts.firstOrNull { BugreportUtils.isBugReport(it) }
            bugreport?.let { BugreportUtils.extractBatterystatsCheckin(it) }
                ?: texts.firstOrNull { it.contains(",i,vers,") }
        }

        if (checkinRaw.isNullOrBlank()) {
            error("No Battery Historian checkin data found in this file")
        }

        val snapshot = withContext(Dispatchers.Default) { CheckinInspector.inspect(checkinRaw) }
        val savedName = runCatching {
            withContext(Dispatchers.IO) {
                store.saveImported(checkinRaw, name).name
            }
        }.onFailure {
            // Import succeeds even when the raw copy cannot be persisted.
            Timber.w(it, "Unable to save imported report")
        }.getOrNull()

        buildReport(
            raw = checkinRaw,
            snapshot = snapshot,
            savedName = savedName,
            origin = ReportOrigin.IMPORT,
            capturedAt = Instant.now(),
            durationMs = 0L,
        )
    }

    override suspend fun listReports(): List<SavedReport> = withContext(Dispatchers.IO) {
        runCatching { store.listReports() }
            .onFailure { Timber.w(it, "Unable to list saved reports") }
            .getOrDefault(emptyList())
    }

    override suspend fun delete(name: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { store.delete(name) }
            .onFailure { Timber.w(it, "Unable to delete report %s", name) }
            .getOrDefault(false)
    }

    override suspend fun resetBatteryStats(): Result<Unit> = resultOf {
        val result = gateway.resetBatteryStats()
        batteryStatsResetError(
            exitCode = result.exitCode,
            stdout = result.stdout,
            stderr = result.stderr,
        )?.let { message -> error(message) }

        Timber.i(
            "Battery stats reset: exit=%d duration=%dms",
            result.exitCode,
            result.durationMs,
        )
    }

    override suspend fun raw(name: String): Result<String> = resultOf {
        val raw = withContext(Dispatchers.IO) {
            runCatching { store.read(name) }
                .getOrElse { cause ->
                    error("Could not read saved report $name: ${cause.message ?: "unknown error"}")
                }
        }
        raw ?: error("Could not read saved report $name")
    }

    override fun file(name: String): File? = store.file(name)

    /** Parses a raw checkin and assembles the immutable UI model. */
    private suspend fun buildReport(
        raw: String,
        snapshot: CheckinSnapshot,
        savedName: String?,
        origin: ReportOrigin,
        capturedAt: Instant?,
        durationMs: Long,
    ): ReportData {
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

        val checkinResult: CheckinResult? = checkin.getOrNull()
        val power: AppPowerReport? = checkinResult?.stats?.let { stats ->
            withContext(Dispatchers.Default) {
                runCatching { AppPowerStats.calculate(stats) }
                    .onFailure { Timber.w(it, "AppPowerStats.calculate failed") }
                    .getOrNull()
            }
        }

        return withContext(Dispatchers.Default) {
            ReportData(
                raw = raw,
                savedName = savedName,
                origin = origin,
                capturedAt = capturedAt,
                durationMs = durationMs,
                sizeBytes = raw.toByteArray(Charsets.UTF_8).size,
                preview = raw.lineSequence().take(PREVIEW_LINES).joinToString("\n"),
                snapshot = snapshot,
                history = history.getOrNull(),
                historyFailure = history.exceptionOrNull()?.message,
                checkin = checkinResult,
                checkinFailure = checkin.exceptionOrNull()?.message,
                power = power,
                apps = buildAppUsage(checkinResult, power),
            )
        }
    }

    private fun buildAppUsage(
        checkin: CheckinResult?,
        power: AppPowerReport?,
    ): List<AppUsage> {
        val stats = checkin?.stats ?: return emptyList()
        val powerByKey = power?.apps?.associateBy { "${it.uid}:${it.name}" }.orEmpty()
        return stats.app
            .filter { !it.name.isNullOrBlank() }
            .map { app ->
                AppUsage(
                    app = app,
                    power = powerByKey["${app.uid}:${app.name.orEmpty()}"],
                )
            }
            .sortedWith(
                compareByDescending<AppUsage> { it.power?.devicePowerPct ?: 0f }
                    .thenByDescending {
                        (it.app.cpu?.user_time_ms ?: 0f) + (it.app.cpu?.system_time_ms ?: 0f)
                    }
                    .thenBy { it.app.name.orEmpty() },
            )
    }

    private companion object {
        const val PREVIEW_LINES = 40
    }
}

/**
 * Runs [block] and converts any failure into [Result.failure].
 *
 * Cancellation is rethrown so a destroyed ViewModel does not surface a bogus
 * error to the UI.
 */
private suspend inline fun <T> resultOf(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
