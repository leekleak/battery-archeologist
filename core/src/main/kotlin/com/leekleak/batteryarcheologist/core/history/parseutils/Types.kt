/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Kotlin port of the core parseutils types from
 * third_party/battery-historian/parseutils/parseutils.go.
 *
 * Durations are stored in milliseconds here. Upstream uses Go's
 * time.Duration (nanoseconds); every conversion in this port is explicit so
 * that CSV values can remain in milliseconds, matching the checkin history.
 */

package com.leekleak.batteryarcheologist.core.history.parseutils

import com.leekleak.batteryarcheologist.core.history.csv.EntryState
import com.leekleak.batteryarcheologist.core.history.csv.Key
import com.leekleak.batteryarcheologist.core.model.PackageInfoModel
import com.leekleak.batteryarcheologist.core.model.TimelineEntry
import com.leekleak.batteryarcheologist.core.packageinfo.appIdFromString
import java.time.Instant
import java.time.ZoneId

/** Summary format requested from the history parser. Port of the Go format constants. */
enum class HistorySummaryFormat(val wireName: String) {
    /** Aggregate by battery-level drop (`batteryLevel`). */
    BATTERY_LEVEL("batteryLevel"),

    /** Aggregate by elapsed time (`totalTime`). */
    TOTAL_TIME("totalTime"),
    ;

    companion object {
    }
}

/**
 * A distribution summary for one battery metric.
 *
 * Port of Go `Dist`. [totalDurationMs] and [maxDurationMs] are milliseconds
 * where upstream stores `time.Duration`.
 */
data class Dist(
    var num: Int = 0,
    var totalDurationMs: Long = 0L,
    var maxDurationMs: Long = 0L,
) {
    /**
     * Adds [durationMs] to the distribution, incrementing [num] and updating
     * [maxDurationMs] when necessary.
     */
    fun addDuration(durationMs: Long) {
        num++
        totalDurationMs += durationMs
        if (durationMs > maxDurationMs) {
            maxDurationMs = durationMs
        }
    }

    /** Port of Go `Dist.csv`: `count,totalDurationMs`. */
    fun csv(): String = "$num,$totalDurationMs"
}

/**
 * The identifying service for a battery operation.
 *
 * Port of Go `ServiceUID`. The uid is kept as a string because history files
 * may contain abbreviated uids such as `u0a25`.
 *
 * This type implements [EntryState] so it can be passed straight to the CSV
 * state machine.
 */
data class ServiceUID(
    var startMs: Long = 0L,
    val service: String = "",
    val uid: String = "",
    var pkg: PackageInfoModel? = null,
) : EntryState {
    override val startTimeMs: Long get() = startMs
    override val type: String get() = "service"
    override val value: String get() = service

    /**
     * UIDs can have multiple service names, and different UIDs can use the
     * same service name, so the uid/service pair is the CSV key.
     */
    override fun key(desc: String): Key = Key(desc, "$uid:$service")

    /**
     * Resets [startMs] to [curTimeMs] when this entity was already active.
     *
     * Port of Go `ServiceUID.initStart`. The `!= 0` check is intentional: it
     * only re-bases entities that were active before a charging/discharging
     * transition, leaving never-seen entities at zero.
     */
    fun initStart(curTimeMs: Long) {
        if (startMs != 0L) {
            startMs = curTimeMs
        }
    }

    /** Adds the interval from [suid]'s start to [curTimeMs] to [summary]. */
    fun addSummaryEntry(curTimeMs: Long, suid: ServiceUID, summary: MutableMap<String, Dist>) {
        val durationMs = curTimeMs - suid.startMs
        if (durationMs > 0) {
            val dist = summary.getOrDefault(service, Dist())
            dist.addDuration(durationMs)
            summary[service] = dist
        }
    }

    /**
     * Port of Go `ServiceUID.assign`.
     *
     * @return an error message, or `null` on success.
     */
    fun assign(
        curTimeMs: Long,
        summaryActive: Boolean,
        logEvent: Boolean,
        summaryStartTimeMs: Long,
        activeMap: MutableMap<String, ServiceUID>,
        summary: MutableMap<String, Dist>,
        transition: String,
        value: String,
        desc: String,
        csvState: com.leekleak.batteryarcheologist.core.history.csv.State,
    ): String? {
        val alreadyActive = activeMap.containsKey(value)
        val appId = appIdFromString(uid)
            ?: return "error getting appID from string: '$uid' is not a valid uid"

        when (transition) {
            "" -> {
                startMs = summaryStartTimeMs
                activeMap[value] = this
            }

            "+" -> {
                if (alreadyActive) {
                    return "two positive transitions seen for \"$desc\""
                }
                // Avoid saying an event occurred before the summary start.
                // This can happen for long wakelocks, where the start is
                // computed backwards.
                val start = maxOf(curTimeMs, summaryStartTimeMs)
                startMs = start
                activeMap[value] = this
            }

            "-" -> {
                if (!alreadyActive) {
                    if (summary.getOrDefault(service, Dist()).num != 0) {
                        return "two negative transitions for \"$desc\":\"$transition\""
                    }
                    // No + transition: assume it was already active at the
                    // beginning of the summary period.
                    startMs = summaryStartTimeMs
                    activeMap[value] = this
                    if (logEvent) {
                        csvState.addEntryWithOpt(desc, this, startMs, appId.toString())
                    }
                }
                if (summaryActive && logEvent) {
                    addSummaryEntry(curTimeMs, activeMap.getValue(value), summary)
                }
                activeMap.remove(value)
            }

            else -> return "unknown transition for \"$desc\":\"$transition\""
        }
        if (logEvent) {
            // Keep the raw uid on the ServiceUID, but print only the app id so
            // the user id does not split services in the CSV.
            csvState.addEntryWithOpt(desc, this, curTimeMs, appId.toString())
        }
        return null
    }

    /** Port of Go `ServiceUID.updateSummary`. */
    fun updateSummary(
        curTimeMs: Long,
        summaryActive: Boolean,
        summaryStartTimeMs: Long,
        summary: MutableMap<String, Dist>,
    ) {
        if (startMs == 0L) {
            startMs = summaryStartTimeMs
        }
        if (summaryActive) {
            val dist = summary.getOrDefault(service, Dist())
            dist.addDuration(curTimeMs - startMs)
            summary[service] = dist
        }
        startMs = curTimeMs
    }
}

/**
 * Per-app CPU usage in a [DCPU].
 *
 * Port of Go `AppCPUUsage`; also implements [EntryState] for the
 * "Highest App CPU Usage" timeline rows.
 */
data class AppCPUUsage(
    val startMs: Long = 0L,
    val pkgName: String = "",
    val uid: String = "",
    val userTimeMs: Long = 0L,
    val systemTimeMs: Long = 0L,
) : EntryState {
    override val startTimeMs: Long get() = startMs
    override val type: String get() = "summary"

    override val value: String
        get() {
            val name = pkgName.ifEmpty { "UID $uid" }
            return "$name~${formatGoDurationMillis(userTimeMs)}~${formatGoDurationMillis(systemTimeMs)}"
        }

    override fun key(desc: String): Key = Key(desc, "$uid($startMs)")
}

/**
 * CPU statistics for the previous discharge step.
 *
 * Port of Go `DCPU`.
 */
data class DCPU(
    var batteryLevel: Int = 0,
    var startMs: Long = 0L,
    var durationMs: Long = 0L,
    var userTimeMs: Long = 0L,
    var systemTimeMs: Long = 0L,
    var cpuUtilizers: List<AppCPUUsage> = emptyList(),
) {
    /** Port of Go `DCPU.initStart` (see [ServiceUID.initStart] for the != 0 rule). */
    fun initStart(curTimeMs: Long) {
        if (startMs != 0L) {
            startMs = curTimeMs
        }
    }
}

/**
 * `/proc/stat` statistics for the previous discharge step.
 *
 * Port of Go `DPST`.
 */
data class DPST(
    var batteryLevel: Int = 0,
    var startMs: Long = 0L,
    var durationMs: Long = 0L,
    var statUserTimeMs: Long = 0L,
    var statSystemTimeMs: Long = 0L,
    var statIoWaitTimeMs: Long = 0L,
    var statIrqTimeMs: Long = 0L,
    var statSoftIrqTimeMs: Long = 0L,
    var statIdleTimeMs: Long = 0L,
) {
    /** Port of Go `DPST.initStart`. */
    fun initStart(curTimeMs: Long) {
        if (startMs != 0L) {
            startMs = curTimeMs
        }
    }
}

/** One voter for a low power state. Port of Go `Voter`. */
data class Voter(
    val name: String = "",
    var timeMs: Long = 0L,
    var count: Int = 0,
)

/**
 * One low power state (`RPM stats` / `SubsystemPowerState`).
 *
 * Port of Go `PowerState`. Parsing of the `state_1` history payload is not
 * ported yet (see the backlog at the end of `HistoryParser.kt`); the type is
 * present so summaries can carry power-state data once that parser lands.
 */
data class PowerState(
    var batteryLevel: Int = 0,
    var startMs: Long = 0L,
    var level: Int = 0,
    var name: String = "",
    var voters: List<Voter> = emptyList(),
    var timeMs: Long = 0L,
    var count: Int = 0,
) : EntryState {
    override val startTimeMs: Long get() = startMs
    override val type: String get() = "summary"
    override val value: String
        get() = "$name~${formatGoDurationMillis(timeMs)}~$count"

    override fun key(desc: String): Key = Key(desc, "$name($batteryLevel)")
}

/**
 * Battery statistics for one aggregation interval.
 *
 * Port of Go `ActivitySummary`. Map fields are mutable, like the Go maps.
 * Field names follow the Go spelling so golden JSON can be compared directly.
 */
class ActivitySummary(
    var summaryFormat: HistorySummaryFormat = HistorySummaryFormat.TOTAL_TIME,
) {
    var reason: String = ""
    var active: Boolean = true
    var startTimeMs: Long = 0L
    var endTimeMs: Long = 0L
    var initialBatteryLevel: Int = -1
    var finalBatteryLevel: Int = 0

    var pluggedInSummary: Dist = Dist()
    var screenOnSummary: Dist = Dist()
    var mobileRadioOnSummary: Dist = Dist()
    var wifiOnSummary: Dist = Dist()
    var cpuRunningSummary: Dist = Dist()

    var gpsOnSummary: Dist = Dist()
    var sensorOnSummary: Dist = Dist()
    var wifiScanSummary: Dist = Dist()
    var wifiFullLockSummary: Dist = Dist()
    var wifiRadioSummary: Dist = Dist()
    var wifiRunningSummary: Dist = Dist()
    var wifiMulticastOnSummary: Dist = Dist()

    var cellularHighTxPowerSummary: Dist = Dist()
    var screenDozeSummary: Dist = Dist()
    var usbDataLinkSummary: Dist = Dist()
    var bluetoothOnSummary: Dist = Dist()

    var audioOnSummary: Dist = Dist()
    var cameraOnSummary: Dist = Dist()
    var videoOnSummary: Dist = Dist()
    var lowPowerModeOnSummary: Dist = Dist()
    var flashlightOnSummary: Dist = Dist()
    var chargingOnSummary: Dist = Dist()

    var phoneCallSummary: Dist = Dist()
    var phoneScanSummary: Dist = Dist()
    var bleScanSummary: Dist = Dist()

    /** Total sync time without breaking down by app. */
    var totalSyncSummary: Dist = Dist()

    val dataConnectionSummary: MutableMap<String, Dist> = LinkedHashMap()
    val connectivitySummary: MutableMap<String, Dist> = LinkedHashMap()
    val foregroundProcessSummary: MutableMap<String, Dist> = LinkedHashMap()
    val activeProcessSummary: MutableMap<String, Dist> = LinkedHashMap()
    val longWakelockSummary: MutableMap<String, Dist> = LinkedHashMap()
    val topApplicationSummary: MutableMap<String, Dist> = LinkedHashMap()
    val perAppSyncSummary: MutableMap<String, Dist> = LinkedHashMap()
    val wakeupReasonSummary: MutableMap<String, Dist> = LinkedHashMap()
    val scheduledJobSummary: MutableMap<String, Dist> = LinkedHashMap()
    val tmpWhiteListSummary: MutableMap<String, Dist> = LinkedHashMap()
    val idleModeSummary: MutableMap<String, Dist> = LinkedHashMap()

    val gpsSignalQualitySummary: MutableMap<String, Dist> = LinkedHashMap()
    val nrStateSummary: MutableMap<String, Dist> = LinkedHashMap()

    val healthSummary: MutableMap<String, Dist> = LinkedHashMap()
    val plugTypeSummary: MutableMap<String, Dist> = LinkedHashMap()
    val chargingStatusSummary: MutableMap<String, Dist> = LinkedHashMap()
    val phoneStateSummary: MutableMap<String, Dist> = LinkedHashMap()
    val wakeLockSummary: MutableMap<String, Dist> = LinkedHashMap()
    val wakeLockDetailedSummary: MutableMap<String, Dist> = LinkedHashMap()
    val wifiSupplSummary: MutableMap<String, Dist> = LinkedHashMap()
    val phoneSignalStrengthSummary: MutableMap<String, Dist> = LinkedHashMap()
    val wifiSignalStrengthSummary: MutableMap<String, Dist> = LinkedHashMap()
    val userRunningSummary: MutableMap<String, Dist> = LinkedHashMap()
    val userForegroundSummary: MutableMap<String, Dist> = LinkedHashMap()

    val dpstStatsSummary: MutableList<DPST> = ArrayList()
    val dcpuStatsSummary: MutableList<DCPU> = ArrayList()
    val powerStateSummary: MutableList<PowerState> = ArrayList()

    val dpstOverallSummary: MutableMap<String, Long> = linkedMapOf(
        "usr" to 0L,
        "sys" to 0L,
        "io" to 0L,
        "irq" to 0L,
        "sirq" to 0L,
        "idle" to 0L,
    )
    val dcpuOverallSummary: MutableMap<String, Long> = LinkedHashMap()

    val powerStateOverallSummary: MutableMap<String, PowerState> = LinkedHashMap()

    val alarmSummary: MutableMap<String, Dist> = LinkedHashMap()

    var date: String = ""

    /** Elapsed time of this summary, in milliseconds. */
    val durationMs: Long get() = endTimeMs - startTimeMs

    /** Returns an independent copy of this summary. */
    fun copy(): ActivitySummary = ActivitySummary(summaryFormat).also { other ->
        other.reason = reason
        other.active = active
        other.startTimeMs = startTimeMs
        other.endTimeMs = endTimeMs
        other.initialBatteryLevel = initialBatteryLevel
        other.finalBatteryLevel = finalBatteryLevel
        other.pluggedInSummary = pluggedInSummary.copy()
        other.screenOnSummary = screenOnSummary.copy()
        other.mobileRadioOnSummary = mobileRadioOnSummary.copy()
        other.wifiOnSummary = wifiOnSummary.copy()
        other.cpuRunningSummary = cpuRunningSummary.copy()
        other.gpsOnSummary = gpsOnSummary.copy()
        other.sensorOnSummary = sensorOnSummary.copy()
        other.wifiScanSummary = wifiScanSummary.copy()
        other.wifiFullLockSummary = wifiFullLockSummary.copy()
        other.wifiRadioSummary = wifiRadioSummary.copy()
        other.wifiRunningSummary = wifiRunningSummary.copy()
        other.wifiMulticastOnSummary = wifiMulticastOnSummary.copy()
        other.cellularHighTxPowerSummary = cellularHighTxPowerSummary.copy()
        other.screenDozeSummary = screenDozeSummary.copy()
        other.usbDataLinkSummary = usbDataLinkSummary.copy()
        other.bluetoothOnSummary = bluetoothOnSummary.copy()
        other.audioOnSummary = audioOnSummary.copy()
        other.cameraOnSummary = cameraOnSummary.copy()
        other.videoOnSummary = videoOnSummary.copy()
        other.lowPowerModeOnSummary = lowPowerModeOnSummary.copy()
        other.flashlightOnSummary = flashlightOnSummary.copy()
        other.chargingOnSummary = chargingOnSummary.copy()
        other.phoneCallSummary = phoneCallSummary.copy()
        other.phoneScanSummary = phoneScanSummary.copy()
        other.bleScanSummary = bleScanSummary.copy()
        other.totalSyncSummary = totalSyncSummary.copy()
        other.dataConnectionSummary.putAll(copyMap(dataConnectionSummary))
        other.connectivitySummary.putAll(copyMap(connectivitySummary))
        other.foregroundProcessSummary.putAll(copyMap(foregroundProcessSummary))
        other.activeProcessSummary.putAll(copyMap(activeProcessSummary))
        other.longWakelockSummary.putAll(copyMap(longWakelockSummary))
        other.topApplicationSummary.putAll(copyMap(topApplicationSummary))
        other.perAppSyncSummary.putAll(copyMap(perAppSyncSummary))
        other.wakeupReasonSummary.putAll(copyMap(wakeupReasonSummary))
        other.scheduledJobSummary.putAll(copyMap(scheduledJobSummary))
        other.tmpWhiteListSummary.putAll(copyMap(tmpWhiteListSummary))
        other.idleModeSummary.putAll(copyMap(idleModeSummary))
        other.gpsSignalQualitySummary.putAll(copyMap(gpsSignalQualitySummary))
        other.nrStateSummary.putAll(copyMap(nrStateSummary))
        other.healthSummary.putAll(copyMap(healthSummary))
        other.plugTypeSummary.putAll(copyMap(plugTypeSummary))
        other.chargingStatusSummary.putAll(copyMap(chargingStatusSummary))
        other.phoneStateSummary.putAll(copyMap(phoneStateSummary))
        other.wakeLockSummary.putAll(copyMap(wakeLockSummary))
        other.wakeLockDetailedSummary.putAll(copyMap(wakeLockDetailedSummary))
        other.wifiSupplSummary.putAll(copyMap(wifiSupplSummary))
        other.phoneSignalStrengthSummary.putAll(copyMap(phoneSignalStrengthSummary))
        other.wifiSignalStrengthSummary.putAll(copyMap(wifiSignalStrengthSummary))
        other.userRunningSummary.putAll(copyMap(userRunningSummary))
        other.userForegroundSummary.putAll(copyMap(userForegroundSummary))
        other.dpstStatsSummary.addAll(dpstStatsSummary.map { it.copy() })
        other.dcpuStatsSummary.addAll(
            dcpuStatsSummary.map { it.copy(cpuUtilizers = it.cpuUtilizers.toList()) },
        )
        other.powerStateSummary.addAll(powerStateSummary.map { it.copy() })
        other.dpstOverallSummary.putAll(dpstOverallSummary)
        other.dcpuOverallSummary.putAll(dcpuOverallSummary)
        other.powerStateOverallSummary.putAll(powerStateOverallSummary)
        other.alarmSummary.putAll(copyMap(alarmSummary))
        other.date = date
    }

    private fun copyMap(source: Map<String, Dist>): Map<String, Dist> =
        source.mapValues { (_, dist) -> dist.copy() }

    companion object {
    }
}

/**
 * Result of analyzing a history log.
 *
 * Port of Go `AnalysisReport`. Errors are messages rather than `Throwable`
 * values because every upstream error originates in this package.
 *
 * @property timeToDelta unix timestamp (ms) to human readable cumulative
 *   delta, for example `+1h00m50s002ms`.
 */
data class AnalysisReport(
    val reportVersion: Int = 0,
    val summaries: List<ActivitySummary> = emptyList(),
    val timestampsAltered: Boolean = false,
    val idxMap: Map<String, ServiceUID> = emptyMap(),
    val errors: List<String> = emptyList(),
    val overflowMs: Long = 0L,
    val timeToDelta: Map<String, String> = emptyMap(),
    /** Unknown history keys and how many times each occurred. */
    val unsupportedKeys: Map<String, Int> = emptyMap(),
)

/**
 * Public result of [HistoryParser.analyze].
 *
 * @property entries typed view of the timeline CSV rows.
 * @property csv timeline CSV (`metric,type,start_time,end_time,value,opt`).
 *   Unlike upstream, this is produced for both summary formats.
 * @property levelCsv battery-level summary CSV, present when the requested
 *   format is [HistorySummaryFormat.BATTERY_LEVEL].
 * @property warnings non-fatal problems such as UID/package mapping errors.
 * @property errors per-line history parser errors, in upstream wording.
 */
data class HistoryResult(
    val entries: List<TimelineEntry>,
    val summaries: List<ActivitySummary>,
    val csv: String,
    val levelCsv: String?,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val overflowMs: Long = 0L,
    val timestampsAltered: Boolean = false,
    val reportVersion: Int = 0,
    val timeToDelta: Map<String, String> = emptyMap(),
    /** Unknown history keys and how many times each occurred. */
    val unsupportedKeys: Map<String, Int> = emptyMap(),
)

/**
 * Best-effort, non-fatal problems found while parsing a checkin history.
 * Kept as a separate list so callers can distinguish them from per-line
 * parser errors.
 */
data class MappingResult(
    val mapping: PackageUIDMapping,
    val errors: List<String> = emptyList(),
)

/** Formats a duration in the way Go's `time.Duration.String` formats millisecond values. */
internal fun formatGoDurationMillis(durationMs: Long): String {
    if (durationMs == 0L) return "0s"
    val negative = durationMs < 0
    var remaining = if (negative) -durationMs else durationMs
    val prefix = if (negative) "-" else ""

    if (remaining < 1_000L) {
        return "$prefix${remaining}ms"
    }

    val hours = remaining / 3_600_000L
    remaining %= 3_600_000L
    val minutes = remaining / 60_000L
    remaining %= 60_000L
    val seconds = remaining / 1_000L
    val millis = remaining % 1_000L

    val secondsText = if (millis == 0L) {
        seconds.toString()
    } else {
        seconds.toString() + "." + millis.toString().padStart(3, '0').trimEnd('0')
    }

    return buildString {
        append(prefix)
        if (hours > 0) {
            append(hours).append('h').append(minutes).append('m')
        } else if (minutes > 0) {
            append(minutes).append('m')
        }
        append(secondsText).append('s')
    }
}

/** Formats the date part of an epoch-millisecond timestamp like upstream `%d-%02d-%02d`. */
internal fun summaryDate(startTimeMs: Long): String =
    Instant.ofEpochMilli(startTimeMs).atZone(ZoneId.systemDefault()).toLocalDate().toString()
