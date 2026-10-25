package com.leekleak.batteryarcheologist.data

import com.leekleak.batteryarcheologist.core.analytics.AppPowerReport
import com.leekleak.batteryarcheologist.core.analytics.AppPowerStat
import com.leekleak.batteryarcheologist.core.checkin.checkinparse.CheckinResult
import com.leekleak.batteryarcheologist.core.history.parseutils.HistoryResult
import com.leekleak.batteryarcheologist.core.model.CheckinSnapshot
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats
import java.time.Instant

/** Where the currently displayed report came from. */
enum class ReportOrigin { CAPTURE, SAVED, IMPORT }

/**
 * A checkin app paired with its computed power estimate, if one is available.
 *
 * [power] is keyed by `uid:name`, matching the [AppPowerStat] values produced
 * by `AppPowerStats.calculate`.
 */
data class AppUsage(
    val app: BatteryStats.App,
    val power: AppPowerStat?,
)

/**
 * Everything the UI needs to render one parsed report.
 *
 * [raw] is kept so reports can be re-parsed, previewed and shared without going
 * back to disk. Parsing failures are captured in [historyFailure] and
 * [checkinFailure] instead of dropping the whole report.
 */
data class ReportData(
    val raw: String,
    val savedName: String?,
    val origin: ReportOrigin,
    val capturedAt: Instant?,
    val durationMs: Long,
    val sizeBytes: Int,
    val preview: String,
    val snapshot: CheckinSnapshot,
    val history: HistoryResult?,
    val historyFailure: String?,
    val checkin: CheckinResult?,
    val checkinFailure: String?,
    val power: AppPowerReport?,
    val apps: List<AppUsage>,
) {
    val capacityEstimated: Boolean get() = power?.capacityEstimated == true
}
