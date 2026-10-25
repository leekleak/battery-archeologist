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
 * Kotlin port of `AnalyzeHistory` and its state machine from
 * third_party/battery-historian/parseutils/parseutils.go.
 *
 * This is a vertical slice covering the metrics needed for a useful timeline
 * and discharge summary. See the TODO list at the end of this file for the
 * remaining upstream metrics.
 */

package com.leekleak.batteryarcheologist.core.history.parseutils

import com.leekleak.batteryarcheologist.core.historianutils.HistorianUtils
import com.leekleak.batteryarcheologist.core.history.csv.Csv
import com.leekleak.batteryarcheologist.core.history.csv.Entry
import com.leekleak.batteryarcheologist.core.history.csv.Event
import com.leekleak.batteryarcheologist.core.history.csv.State
import com.leekleak.batteryarcheologist.core.history.csv.extractEvents
import com.leekleak.batteryarcheologist.core.history.csv.mergeEvents
import com.leekleak.batteryarcheologist.core.history.csv.parseCsvRecords
import com.leekleak.batteryarcheologist.core.model.PackageInfoModel
import com.leekleak.batteryarcheologist.core.model.TimelineEntry
import com.leekleak.batteryarcheologist.core.packageinfo.appIdFromString
import java.time.ZoneOffset
import java.util.Locale

/** Battery history event names. Port of the Go constants. */
object HistoryMetrics {
    const val BATTERY_LEVEL = "Battery Level"
    const val CHARGING = "Charging on"
    const val FOREGROUND = "Foreground process"
    const val LONG_WAKELOCKS = "Long Wakelocks"
    const val PLUGGED = "Plugged"
    const val TOP = "Top app"
    const val CPU_RUNNING = Csv.CPU_RUNNING
    const val PARTIAL_WAKELOCK = "Partial wakelock"
    const val SCREEN = "Screen"
    const val WIFI_ON = "Wifi on"
    const val WIFI_RUNNING = "Wifi running"
    const val MOBILE_RADIO = "Mobile radio active"
    const val CELLULAR_HIGH_TX_POWER = "Cellular high TX power"
    const val SCREEN_DOZE = "Screen doze"
    const val USB_DATA_LINK = "USB data link"
    const val GPS_SIGNAL_QUALITY = "GPS signal quality"
    const val NR_STATE = "NR state"
    const val BLUETOOTH_ON = "Bluetooth on"
    const val STATE_EVENT = "State"
    const val DISPLAY_STATE_EVENT = "Display state changed"
}

/** Connectivity constants from `android.net.ConnectivityManager`. */
private val connectivityConstants = mapOf(
    "-1" to "TYPE_NONE",
    "0" to "TYPE_MOBILE",
    "1" to "TYPE_WIFI",
    "2" to "TYPE_MOBILE_MMS",
    "3" to "TYPE_MOBILE_SUPL",
    "4" to "TYPE_MOBILE_DUN",
    "5" to "TYPE_MOBILE_HIPRI",
    "6" to "TYPE_WIMAX",
    "7" to "TYPE_BLUETOOTH",
    "8" to "TYPE_DUMMY",
    "9" to "TYPE_ETHERNET",
    "10" to "TYPE_MOBILE_FOTA",
    "11" to "TYPE_MOBILE_IMS",
    "12" to "TYPE_MOBILE_CBS",
    "13" to "TYPE_WIFI_P2P",
    "14" to "TYPE_MOBILE_IA",
    "15" to "TYPE_MOBILE_EMERGENCY",
    "16" to "TYPE_PROXY",
    "17" to "TYPE_VPN",
)

private const val ECN_CONNECTED = "\"CONNECTED\""
private const val ECN_DISCONNECTED = "\"DISCONNECTED\""
private const val ECN_SUSPENDED = "\"SUSPENDED\""

/** Signal strength constants from `android.telephony.SignalStrength`. */
private val signalStrengthConstants = mapOf(
    "0" to "none",
    "1" to "poor",
    "2" to "moderate",
    "3" to "good",
    "4" to "great",
)

/**
 * Analyzes a raw `dumpsys batterystats -c` checkin capture.
 *
 * The capture is expected to contain `9,h,...` history lines and their
 * `9,hsp,...` string pool. Other checkin lines are used only for the
 * UID/package mapping (`...,l,apk,...`) and version detection.
 */
object HistoryParser {
    /**
     * Parses [rawCheckin] and aggregates it according to [format].
     *
     * @param rawCheckin complete checkin capture, not just the history block.
     * @param format aggregation mode for [HistoryResult.summaries].
     * @param packages package list from a bug report; may be empty for raw
     *   checkin captures, whose `apk` lines provide the mapping instead.
     */
    fun analyze(
        rawCheckin: String,
        format: HistorySummaryFormat,
        packages: List<PackageInfoModel>,
    ): HistoryResult {
        val mapping = UIDAndPackageNameMapping(rawCheckin, packages)
        val timeline = StringBuilder()
        val report = analyzeHistory(
            csvWriter = timeline,
            history = rawCheckin,
            format = format,
            pum = mapping.mapping,
            scrubPII = true,
        )
        val timelineCsv = timeline.toString()
        val levelCsv = if (format == HistorySummaryFormat.BATTERY_LEVEL) {
            buildString { BatteryLevelSummariesToCSV(this, report.summaries, true) }
        } else {
            null
        }
        return HistoryResult(
            entries = timelineEntries(timelineCsv),
            summaries = report.summaries,
            csv = timelineCsv,
            levelCsv = levelCsv,
            warnings = mapping.errors,
            errors = report.errors,
            overflowMs = report.overflowMs,
            timestampsAltered = report.timestampsAltered,
            reportVersion = report.reportVersion,
            timeToDelta = report.timeToDelta,
            unsupportedKeys = report.unsupportedKeys,
        )
    }
}

/**
 * Parses a full history capture and returns its report.
 *
 * This is the Kotlin equivalent of Go's `AnalyzeHistory(csvWriter, history,
 * format, pum, scrubPII)`. Unlike upstream it always writes the timeline CSV;
 * the caller decides whether it also wants level summaries.
 */
internal fun analyzeHistory(
    csvWriter: Appendable,
    history: String,
    format: HistorySummaryFormat,
    pum: PackageUIDMapping,
    scrubPII: Boolean,
): AnalysisReport {
    val fixed = fixTimeline(history)
    val errors = ArrayList<String>()
    fixed.error?.let { errors.add(it) }
    val lines = fixed.lines

    val csvState = State(csvWriter, printHeader = true)
    val deltaMapping = DeltaMapping()
    val analyzer = HistoryAnalyzer(csvState, pum, deltaMapping, format)

    var reportVersion = 0
    var overflowIndex = -1
    var overflowMs = 0L

    for ((index, line) in lines.withIndex()) {
        if (HistoryRegex.overflow.containsMatchIn(line)) {
            overflowIndex = index
            // Multiple overflow events can occur, but only the first one is
            // worth plotting. Stop summarizing as soon as it is seen.
            overflowMs = analyzer.state.currentTimeMs
            break
        }
        val versionMatch = HistoryRegex.versionLine.find(line)
        if (versionMatch != null) {
            reportVersion = versionMatch.groupValues[1].toIntOrNull() ?: continue
            continue
        }
        val error = analyzer.analyzeLine(line, scrubPII)
        if (error != null) {
            errors.add(error)
        }
    }

    if (overflowIndex >= 0) {
        // Battery level events are still reported after overflow.
        val levelEvents = extractLevel(lines.subList(overflowIndex + 1, lines.size), overflowMs, deltaMapping)
        if (levelEvents.isNotEmpty() && csvState.hasEvent(HistoryMetrics.BATTERY_LEVEL, "")) {
            // End the active battery level at the start of the first level
            // event after overflow.
            csvState.endEvent(HistoryMetrics.BATTERY_LEVEL, "", levelEvents.first().startMs)
        }
        for (event in levelEvents) {
            csvState.printEvent(HistoryMetrics.BATTERY_LEVEL, event)
        }
    }

    csvState.printAllReset(analyzer.state.currentTimeMs)
    csvState.printRebootEvent(analyzer.state.currentTimeMs)
    if (analyzer.summary.active) {
        analyzer.summarize(reset = true, reason = "END")
    }

    return AnalysisReport(
        reportVersion = reportVersion,
        summaries = analyzer.summaries.toList(),
        timestampsAltered = fixed.timestampsAltered,
        idxMap = analyzer.idxMap.toMap(),
        errors = errors,
        overflowMs = overflowMs,
        timeToDelta = deltaMapping.timeToDelta.toMap(),
        unsupportedKeys = analyzer.unsupportedKeys.toMap(),
    )
}

/**
 * Extracts battery level events from history lines after an overflow.
 * Port of Go `extractLevel`.
 */
private fun extractLevel(
    lines: List<String>,
    curTimeMs: Long,
    deltaMapping: DeltaMapping,
): List<Event> {
    val buffer = StringBuilder()
    val csvState = State(buffer, printHeader = false)
    val analyzer = HistoryAnalyzer(
        csvState = csvState,
        pum = PackageUIDMapping(),
        deltaMapping = deltaMapping,
        format = HistorySummaryFormat.TOTAL_TIME,
    )
    analyzer.state.currentTimeMs = curTimeMs
    for (line in lines) {
        // Most errors here are due to incomplete (non battery level) events,
        // so they are ignored, exactly like upstream.
        analyzer.analyzeLine(line, scrubPII = true)
    }
    csvState.printAllReset(analyzer.state.currentTimeMs)
    val extracted = extractEvents(buffer.toString(), listOf(HistoryMetrics.BATTERY_LEVEL))
    return extracted.events[HistoryMetrics.BATTERY_LEVEL].orEmpty()
}

/** Converts CSV rows to [TimelineEntry] values, skipping the header. */
private fun timelineEntries(csv: String): List<TimelineEntry> =
    parseCsvRecords(csv).mapNotNull { fields ->
        if (fields.size != 6) return@mapNotNull null
        if (fields[0] == "metric" && fields[1] == "type") return@mapNotNull null
        val start = fields[2].toLongOrNull() ?: return@mapNotNull null
        val end = fields[3].toLongOrNull() ?: return@mapNotNull null
        TimelineEntry(
            metric = fields[0],
            type = fields[1],
            startMs = start,
            endMs = end,
            value = fields[4],
            opt = fields[5],
        )
    }

/**
 * Mutable analyzer state for one history pass.
 *
 * Holds the equivalents of the `state`, `summary`, `summaries`, `idxMap`, and
 * `csv` locals threaded through Go's `AnalyzeHistory`.
 */
private class HistoryAnalyzer(
    private val csvState: State,
    private val pum: PackageUIDMapping,
    private val deltaMapping: DeltaMapping,
    format: HistorySummaryFormat,
) {
    var state: DeviceState = DeviceState()
    var summary: ActivitySummary = ActivitySummary(format)
    val summaries: MutableList<ActivitySummary> = ArrayList()
    val idxMap: MutableMap<String, ServiceUID> = LinkedHashMap()

    /** Unknown history keys, counted so callers can report one line per key. */
    val unsupportedKeys: MutableMap<String, Int> = LinkedHashMap()

    /** Handles one already-filtered history line. Returns an error message or null. */
    fun analyzeLine(line: String, scrubPII: Boolean): String? {
        val stringPool = HistoryRegex.genericHistoryStringPoolLine.find(line)
        if (stringPool != null) {
            val index = stringPool.groupValues[1]
            var service = stringPool.groupValues[3]
            if (scrubPII) {
                service = HistorianUtils.scrubPII(service)
            }
            val suid = ServiceUID(
                service = service,
                uid = stringPool.groupValues[2],
            )
            val error = pum.matchServiceWithPackageInfo(suid)
            idxMap[index] = suid
            return error
        }

        if (HistoryRegex.genericHistoryLine.containsMatchIn(line)) {
            val error = analyzeData(line)
            // Record the cumulative delta for the human-readable timeline.
            val deltaText = HistoryRegex.genericHistoryLine.find(line)?.groupValues?.get(1)
            val delta = deltaText?.toLongOrNull()
            if (delta != null) {
                deltaMapping.addMapping(state.currentTimeMs, delta)
            }
            return error
        }

        if (HistoryRegex.nextLine.containsMatchIn(line)) {
            idxMap.clear()
            return null
        }
        if (HistoryRegex.oldHistoryLine.containsMatchIn(line)) {
            // Ignore old history versions.
            return null
        }
        if (!HistoryRegex.versionLine.containsMatchIn(line)) {
            return "unknown line format: $line"
        }
        return null
    }

    /** Port of Go `analyzeData`. */
    private fun analyzeData(line: String): String? {
        HistoryRegex.reset.find(line)?.let { match ->
            val timestamp = match.groupValues[2].toLongOrNull()
                ?: return "count not extract TIME in line:$line"
            csvState.printAllReset(state.currentTimeMs)
            // Reset state and summary; the history string pool stays valid.
            if (summary.startTimeMs > 0) {
                summarize(reset = true, reason = "RESET")
            }
            summary.startTimeMs = timestamp
            summary.endTimeMs = timestamp
            state.currentTimeMs = timestamp
            return null
        }

        HistoryRegex.shutdown.find(line)?.let { match ->
            // START follows immediately, so saving the state here is redundant.
            val delta = match.groupValues[1].toLongOrNull()
                ?: return "int parsing error for timestamp in line:$line"
            state.currentTimeMs += delta
            summary.endTimeMs = state.currentTimeMs
            csvState.addRebootEvent(state.currentTimeMs)
            return null
        }

        if (HistoryRegex.start.containsMatchIn(line)) {
            csvState.printAllReset(state.currentTimeMs)
            // If no SHUTDOWN was reported, create the reboot entry here.
            if (!csvState.hasRebootEvent()) {
                csvState.addRebootEvent(state.currentTimeMs)
            }
            summarize(reset = true, reason = "START")
            return null
        }

        HistoryRegex.time.find(line)?.let { match ->
            val timestamp = match.groupValues[2].toLongOrNull()
                ?: return "int parsing error for TIME in line:$line"
            // Do not reset the summary start when parsing the next periodic
            // report. This cutoff filters out timestamps that start at epoch.
            val cutTime = CUT_TIME_MS
            if (summary.startTimeMs < cutTime && timestamp > cutTime) {
                summary.startTimeMs = timestamp
                summary.endTimeMs = timestamp
            }
            state.currentTimeMs = timestamp
            // A SHUTDOWN is followed by START and then TIME; print the reboot
            // event here so its end time is the next start time.
            csvState.printRebootEvent(state.currentTimeMs)
            return null
        }

        if (HistoryRegex.overflow.containsMatchIn(line)) {
            // History is no longer useful.
            return null
        }

        val parts = line.split(',')
        if (parts.size < 3) {
            return "unknown format: $line"
        }
        val timeDeltaText = parts[2]
        val timeDelta = timeDeltaText.toLongOrNull()
            ?: return "int parsing error for timestamp in line:$line"
        state.currentTimeMs += timeDelta
        summary.endTimeMs = state.currentTimeMs

        if (parts.size >= 4) {
            var success = true
            val errorBuffer = StringBuilder()
            for (part in parts.subList(3, parts.size)) {
                val match = HistoryRegex.data.find(part) ?: continue
                var value = match.groups["value"]?.value.orEmpty()
                val key = match.groupValues[2]
                if (key == "state_1") {
                    // DataRE does not capture the rest of the output because it
                    // does not expect spaces.
                    value = part
                }
                val error = updateState(match.groupValues[1], key, value)
                if (error != null) {
                    success = false
                    errorBuffer.append("** Error in ").append(line)
                        .append(" with ").append(part).append(" : ")
                        .append(error).append('\n')
                }
            }
            if (success) {
                return null
            }
            return errorBuffer.toString().trim()
        }
        return if (parts.size == 3) null else "unknown format: $line"
    }

    /**
     * Port of Go `updateState`. Returns an error message or null.
     *
     * The unused `idx` argument from upstream has been dropped.
     */
    private fun updateState(transition: String, key: String, value: String): String? {
        when (key) {
            // Battery status: charging / discharging state machine.
            "Bs" -> {
                val previous = state.chargingStatus.copy()
                val active = summary.active
                state.chargingStatus.assign(
                    state.currentTimeMs,
                    summary.active,
                    summary.startTimeMs,
                    summary.chargingStatusSummary,
                    value,
                    "Charging status",
                    csvState,
                )?.let { return it }
                when (value) {
                    "?" -> {} // unknown
                    "c" -> {
                        if (active && previous != state.chargingStatus) {
                            summarize(reset = false, reason = "CHARGING")
                            summary.active = false
                        }
                    }

                    "n", "d" -> {
                        if (!active) {
                            state.initStartTimeForAllStates()
                            summary.startTimeMs = state.currentTimeMs
                            summary.endTimeMs = state.currentTimeMs
                            summary.active = true
                        }
                    }

                    "f" -> {} // full
                    else -> return "unknown status = \"$value\""
                }
                return null
            }

            "Bh" -> {
                when (value) {
                    "?", "g", "h", "d", "v", "f", "c" -> {}
                    else -> return "unknown health = \"$value\""
                }
                return state.health.assign(
                    state.currentTimeMs,
                    summary.active,
                    summary.startTimeMs,
                    summary.healthSummary,
                    value,
                    "Health",
                    csvState,
                )
            }

            "Bp" -> {
                when (value) {
                    "n", "a", "u", "w" -> {}
                    else -> return "unknown plug type = \"$value\""
                }
                return state.plugType.assign(
                    state.currentTimeMs,
                    summary.active,
                    summary.startTimeMs,
                    summary.plugTypeSummary,
                    value,
                    "Plug",
                    csvState,
                )
            }

            "Bt" -> return state.temperature.assign(
                state.currentTimeMs,
                value,
                summary.active,
                "Temperature",
                csvState,
            )

            "Bv" -> return state.voltage.assign(
                state.currentTimeMs,
                value,
                summary.active,
                "Voltage",
                csvState,
            )

            "Bl" -> {
                val previous = state.batteryLevel.copy()
                val parsedLevel = value.toIntOrNull()
                    ?: return "parsing int error for level"
                state.lastBatteryLevel = previous
                state.batteryLevel.assign(
                    state.currentTimeMs,
                    value,
                    summary.active,
                    HistoryMetrics.BATTERY_LEVEL,
                    csvState,
                )?.let { return it }
                summary.finalBatteryLevel = parsedLevel

                if (!summary.active || summary.initialBatteryLevel == -1) {
                    summary.initialBatteryLevel = parsedLevel
                } else if (summary.summaryFormat == HistorySummaryFormat.BATTERY_LEVEL &&
                    previous != state.batteryLevel
                ) {
                    summarize(reset = false, reason = "LEVEL")
                }
                return null
            }

            "BP" -> return state.plugged.assign(
                state.currentTimeMs,
                summary.active,
                summary.startTimeMs,
                summary.pluggedInSummary,
                transition,
                HistoryMetrics.PLUGGED,
                csvState,
            )

            "Bcc" -> return state.coulombCharge.assign(
                state.currentTimeMs,
                value,
                summary.active,
                "Coulomb charge",
                csvState,
            )

            // CPU running. Wakeup reasons arrive asynchronously and are handled
            // together with `wr`.
            "r" -> {
                when (transition) {
                    "+" -> {
                        if (state.cpuRunning.isOn) {
                            return "consecutive +r events"
                        }
                        state.cpuRunning.startMs = state.currentTimeMs
                        state.cpuRunning.isOn = true
                        csvState.addEntry(
                            HistoryMetrics.CPU_RUNNING,
                            TsString(state.cpuRunning.startMs, ""),
                            state.currentTimeMs,
                        )
                        state.lastWakeupTimeMs = state.currentTimeMs
                    }

                    "-" -> {
                        if (!state.cpuRunning.isOn) {
                            // -r without a corresponding +r.
                            if (state.cpuRunning.startMs != 0L &&
                                state.cpuRunning.startMs != summary.startTimeMs
                            ) {
                                return "-r received without a corresponding +r"
                            }
                            state.cpuRunning.startMs = summary.startTimeMs
                            csvState.addEntry(
                                HistoryMetrics.CPU_RUNNING,
                                TsString(state.cpuRunning.startMs, ""),
                                state.cpuRunning.startMs,
                            )
                        }
                        csvState.addEntry(
                            HistoryMetrics.CPU_RUNNING,
                            TsString(state.cpuRunning.startMs, state.wakeupReason.service),
                            state.currentTimeMs,
                        )
                        if (summary.active) {
                            val durationMs = state.currentTimeMs - state.cpuRunning.startMs
                            if (durationMs > 0) {
                                summary.cpuRunningSummary.addDuration(durationMs)
                            }
                        }
                        state.lastWakeupDurationMs = state.currentTimeMs - state.lastWakeupTimeMs
                        // Reset so wakeup reasons logged after -r get a zero
                        // duration.
                        state.lastWakeupTimeMs = state.currentTimeMs
                        state.cpuRunning.startMs = state.currentTimeMs
                        state.cpuRunning.isOn = false

                        if (state.wakeupReason.service.isNotEmpty()) {
                            csvState.endWakeupReason(
                                state.wakeupReason.service,
                                state.currentTimeMs,
                            )?.let { return it }
                            if (summary.active) {
                                val dist = summary.wakeupReasonSummary
                                    .getOrDefault(state.wakeupReason.service, Dist())
                                if (state.lastWakeupDurationMs > 0) {
                                    dist.addDuration(state.lastWakeupDurationMs)
                                    summary.wakeupReasonSummary[state.wakeupReason.service] = dist
                                }
                            }
                            state.wakeupReason = state.wakeupReason.copy(service = "")
                        }
                    }

                    else -> return "unknown transition for cpu running : \"$transition\""
                }
                return null
            }

            // Wakeup reason. No transitions; the reason arrives asynchronously.
            "wr" -> {
                val serviceUid = idxMap[value]
                    ?: return "unable to find index \"$value\" in idxMap for wakelock"
                var old = state.wakeupReason.service
                state.wakeupReason = state.wakeupReason.copy(service = serviceUid.service)

                if (state.cpuRunning.isOn &&
                    state.lastWakeupTimeMs != 0L &&
                    (state.wakeLockHeld.isOn || old.isEmpty())
                ) {
                    // A wakelock is held (or no previous reason was saved), so
                    // LastWakeupTime is when the CPU started running.
                    csvState.startWakeupReason(state.wakeupReason.service, state.lastWakeupTimeMs)
                    if (state.wakeLockHeld.isOn) {
                        csvState.endWakeupReason(
                            state.wakeupReason.service,
                            state.wakeLockHeld.startMs,
                        )
                    }
                } else {
                    csvState.startWakeupReason(state.wakeupReason.service, state.currentTimeMs)
                }
                if (state.cpuRunning.isOn) {
                    state.wakeupReason.startMs = state.cpuRunning.startMs
                    var endTime = state.currentTimeMs
                    if (old.isEmpty() && state.wakeLockHeld.isOn) {
                        old = state.wakeupReason.service
                        // The reason ends when a userspace wakelock is acquired.
                        endTime = state.wakeLockHolder.startMs
                        state.wakeupReason = state.wakeupReason.copy(service = "")
                        state.lastWakeupDurationMs = endTime - state.lastWakeupTimeMs
                    }
                    if (old.isNotEmpty()) {
                        if (summary.active) {
                            val dist = summary.wakeupReasonSummary.getOrDefault(old, Dist())
                            dist.addDuration(endTime - state.lastWakeupTimeMs)
                            summary.wakeupReasonSummary[old] = dist
                        }
                        state.lastWakeupTimeMs = state.currentTimeMs
                    }
                } else {
                    // Wakeup reason received while the CPU is not running.
                    if (state.lastWakeupTimeMs == 0L) {
                        // Reason before a +r: assume the CPU is already running.
                        state.lastWakeupTimeMs = state.currentTimeMs
                        return null
                    }
                    state.wakeupReason.startMs = state.lastWakeupTimeMs
                    if (summary.active) {
                        val dist = summary.wakeupReasonSummary
                            .getOrDefault(state.wakeupReason.service, Dist())
                        dist.addDuration(state.lastWakeupTimeMs - state.currentTimeMs)
                        summary.wakeupReasonSummary[state.wakeupReason.service] = dist
                    }
                    state.lastWakeupTimeMs = state.currentTimeMs
                    if (state.cpuRunning.startMs != 0L) {
                        // A -r was seen before; avoid printing the second wr
                        // event's times.
                        csvState.endWakeupReason(state.wakeupReason.service, state.currentTimeMs)
                        state.wakeupReason = state.wakeupReason.copy(service = "")
                    }
                }
                return null
            }

            // Partial wakelock. `+w` names the first holder; `-w` may or may
            // not name the last one.
            "w" -> {
                when (transition) {
                    "", "+" -> {
                        if (state.wakeLockHeld.isOn) {
                            if (value.isEmpty()) {
                                // +w with no value: the wakelock was already
                                // held when the summary started.
                                if (state.wakeLockHolder.service.isEmpty()) {
                                    return "logic error"
                                }
                                return null
                            }
                            return "two holders of the wakelock?"
                        }
                        if (state.wakeupReason.service.isNotEmpty()) {
                            // Count the time from the wakeup until the first
                            // userspace wakelock as the wakeup reason duration.
                            csvState.endWakeupReason(
                                state.wakeupReason.service,
                                state.currentTimeMs,
                            )?.let { return it }
                            if (summary.active) {
                                val dist = summary.wakeupReasonSummary
                                    .getOrDefault(state.wakeupReason.service, Dist())
                                var durationMs = state.lastWakeupDurationMs
                                if (durationMs == 0L) {
                                    durationMs = state.currentTimeMs - state.lastWakeupTimeMs
                                }
                                dist.addDuration(durationMs)
                                summary.wakeupReasonSummary[state.wakeupReason.service] = dist
                            }
                            state.wakeupReason = state.wakeupReason.copy(service = "")
                            state.lastWakeupTimeMs = state.currentTimeMs
                        }
                        if (value.isEmpty()) {
                            // First +w: the entity was already active when the
                            // summary was taken.
                            state.wakeLockHolder = state.wakeLockHolder.copy(
                                service = "unknown-wakelock-holder",
                            )
                            if (state.currentTimeMs != summary.startTimeMs) {
                                return "got w state in the middle of the summary"
                            }
                        } else {
                            val serviceUid = idxMap[value]
                                ?: return "wakelock held by unknown service : \"$value\""
                            state.wakeLockHolder =
                                state.wakeLockHolder.copy(service = serviceUid.service)
                        }
                        state.wakeLockHolder.startMs = state.currentTimeMs
                        state.wakeLockHeld = TsBool(startMs = state.currentTimeMs, isOn = true)
                    }

                    "-" -> {
                        // Catch wakeup reasons given after the wakelock release.
                        state.lastWakeupTimeMs = state.currentTimeMs
                        if (!state.wakeLockHeld.isOn) {
                            // There was no + transition: show an instant error
                            // event at the time of the -w.
                            state.wakeLockHolder.startMs = state.currentTimeMs
                            state.wakeLockHolder = state.wakeLockHolder.copy(
                                service = "unknown-wakelock-holder",
                            )
                            addCSVInstantEvent(
                                csvState,
                                state,
                                HistoryMetrics.PARTIAL_WAKELOCK,
                                "error",
                                "\"missing corresponding +w\"",
                            )
                            return null
                        }
                        if (summary.active) {
                            val dist = summary.wakeLockSummary
                                .getOrDefault(state.wakeLockHolder.service, Dist())
                            val durationMs = state.currentTimeMs - state.wakeLockHolder.startMs
                            if (durationMs > 0) {
                                dist.addDuration(durationMs)
                                summary.wakeLockSummary[state.wakeLockHolder.service] = dist
                            }
                        }
                        state.wakeLockHeld =
                            TsBool(startMs = state.currentTimeMs, isOn = false)
                    }

                    else -> return "unknown transition for wakelock : \"$transition\""
                }
                csvState.addEntry(
                    HistoryMetrics.PARTIAL_WAKELOCK,
                    state.wakeLockHolder,
                    state.currentTimeMs,
                )
                return null
            }

            "g" -> return state.gpsOn.assign(
                state.currentTimeMs,
                summary.active,
                summary.startTimeMs,
                summary.gpsOnSummary,
                transition,
                "GPS",
                csvState,
            )

            "s" -> return state.sensorOn.assign(
                state.currentTimeMs,
                summary.active,
                summary.startTimeMs,
                summary.sensorOnSummary,
                transition,
                "Sensor",
                csvState,
            )

            // Screen wake reason: identifies the app that turned the screen on.
            "Esw" -> {
                if (state.screenOn.isOn &&
                    state.screenOn.startMs > 0 &&
                    state.screenOn.data != UNKNOWN_SCREEN_ON_REASON
                ) {
                    // TODO: decide the correct policy for multiple Esw events
                    // between one +S/-S pair.
                    return "encountered multiple Esw events between a single pair of +S/-S events"
                }
                val suid = idxMap[value]
                    ?: return "screen turned on by unknown process: \"$value\""
                if (!state.screenOn.isOn) {
                    var error: String? = null
                    if (state.screenOn.data != UNKNOWN_SCREEN_ON_REASON) {
                        error = "encountered multiple Esw events " +
                            "(${state.screenOn.data} and ${suid.service}) outside of +S/-S events"
                    }
                    state.screenOn.data = suid.service
                    return error
                }
                state.screenOn.data = suid.service
                csvState.addOptToEntry(HistoryMetrics.SCREEN, state.screenOn, suid.service)
                return null
            }

            // Screen on/off.
            "S" -> {
                val previousValue = state.screenOn.isOn
                state.screenOn.assign(
                    state.currentTimeMs,
                    summary.active,
                    summary.startTimeMs,
                    summary.screenOnSummary,
                    transition,
                    HistoryMetrics.SCREEN,
                    csvState,
                )?.let { return it }
                if (transition == "-") {
                    // Do not carry reasons into the next screen-on event.
                    state.screenOn.data = UNKNOWN_SCREEN_ON_REASON
                }
                if (previousValue == state.screenOn.isOn) {
                    return null
                }
                // Update the top app state.
                val (topApp, topAppError) = state.topApp()
                if (topAppError != null) return topAppError
                if (topApp == null) return null
                val appId = appIdFromString(topApp.uid)
                    ?: return "error getting appID from string: '${topApp.uid}' is not a valid uid"
                if (!state.screenOn.isOn && summary.active) {
                    topApp.addSummaryEntry(
                        state.currentTimeMs,
                        topApp,
                        summary.topApplicationSummary,
                    )
                }
                csvState.addEntryWithOpt(
                    HistoryMetrics.TOP,
                    topApp,
                    state.currentTimeMs,
                    appId.toString(),
                )
                topApp.startMs = state.currentTimeMs
                return null
            }

            // Screen doze, USB data link, and cellular high-TX-power state flags.
            "Sd" -> return state.screenDoze.assign(
                state.currentTimeMs,
                summary.active,
                summary.startTimeMs,
                summary.screenDozeSummary,
                transition,
                HistoryMetrics.SCREEN_DOZE,
                csvState,
            )

            "Ud" -> return state.usbDataLink.assign(
                state.currentTimeMs,
                summary.active,
                summary.startTimeMs,
                summary.usbDataLinkSummary,
                transition,
                HistoryMetrics.USB_DATA_LINK,
                csvState,
            )

            "Chtp" -> return state.cellularHighTxPower.assign(
                state.currentTimeMs,
                summary.active,
                summary.startTimeMs,
                summary.cellularHighTxPowerSummary,
                transition,
                HistoryMetrics.CELLULAR_HIGH_TX_POWER,
                csvState,
            )

            // GPS signal quality and NR state are string-valued state flags.
            "Gss" -> return state.gpsSignalQuality.assign(
                state.currentTimeMs,
                summary.active,
                summary.startTimeMs,
                summary.gpsSignalQualitySummary,
                value,
                HistoryMetrics.GPS_SIGNAL_QUALITY,
                csvState,
            )

            "nrs" -> return state.nrState.assign(
                state.currentTimeMs,
                summary.active,
                summary.startTimeMs,
                summary.nrStateSummary,
                value,
                HistoryMetrics.NR_STATE,
                csvState,
            )

            // Newer instantaneous history events. Their payload is a bitmask; the
            // timeline keeps the raw value.
            "Esc" -> {
                addCSVInstantEvent(
                    csvState,
                    state,
                    HistoryMetrics.STATE_EVENT,
                    "state",
                    value.ifEmpty { transition },
                )
                return null
            }

            "Eds" -> {
                addCSVInstantEvent(
                    csvState,
                    state,
                    HistoryMetrics.DISPLAY_STATE_EVENT,
                    "display_state",
                    value.ifEmpty { transition },
                )
                return null
            }

            // Modem/Wi-Fi rail charge counters from the initial history line.
            "Mrc", "Wrc" -> return null

            "Sb" -> return state.brightness.assign(
                state.currentTimeMs,
                value,
                summary.active,
                "Brightness",
                csvState,
            )

            "Pcl" -> return state.phoneInCall.assign(
                state.currentTimeMs,
                summary.active,
                summary.startTimeMs,
                summary.phoneCallSummary,
                transition,
                "Phone call",
                csvState,
            )

            "Pcn" -> return state.dataConnection.assign(
                state.currentTimeMs,
                summary.active,
                summary.startTimeMs,
                summary.dataConnectionSummary,
                value,
                "Mobile network type",
                csvState,
            )

            "Pr" -> return state.mobileRadioOn.assign(
                state.currentTimeMs,
                summary.active,
                summary.startTimeMs,
                summary.mobileRadioOnSummary,
                transition,
                HistoryMetrics.MOBILE_RADIO,
                csvState,
            )

            "Psc" -> return state.phoneScanning.assign(
                state.currentTimeMs,
                summary.active,
                summary.startTimeMs,
                summary.phoneScanSummary,
                transition,
                "Phone scanning",
                csvState,
            )

            "Pss" -> {
                val signalValue = signalStrengthConstants[value]
                    ?: return "unknown phone signal strength = \"$value\""
                return state.phoneSignalStrength.assign(
                    state.currentTimeMs,
                    summary.active,
                    summary.startTimeMs,
                    summary.phoneSignalStrengthSummary,
                    signalValue,
                    "Mobile signal strength",
                    csvState,
                )
            }

            "Pst" -> {
                when (value) {
                    "in", "out", "em" -> {}
                    "off" -> {
                        // Phone went off; make sure signal strength reflects it.
                        state.phoneSignalStrength.assign(
                            state.currentTimeMs,
                            summary.active,
                            summary.startTimeMs,
                            summary.phoneSignalStrengthSummary,
                            "none",
                            "Mobile signal strength",
                            csvState,
                        )
                    }

                    else -> return "unknown phone state = \"$value\""
                }
                return state.phoneState.assign(
                    state.currentTimeMs,
                    summary.active,
                    summary.startTimeMs,
                    summary.phoneStateSummary,
                    value,
                    "Phone state",
                    csvState,
                )
            }

            "bles" -> return state.bleScanning.assign(
                state.currentTimeMs,
                summary.active,
                summary.startTimeMs,
                summary.bleScanSummary,
                transition,
                "BLE scanning",
                csvState,
            )

            // Bluetooth on/off state (Android 8+).
            "b" -> return state.bluetoothOn.assign(
                state.currentTimeMs,
                summary.active,
                summary.startTimeMs,
                summary.bluetoothOnSummary,
                transition,
                HistoryMetrics.BLUETOOTH_ON,
                csvState,
            )

            // Null history event: nothing to record.
            "Enl" -> return null

            "Epr" -> {
                val serviceUid = idxMap[value]
                    ?: return "unable to find index \"$value\" in idxMap for active process"
                return serviceUid.copy().assign(
                    state.currentTimeMs,
                    summary.active,
                    true,
                    summary.startTimeMs,
                    state.activeProcessMap,
                    summary.activeProcessSummary,
                    transition,
                    value,
                    "Active process",
                    csvState,
                )
            }

            "Efg" -> {
                val serviceUid = idxMap[value]
                    ?: return "unable to find index \"$value\" in idxMap for foreground process"
                return serviceUid.copy().assign(
                    state.currentTimeMs,
                    summary.active,
                    true,
                    summary.startTimeMs,
                    state.foregroundProcessMap,
                    summary.foregroundProcessSummary,
                    transition,
                    value,
                    HistoryMetrics.FOREGROUND,
                    csvState,
                )
            }

            "Etp" -> {
                val serviceUid = idxMap[value]
                    ?: return "unable to find index \"$value\" in idxMap for top app"
                return serviceUid.copy().assign(
                    state.currentTimeMs,
                    summary.active,
                    state.screenOn.isOn,
                    summary.startTimeMs,
                    state.topApplicationMap,
                    summary.topApplicationSummary,
                    transition,
                    value,
                    HistoryMetrics.TOP,
                    csvState,
                )
            }

            "Esy" -> {
                val serviceUid = idxMap[value]
                    ?: return "unable to find index \"$value\" in idxMap sync app"
                val alreadyActive = state.appSyncingMap.containsKey(value)
                if (transition == "-") {
                    val start = if (!alreadyActive) {
                        summary.startTimeMs
                    } else {
                        state.appSyncingMap.getValue(value).startMs
                    }
                    if (summary.active) {
                        state.syncIntervals.add(Event(startMs = start, endMs = state.currentTimeMs))
                    }
                }
                return serviceUid.copy().assign(
                    state.currentTimeMs,
                    summary.active,
                    true,
                    summary.startTimeMs,
                    state.appSyncingMap,
                    summary.perAppSyncSummary,
                    transition,
                    value,
                    "SyncManager",
                    csvState,
                )
            }

            "W" -> {
                if (transition == "-") {
                    // Wifi went off; update signal strength accordingly.
                    state.wifiSignalStrength.assign(
                        state.currentTimeMs,
                        summary.active,
                        summary.startTimeMs,
                        summary.wifiSignalStrengthSummary,
                        "none",
                        "Wifi signal strength",
                        csvState,
                    )
                }
                return state.wifiOn.assign(
                    state.currentTimeMs,
                    summary.active,
                    summary.startTimeMs,
                    summary.wifiOnSummary,
                    transition,
                    HistoryMetrics.WIFI_ON,
                    csvState,
                )
            }

            "Wl" -> return state.wifiFullLock.assign(
                state.currentTimeMs,
                summary.active,
                summary.startTimeMs,
                summary.wifiFullLockSummary,
                transition,
                "Wifi full lock",
                csvState,
            )

            "Ws" -> return state.wifiScan.assign(
                state.currentTimeMs,
                summary.active,
                summary.startTimeMs,
                summary.wifiScanSummary,
                transition,
                "Wifi scan",
                csvState,
            )

            "Wm" -> return state.wifiMulticastOn.assign(
                state.currentTimeMs,
                summary.active,
                summary.startTimeMs,
                summary.wifiMulticastOnSummary,
                transition,
                "Wifi multicast",
                csvState,
            )

            // `Wr` is overloaded: with a transition it is the wifi radio.
            "Wr" -> {
                if (transition.isNotEmpty()) {
                    return state.wifiRadio.assign(
                        state.currentTimeMs,
                        summary.active,
                        summary.startTimeMs,
                        summary.wifiRadioSummary,
                        transition,
                        "Wifi radio",
                        csvState,
                    )
                }
                return null
            }

            "Ww" -> return state.wifiRunning.assign(
                state.currentTimeMs,
                summary.active,
                summary.startTimeMs,
                summary.wifiRunningSummary,
                transition,
                HistoryMetrics.WIFI_RUNNING,
                csvState,
            )

            "lp", "ps" -> return state.lowPowerModeOn.assign(
                state.currentTimeMs,
                summary.active,
                summary.startTimeMs,
                summary.lowPowerModeOnSummary,
                transition,
                "Battery Saver",
                csvState,
            )

            "a" -> return state.audioOn.assign(
                state.currentTimeMs,
                summary.active,
                summary.startTimeMs,
                summary.audioOnSummary,
                transition,
                "Audio",
                csvState,
            )

            "ca" -> return state.cameraOn.assign(
                state.currentTimeMs,
                summary.active,
                summary.startTimeMs,
                summary.cameraOnSummary,
                transition,
                "Camera",
                csvState,
            )

            "v" -> return state.videoOn.assign(
                state.currentTimeMs,
                summary.active,
                summary.startTimeMs,
                summary.videoOnSummary,
                transition,
                "Video",
                csvState,
            )

            "Ecn" -> {
                val suid = idxMap[value]
                    ?: return "unable to find index \"$value\" in idxMap for Ecn"
                val networkType = connectivityConstants[suid.uid] ?: "UNKNOWN"
                val active = state.connectivityMap
                val summaryMap = summary.connectivitySummary
                var ts: String

                fun addConnectivityEntry(service: String, at: Long) {
                    csvState.addEntry(
                        "Network connectivity",
                        ServiceUID(startMs = at, service = service, uid = suid.uid),
                        state.currentTimeMs,
                    )
                }

                when (suid.service) {
                    ECN_CONNECTED -> {
                        ts = "$networkType:$ECN_CONNECTED"
                        val suspended = "$networkType:$ECN_SUSPENDED"
                        if (active.containsKey(ts)) {
                            // The connectivity stack repeats the connected state.
                            return null
                        }
                        active[suspended]?.let { suspendedState ->
                            val dist = summaryMap.getOrDefault(suspended, Dist())
                            if (summary.active) {
                                val durationMs = state.currentTimeMs - suspendedState.startMs
                                if (durationMs > 0) {
                                    dist.addDuration(durationMs)
                                    summaryMap[suspended] = dist
                                }
                            }
                            active.remove(suspended)
                            addConnectivityEntry(suspended, suspendedState.startMs)
                        }
                        suid.startMs = state.currentTimeMs
                        active[ts] = suid
                    }

                    ECN_DISCONNECTED -> {
                        ts = "$networkType:$ECN_SUSPENDED"
                        var alreadyActive = active.containsKey(ts)
                        if (!alreadyActive) {
                            ts = "$networkType:$ECN_CONNECTED"
                            alreadyActive = active.containsKey(ts)
                        }
                        val dist = summaryMap.getOrDefault(ts, Dist())
                        if (!alreadyActive) {
                            if (dist.num != 0) return null
                            suid.startMs = summary.startTimeMs
                            active[ts] = suid
                            addConnectivityEntry(ts, suid.startMs)
                        }
                        if (summary.active) {
                            val durationMs = state.currentTimeMs - (active[ts]?.startMs ?: state.currentTimeMs)
                            if (durationMs > 0) {
                                dist.addDuration(durationMs)
                                summaryMap[ts] = dist
                            }
                        }
                        active.remove(ts)
                    }

                    ECN_SUSPENDED -> {
                        ts = "$networkType:$ECN_SUSPENDED"
                        if (active.containsKey(ts)) return null
                        val connected = "$networkType:$ECN_CONNECTED"
                        if (!active.containsKey(connected)) {
                            suid.startMs = summary.startTimeMs
                            active[connected] = suid
                            addConnectivityEntry(connected, suid.startMs)
                        }
                        val dist = summaryMap.getOrDefault(connected, Dist())
                        if (summary.active) {
                            val durationMs = state.currentTimeMs - (active[connected]?.startMs ?: state.currentTimeMs)
                            if (durationMs > 0) {
                                dist.addDuration(durationMs)
                                summaryMap[connected] = dist
                            }
                        }
                        active.remove(connected)
                        addConnectivityEntry(connected, state.currentTimeMs)
                        suid.startMs = state.currentTimeMs
                        active[ts] = suid
                    }

                    else -> return "unknown Ecn change string: ${suid.service}"
                }

                addConnectivityEntry(ts, state.currentTimeMs)
                return null
            }

            "Ewl" -> {
                val serviceUid = idxMap[value]
                    ?: return "unable to find index \"$value\" in idxMap for wakelock_in"
                return serviceUid.copy().assign(
                    state.currentTimeMs,
                    summary.active,
                    true,
                    summary.startTimeMs,
                    state.wakeLockMap,
                    summary.wakeLockDetailedSummary,
                    transition,
                    value,
                    "Wakelock_in",
                    csvState,
                )
            }

            "di" -> {
                var dozeValue = value
                if (dozeValue.isEmpty()) {
                    // M-era histories have no value; the transition implies it.
                    val opposite = when (transition) {
                        "+" -> "full"
                        "-" -> "off"
                        else -> return "unknown transition for di: \"$transition\""
                    }
                    if (state.idleMode.value.isEmpty()) {
                        state.idleMode.value = if (opposite == "off") "full" else "off"
                        state.idleMode.startMs = summary.startTimeMs
                        csvState.addEntry("Doze", state.idleMode, summary.startTimeMs)
                    }
                    dozeValue = opposite
                } else if (state.idleMode.value.isEmpty() && dozeValue == "off") {
                    // N+ histories can start in an unknown doze state.
                    state.idleMode.value = "unknown"
                    state.idleMode.startMs = summary.startTimeMs
                    csvState.addEntry("Doze", state.idleMode, summary.startTimeMs)
                }

                val error = if (dozeValue == "???") "encountered ??? doze mode" else null
                state.idleMode.assign(
                    state.currentTimeMs,
                    summary.active,
                    summary.startTimeMs,
                    summary.idleModeSummary,
                    dozeValue,
                    "Doze",
                    csvState,
                )?.let { return it }
                return error
            }

            "Ejb" -> {
                val serviceUid = idxMap[value]
                    ?: return "unable to find index \"$value\" in idxMap for job"
                return serviceUid.copy().assign(
                    state.currentTimeMs,
                    summary.active,
                    true,
                    summary.startTimeMs,
                    state.scheduledJobMap,
                    summary.scheduledJobSummary,
                    transition,
                    value,
                    "JobScheduler",
                    csvState,
                )
            }

            "Elw" -> {
                val serviceUid = idxMap[value]
                    ?: return "unable to find index \"$value\" in idxMap for longwake"
                var curTime = state.currentTimeMs
                if (transition == "+") {
                    // Include the minute the wakelock was held before it was
                    // logged in the history.
                    curTime = state.currentTimeMs - 60_000L
                }
                return serviceUid.copy().assign(
                    curTime,
                    summary.active,
                    true,
                    summary.startTimeMs,
                    state.longWakelockMap,
                    summary.longWakelockSummary,
                    transition,
                    value,
                    HistoryMetrics.LONG_WAKELOCKS,
                    csvState,
                )
            }

            "Etw" -> {
                val serviceUid = idxMap[value]
                    ?: return "unable to find index \"$value\" in idxMap for tmpwhitelist"
                return serviceUid.copy().assign(
                    state.currentTimeMs,
                    summary.active,
                    true,
                    summary.startTimeMs,
                    state.tmpWhiteListMap,
                    summary.tmpWhiteListSummary,
                    transition,
                    value,
                    "Temp White List",
                    csvState,
                )
            }

            "Wsp" -> {
                when (value) {
                    "inv", "dsc", "dis", "inact", "scan", "auth", "ascing", "asced",
                    "4-way", "group", "compl", "dorm", "uninit",
                    -> {}
                    else -> return "unknown Wifi Supplicant state = \"$value\""
                }
                return state.wifiSuppl.assign(
                    state.currentTimeMs,
                    summary.active,
                    summary.startTimeMs,
                    summary.wifiSupplSummary,
                    value,
                    "Wifi supplicant",
                    csvState,
                )
            }

            "Wss" -> {
                val signalValue = signalStrengthConstants[value]
                    ?: return "unknown wifi signal strength = \"$value\""
                return state.wifiSignalStrength.assign(
                    state.currentTimeMs,
                    summary.active,
                    summary.startTimeMs,
                    summary.wifiSignalStrengthSummary,
                    signalValue,
                    "Wifi signal strength",
                    csvState,
                )
            }

            "fl" -> return state.flashlightOn.assign(
                state.currentTimeMs,
                summary.active,
                summary.startTimeMs,
                summary.flashlightOnSummary,
                transition,
                "Flashlight on",
                csvState,
            )

            "ch" -> return state.chargingOn.assign(
                state.currentTimeMs,
                summary.active,
                summary.startTimeMs,
                summary.chargingOnSummary,
                transition,
                HistoryMetrics.CHARGING,
                csvState,
            )

            "Epi" -> return addCSVInstantAppEvent(csvState, state, idxMap, "Package install", value)

            "Epu" -> return addCSVInstantAppEvent(
                csvState,
                state,
                idxMap,
                "Package uninstall",
                value,
            )

            "Esm" -> {
                // Point event without a duration.
                addCSVInstantEvent(csvState, state, "Significant motion", "bool", "true")
                return null
            }

            "Ewa" -> return addCSVInstantAppEvent(
                csvState,
                state,
                idxMap,
                "App Processor wakeup",
                value,
            )

            "Eaa" -> return addCSVInstantAppEvent(csvState, state, idxMap, "Package active", value)

            "Eac" -> {
                addCSVInstantEvent(csvState, state, "Device active", "bool", "true")
                return null
            }

            "Eai" -> return addCSVInstantAppEvent(csvState, state, idxMap, "Package inactive", value)

            "Eal" -> {
                val serviceUid = idxMap[value]
                    ?: return "unable to find index \"$value\" in idxMap for Alarm going off (Eal)"
                return serviceUid.copy().assign(
                    state.currentTimeMs,
                    summary.active,
                    true,
                    summary.startTimeMs,
                    state.alarmMap,
                    summary.alarmSummary,
                    transition,
                    value,
                    "Alarm",
                    csvState,
                )
            }

            "Est" -> {
                // Debug-only "why we pulled external stats" event.
                if (!idxMap.containsKey(value)) {
                    return "unable to find index \"$value\" in idxMap for collect external stats event (Est)"
                }
                return null
            }

            "Dcpu" -> return updateDcpu(value)

            "Dpst" -> return updateDpst(value)

            // Low power state payload. The state_1 parser is not ported yet,
            // so the generic unknown-key error is reported below.
            // "state_1" -> TODO(parseutils)

            "null" -> return null

            // Unimplemented framework debug keys, ignored like upstream.
            "Eur", "Euf" -> return null

            else -> {
                if (state.isDpstEvent) {
                    val token = key.toIntOrNull()
                        ?: return "parsing int error for Dpst"
                    val durationMs = token.toLong()
                    when (state.dpstTokenIndex) {
                        1 -> {
                            state.dpstStats.statSystemTimeMs = durationMs
                            summary.dpstOverallSummary["sys"] =
                                summary.dpstOverallSummary.getValue("sys") + durationMs
                        }

                        2 -> {
                            state.dpstStats.statIoWaitTimeMs = durationMs
                            summary.dpstOverallSummary["io"] =
                                summary.dpstOverallSummary.getValue("io") + durationMs
                        }

                        3 -> {
                            state.dpstStats.statIrqTimeMs = durationMs
                            summary.dpstOverallSummary["irq"] =
                                summary.dpstOverallSummary.getValue("irq") + durationMs
                        }

                        4 -> {
                            state.dpstStats.statSoftIrqTimeMs = durationMs
                            summary.dpstOverallSummary["sirq"] =
                                summary.dpstOverallSummary.getValue("sirq") + durationMs
                        }

                        5 -> {
                            state.dpstStats.statIdleTimeMs = durationMs
                            summary.dpstOverallSummary["idle"] =
                                summary.dpstOverallSummary.getValue("idle") + durationMs
                        }
                    }
                    if (state.dpstTokenIndex == 5) {
                        summary.dpstStatsSummary.add(state.dpstStats.copy())
                        state.dpstStats.startMs = state.currentTimeMs
                        state.isDpstEvent = false
                        return null
                    }
                    state.dpstTokenIndex++
                    return null
                }
                // A key this parser does not model (usually a newer Android
                // release). Keep the rest of the line and report the key once
                // through unsupportedKeys instead of failing the line.
                unsupportedKeys.merge(key, 1, Int::plus)
                return null
            }
        }
    }

    /** Port of the `Dcpu` branch of `updateState`. */
    private fun updateDcpu(value: String): String? {
        var dcpu = state.dcpuStats
        dcpu.cpuUtilizers = emptyList()

        if (dcpu.startMs == 0L) {
            dcpu.startMs = summary.startTimeMs
        }
        dcpu.batteryLevel = state.lastBatteryLevel.intValue
        dcpu.durationMs = state.currentTimeMs - dcpu.startMs

        val sections = value.split('/')
        for ((index, section) in sections.withIndex()) {
            val parts = section.split(':')
            val numbers = ArrayList<Int>(parts.size)
            for (part in parts) {
                numbers.add(part.toIntOrNull() ?: return "parsing int error for Dcpu")
            }
            when (index) {
                0 -> {
                    if (numbers.size < 2) return "parsing int error for Dcpu"
                    dcpu.userTimeMs = numbers[0].toLong()
                    dcpu.systemTimeMs = numbers[1].toLong()
                }

                1, 2, 3 -> {
                    if (numbers.size < 3) return "parsing int error for Dcpu"
                    val uid = parts[0]
                    val appId = appIdFromString(uid)
                        ?: return "error getting appID from string: '$uid' is not a valid uid"
                    val app = AppCPUUsage(
                        startMs = state.lastBatteryLevel.startMs,
                        pkgName = pum.packageName(appId),
                        uid = uid,
                        userTimeMs = numbers[1].toLong(),
                        systemTimeMs = numbers[2].toLong(),
                    )
                    // Two calls are needed for the CSV line to be printed.
                    csvState.addEntryWithOpt(
                        "Highest App CPU Usage",
                        app,
                        app.startMs,
                        appId.toString(),
                    )
                    csvState.addEntryWithOpt(
                        "Highest App CPU Usage",
                        app,
                        state.currentTimeMs,
                        appId.toString(),
                    )
                    dcpu.cpuUtilizers = dcpu.cpuUtilizers + app
                    if (summary.active) {
                        summary.dcpuOverallSummary[uid] =
                            summary.dcpuOverallSummary.getOrDefault(uid, 0L) +
                            (numbers[1] + numbers[2]).toLong()
                    }
                }

                else -> return "unknown Dcpu part: \"$section\""
            }
        }
        state.dcpuStats = dcpu
        summary.dcpuStatsSummary.add(
            dcpu.copy(cpuUtilizers = dcpu.cpuUtilizers.toList()),
        )
        // A new battery step begins.
        state.dcpuStats.startMs = state.currentTimeMs
        return null
    }

    /** Port of the `Dpst` branch of `updateState`. */
    private fun updateDpst(value: String): String? {
        // The events are split by ',' so only the first token arrives here;
        // the remaining fields are handled by the default branch.
        state.isDpstEvent = true
        if (state.dpstStats.startMs == 0L) {
            state.dpstStats.startMs = summary.startTimeMs
        }
        // +1 makes BatteryLevel the starting battery level.
        state.dpstStats.batteryLevel = state.batteryLevel.intValue + 1
        state.dpstStats.durationMs = state.currentTimeMs - state.dpstStats.startMs

        val parsed = value.toIntOrNull() ?: return "parsing int error for Dpst"
        val durationMs = parsed.toLong() // token 0: statUserTime
        state.dpstStats.statUserTimeMs = durationMs
        summary.dpstOverallSummary["usr"] =
            summary.dpstOverallSummary.getValue("usr") + durationMs
        state.dpstTokenIndex = 1
        return null
    }

    /**
     * Stores the current summary if it spans a non-zero interval and starts a
     * fresh one. Port of Go `summarizeActiveState`.
     */
    fun summarize(reset: Boolean, reason: String) {
        if (summary.startTimeMs != summary.endTimeMs) {
            summary.reason = reason
            concludeActiveFromState()
            summary.totalSyncSummary = calTotalSync(state)
            summaries.add(summary.copy())
        }

        val format = summary.summaryFormat
        summary = ActivitySummary(format)
        if (!reset) {
            summary.startTimeMs = state.currentTimeMs
            summary.endTimeMs = state.currentTimeMs
            summary.initialBatteryLevel = state.batteryLevel.intValue
            summary.finalBatteryLevel = state.batteryLevel.intValue
        } else {
            state = DeviceState()
        }
        state.syncIntervals.clear()
    }

    /**
     * Folds all currently active states into [summary] up to
     * [DeviceState.currentTimeMs]. Port of Go `concludeActiveFromState`.
     */
    private fun concludeActiveFromState() {
        if (state.batteryLevel.intValue != summary.finalBatteryLevel) {
            // Logical error: finalBatteryLevel should already be current.
            state.batteryLevel.startMs = state.currentTimeMs
        }

        val now = state.currentTimeMs
        val active = summary.active
        val start = summary.startTimeMs

        state.cpuRunning.updateSummary(now, active, start, summary.cpuRunningSummary)
        state.screenOn.updateSummary(now, active, start, summary.screenOnSummary)
        state.phoneInCall.updateSummary(now, active, start, summary.phoneCallSummary)
        state.mobileRadioOn.updateSummary(now, active, start, summary.mobileRadioOnSummary)
        state.phoneScanning.updateSummary(now, active, start, summary.phoneScanSummary)
        state.bleScanning.updateSummary(now, active, start, summary.bleScanSummary)
        state.wifiOn.updateSummary(now, active, start, summary.wifiOnSummary)
        state.wifiFullLock.updateSummary(now, active, start, summary.wifiFullLockSummary)
        state.wifiRadio.updateSummary(now, active, start, summary.wifiRadioSummary)
        state.wifiRunning.updateSummary(now, active, start, summary.wifiRunningSummary)
        state.bluetoothOn.updateSummary(now, active, start, summary.bluetoothOnSummary)
        state.screenDoze.updateSummary(now, active, start, summary.screenDozeSummary)
        state.usbDataLink.updateSummary(now, active, start, summary.usbDataLinkSummary)
        state.cellularHighTxPower.updateSummary(
            now,
            active,
            start,
            summary.cellularHighTxPowerSummary,
        )
        state.gpsSignalQuality.updateSummary(
            now,
            active,
            start,
            summary.gpsSignalQualitySummary,
        )
        state.nrState.updateSummary(now, active, start, summary.nrStateSummary)
        state.plugged.updateSummary(now, active, start, summary.pluggedInSummary)
        state.gpsOn.updateSummary(now, active, start, summary.gpsOnSummary)
        state.sensorOn.updateSummary(now, active, start, summary.sensorOnSummary)
        state.wifiScan.updateSummary(now, active, start, summary.wifiScanSummary)
        state.wifiMulticastOn.updateSummary(now, active, start, summary.wifiMulticastOnSummary)
        state.idleMode.updateSummary(now, active, start, summary.idleModeSummary)
        state.audioOn.updateSummary(now, active, start, summary.audioOnSummary)
        state.cameraOn.updateSummary(now, active, start, summary.cameraOnSummary)
        state.flashlightOn.updateSummary(now, active, start, summary.flashlightOnSummary)
        state.videoOn.updateSummary(now, active, start, summary.videoOnSummary)
        state.chargingOn.updateSummary(now, active, start, summary.chargingOnSummary)

        state.phoneState.updateSummary(now, active, start, summary.phoneStateSummary)
        state.dataConnection.updateSummary(now, active, start, summary.dataConnectionSummary)
        state.plugType.updateSummary(now, active, start, summary.plugTypeSummary)
        state.chargingStatus.updateSummary(now, active, start, summary.chargingStatusSummary)
        state.health.updateSummary(now, active, start, summary.healthSummary)
        state.wifiSuppl.updateSummary(now, active, start, summary.wifiSupplSummary)
        state.phoneSignalStrength.updateSummary(
            now,
            active,
            start,
            summary.phoneSignalStrengthSummary,
        )
        state.wifiSignalStrength.updateSummary(
            now,
            active,
            start,
            summary.wifiSignalStrengthSummary,
        )

        if (state.wakeupReason.service.isNotEmpty()) {
            state.wakeupReason.updateSummary(now, active, start, summary.wakeupReasonSummary)
        }
        if (state.wakeLockHeld.isOn) {
            state.wakeLockHolder.updateSummary(now, active, start, summary.wakeLockSummary)
        }

        for (suid in state.activeProcessMap.values) {
            suid.updateSummary(now, active, start, summary.activeProcessSummary)
        }
        for (suid in state.foregroundProcessMap.values) {
            suid.updateSummary(now, active, start, summary.foregroundProcessSummary)
        }
        for (suid in state.topApplicationMap.values) {
            if (suid.startMs < now) {
                suid.updateSummary(now, active, start, summary.topApplicationSummary)
            }
        }
        for (suid in state.appSyncingMap.values) {
            val syncStart = if (suid.startMs == 0L) start else suid.startMs
            if (active) {
                state.syncIntervals.add(Event(startMs = syncStart, endMs = now))
            }
            suid.updateSummary(now, active, start, summary.perAppSyncSummary)
        }
        for (suid in state.longWakelockMap.values) {
            suid.updateSummary(now, active, start, summary.longWakelockSummary)
        }
        for (suid in state.wakeLockMap.values) {
            suid.updateSummary(now, active, start, summary.wakeLockDetailedSummary)
        }
        for (suid in state.alarmMap.values) {
            suid.updateSummary(now, active, start, summary.alarmSummary)
        }
        for ((name, suid) in state.connectivityMap) {
            if (suid.startMs == 0L) {
                suid.startMs = start
            }
            if (active) {
                val dist = summary.connectivitySummary.getOrDefault(name, Dist())
                dist.addDuration(now - suid.startMs)
                summary.connectivitySummary[name] = dist
            }
            suid.startMs = now
        }

        summary.date = summaryDate(start)

        for (suid in state.scheduledJobMap.values) {
            suid.updateSummary(now, active, start, summary.scheduledJobSummary)
        }
        for (suid in state.tmpWhiteListMap.values) {
            suid.updateSummary(now, active, start, summary.tmpWhiteListSummary)
        }
    }

    /** Port of Go `calTotalSync`: merged intervals count, total, and max. */
    private fun calTotalSync(state: DeviceState): Dist {
        val dist = Dist(num = state.syncIntervals.size)
        if (dist.num == 0) return dist
        val intervals = mergeEvents(state.syncIntervals)
        for (interval in intervals) {
            val durationMs = interval.endMs - interval.startMs
            dist.totalDurationMs += durationMs
            if (durationMs > dist.maxDurationMs) {
                dist.maxDurationMs = durationMs
            }
        }
        return dist
    }
}

/** Adds an instantaneous app event to the CSV log. Port of `addCSVInstantAppEvent`. */
private fun addCSVInstantAppEvent(
    csvState: State,
    state: DeviceState,
    idxMap: Map<String, ServiceUID>,
    eventName: String,
    value: String,
): String? {
    val suid = idxMap[value]
        ?: return "unable to find index \"$value\" in idxMap for \"$eventName\""
    var appId: Int
    var service = suid.service
    val pkg = suid.pkg
    if (pkg == null) {
        appId = appIdFromString(suid.uid)
            ?: return "error getting appID from string: '${suid.uid}' is not a valid uid"
    } else {
        appId = pkg.uid
        if (service.isEmpty() || service == "\"\"") {
            service = "\"${pkg.name}\""
        }
    }
    val event = ServiceUID(startMs = state.currentTimeMs, service = service, uid = suid.uid)
    // Two calls are needed to emit an instantaneous entry.
    csvState.addEntryWithOpt(eventName, event, state.currentTimeMs, appId.toString())
    csvState.addEntryWithOpt(eventName, event, state.currentTimeMs, appId.toString())
    return null
}

/** Adds an instantaneous non-app event. Port of `addCSVInstantEvent`. */
private fun addCSVInstantEvent(
    csvState: State,
    state: DeviceState,
    eventName: String,
    eventType: String,
    value: String,
) {
    csvState.printInstantEvent(
        Entry(
            desc = eventName,
            startMs = state.currentTimeMs,
            type = eventType,
            value = value,
        ),
    )
}

/**
 * The upstream cutoff for "timestamps that start at epoch":
 * 2005-08-17 12:00:00 UTC in epoch milliseconds.
 */
private val CUT_TIME_MS: Long =
    java.time.LocalDateTime.of(2005, 8, 17, 12, 0, 0)
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli()

// ---------------------------------------------------------------------------
// Battery-level summary CSV (port of BatteryLevelSummariesToCSV).
// ---------------------------------------------------------------------------

/** One dimension in the battery-level summary CSV. */
private class LevelSummaryDimension(
    val name: String,
    val hasNumAndDuration: Boolean,
    val value: (ActivitySummary) -> String,
)

private val levelSummaryDimensions: List<LevelSummaryDimension> = listOf(
    LevelSummaryDimension("StartTime", false) { it.startTimeMs.toString() },
    LevelSummaryDimension("EndTime", false) { it.endTimeMs.toString() },
    LevelSummaryDimension("Duration", false) { it.durationMs.toString() },
    LevelSummaryDimension("Reason", false) { it.reason },
    LevelSummaryDimension("InitialBatteryLevel", false) { it.initialBatteryLevel.toString() },
    LevelSummaryDimension("FinalBatteryLevel", false) { it.finalBatteryLevel.toString() },
    LevelSummaryDimension("LevelDropPerHour", false) { summary ->
        val durationMs = summary.durationMs
        val dropPerHour = if (durationMs == 0L) {
            Double.POSITIVE_INFINITY
        } else {
            (summary.initialBatteryLevel - summary.finalBatteryLevel) /
                (durationMs.toDouble() / 3_600_000.0)
        }
        String.format(Locale.US, "%f", dropPerHour)
    },

    // Each of the following expands into `.num` and `.dur` columns.
    LevelSummaryDimension("PluggedIn", true) { it.pluggedInSummary.csv() },
    LevelSummaryDimension("ScreenOn", true) { it.screenOnSummary.csv() },
    LevelSummaryDimension("MobileRadioOn", true) { it.mobileRadioOnSummary.csv() },
    LevelSummaryDimension("WifiOn", true) { it.wifiOnSummary.csv() },
    LevelSummaryDimension("CPURunning", true) { it.cpuRunningSummary.csv() },
    LevelSummaryDimension("GpsOn", true) { it.gpsOnSummary.csv() },
    LevelSummaryDimension("SensorOn", true) { it.sensorOnSummary.csv() },
    LevelSummaryDimension("WifiScan", true) { it.wifiScanSummary.csv() },
    LevelSummaryDimension("WifiFullLock", true) { it.wifiFullLockSummary.csv() },
    LevelSummaryDimension("WifiRadio", true) { it.wifiRadioSummary.csv() },
    LevelSummaryDimension("WifiRunning", true) { it.wifiRunningSummary.csv() },
    LevelSummaryDimension("WifiMulticastOn", true) { it.wifiMulticastOnSummary.csv() },
    LevelSummaryDimension("AudioOn", true) { it.audioOnSummary.csv() },
    LevelSummaryDimension("CameraOn", true) { it.cameraOnSummary.csv() },
    LevelSummaryDimension("VideoOn", true) { it.videoOnSummary.csv() },
    LevelSummaryDimension("LowPowerModeOn", true) { it.lowPowerModeOnSummary.csv() },
    LevelSummaryDimension("FlashlightOn", true) { it.flashlightOnSummary.csv() },
    LevelSummaryDimension("ChargingOn", true) { it.chargingOnSummary.csv() },
    LevelSummaryDimension("PhoneCall", true) { it.phoneCallSummary.csv() },
    LevelSummaryDimension("PhoneScan", true) { it.phoneScanSummary.csv() },
    LevelSummaryDimension("BLEScan", true) { it.bleScanSummary.csv() },
    LevelSummaryDimension("TotalSync", true) { it.totalSyncSummary.csv() },
)

/** Generates the CSV string for one battery-level drop. Port of `levelSummaryCSVString`. */
internal fun ActivitySummary.levelSummaryCsvString(): String? {
    if (durationMs == 0L) return null
    return levelSummaryDimensions.joinToString(",") { dimension ->
        if (dimension.hasNumAndDuration) {
            // A Dist expands to "num,dur".
            dimension.value(this)
        } else {
            dimension.value(this)
        }
    }
}

/**
 * Writes battery-level summary CSV rows for every summary where the battery
 * actually dropped.
 *
 * Port of Go `BatteryLevelSummariesToCSV`.
 *
 * @param printDimensions when true, writes the header row first.
 */
fun BatteryLevelSummariesToCSV(
    buffer: Appendable,
    summaries: List<ActivitySummary>,
    printDimensions: Boolean,
) {
    if (printDimensions) {
        val header = levelSummaryDimensions.flatMap { dimension ->
            if (dimension.hasNumAndDuration) {
                listOf("${dimension.name}.num", "${dimension.name}.dur")
            } else {
                listOf(dimension.name)
            }
        }
        buffer.append(header.joinToString(",")).append('\n')
    }
    for (summary in summaries) {
        if (summary.initialBatteryLevel > summary.finalBatteryLevel) {
            val line = summary.levelSummaryCsvString() ?: continue
            buffer.append(line).append('\n')
        }
    }
}

/*
 * TODO(parseutils) — remaining upstream metrics and behavior:
 *  - state_1 / SubsystemPowerState: low power states, voters, RPM stats,
 *    subtractPowerStates, and per-level power state summaries.
 *  - DCPU/Dpst overall summaries already populate; their pretty-print helpers
 *    and PowerState aggregation are not ported.
 *  - Epr/Efg/Etp/Esy/Ewl/Ejb/Elw/Etw/Eal, di (doze), and Ecn
 *    (connectivity) are ported; low-power/RPM power states remain.
 *  - ActivitySummary.Print / printMap / printDcpuSlice / printDpstSlice are
 *    not ported; the structured summaries and level CSV are the supported
 *    output paths.
 *  - `b` Bluetooth state and `Enl` null events deliberately remain
 *    unsupported, matching upstream's own TODOs.
 */
