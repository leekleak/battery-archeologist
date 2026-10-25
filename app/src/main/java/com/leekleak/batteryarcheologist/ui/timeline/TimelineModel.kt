package com.leekleak.batteryarcheologist.ui.timeline

import androidx.annotation.StringRes
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.core.model.TimelineEntry

/**
 * Visual grouping for the timeline rows.
 *
 * The group only selects a colour and ordering bucket. The label shown on a
 * row always comes from [TimelineMetricSpec], so unrelated metrics are never
 * presented under a shared "Other" label.
 */
enum class TimelineGroup(@StringRes val labelRes: Int) {
    BATTERY(R.string.timeline_group_battery),
    CHARGING(R.string.timeline_group_charging),
    SCREEN(R.string.timeline_group_screen),
    WIFI(R.string.timeline_group_wifi),
    RADIO(R.string.timeline_group_radio),
    GPS(R.string.timeline_group_gps),
    WAKELOCK(R.string.timeline_group_wakelocks),
    TOP_APP(R.string.timeline_group_top_app),
    CPU(R.string.timeline_group_cpu),
    APPS(R.string.timeline_group_apps),
    DEVICE(R.string.timeline_group_device),
    MEDIA(R.string.timeline_group_media),
    SYSTEM(R.string.timeline_group_system),
    OTHER(R.string.timeline_group_other),
    ;

    companion object {
        fun forMetric(metric: String): TimelineGroup = when (metric) {
            "Battery Level" -> BATTERY
            "Charging on", "Charging status", "Plugged", "Plug" -> CHARGING
            "Screen", "Screen doze" -> SCREEN
            "Wifi on", "Wifi radio", "Wifi scan", "Wifi full lock",
            "Wifi multicast", "Wifi signal strength", "Wifi supplicant",
            -> WIFI
            "Mobile radio active", "Mobile signal strength", "Mobile network type",
            "Phone state", "Phone scanning", "Cellular high TX power", "NR state",
            "Network connectivity",
            -> RADIO
            "GPS", "GPS signal quality" -> GPS
            "Wakelock_in", "Partial wakelock", "Long Wakelocks" -> WAKELOCK
            "Top app", "Foreground process", "App Processor wakeup" -> TOP_APP
            "CPU running" -> CPU
            "JobScheduler", "SyncManager", "Temp White List",
            "Package install", "Package active", "Package inactive", "Package uninstall",
            -> APPS
            "Bluetooth on", "Doze", "Device active", "USB data link",
            "Temperature", "Voltage", "Coulomb charge", "Brightness", "Health",
            -> DEVICE
            "Audio", "Video", "Camera", "Flashlight on", "Sensor", "BLE scanning",
            -> MEDIA
            else -> OTHER
        }
    }
}

/** How a metric's raw history value should be presented in the cursor readout. */
enum class TimelineValueFormat {
    BOOL,
    SIGNAL,
    CHARGING_STATUS,
    DOZE,
    PLUG,
    HEALTH,
    BRIGHTNESS,
    TEMPERATURE,
    VOLTAGE,
    COULOMB,
    CPU,
    PHONE_STATE,
    WIFI_SUPPLICANT,
    NR_STATE,
    SERVICE,
    RAW,
}

/**
 * One display row for one raw history metric.
 *
 * [metric] is the exact name from the history CSV. [label] is the full label
 * used by the readout; [chartLabel] is the shorter label drawn in the chart
 * gutter.
 */
data class TimelineMetricSpec(
    val metric: String,
    val label: String,
    val chartLabel: String,
    val group: TimelineGroup,
    val format: TimelineValueFormat,
    val order: Int,
)

private fun metric(
    metric: String,
    label: String = metric,
    chartLabel: String = label,
    group: TimelineGroup = TimelineGroup.forMetric(metric),
    format: TimelineValueFormat = TimelineValueFormat.RAW,
    order: Int = 1000,
): TimelineMetricSpec = TimelineMetricSpec(metric, label, chartLabel, group, format, order)

private val metricSpecs: Map<String, TimelineMetricSpec> = listOf(
    metric("Charging on", "Charging", group = TimelineGroup.CHARGING, format = TimelineValueFormat.BOOL, order = 100),
    metric("Charging status", "Charging status", "Charge status", TimelineGroup.CHARGING, TimelineValueFormat.CHARGING_STATUS, 110),
    metric("Plugged", "Plugged", group = TimelineGroup.CHARGING, format = TimelineValueFormat.BOOL, order = 120),
    metric("Plug", "Plug type", group = TimelineGroup.CHARGING, format = TimelineValueFormat.PLUG, order = 130),
    metric("Screen", "Screen", group = TimelineGroup.SCREEN, format = TimelineValueFormat.BOOL, order = 200),
    metric("Screen doze", "Screen doze", group = TimelineGroup.SCREEN, format = TimelineValueFormat.BOOL, order = 210),
    metric("Wifi on", "Wi-Fi", group = TimelineGroup.WIFI, format = TimelineValueFormat.BOOL, order = 300),
    metric("Wifi radio", "Wi-Fi radio", group = TimelineGroup.WIFI, format = TimelineValueFormat.BOOL, order = 310),
    metric("Wifi scan", "Wi-Fi scan", group = TimelineGroup.WIFI, format = TimelineValueFormat.BOOL, order = 320),
    metric("Wifi full lock", "Wi-Fi lock", group = TimelineGroup.WIFI, format = TimelineValueFormat.BOOL, order = 330),
    metric("Wifi multicast", "Wi-Fi multicast", group = TimelineGroup.WIFI, format = TimelineValueFormat.BOOL, order = 340),
    metric("Wifi signal strength", "Wi-Fi signal", "Wi-Fi signal", TimelineGroup.WIFI, TimelineValueFormat.SIGNAL, 350),
    metric("Wifi supplicant", "Wi-Fi state", "Wi-Fi state", TimelineGroup.WIFI, TimelineValueFormat.WIFI_SUPPLICANT, 360),
    metric("Mobile radio active", "Mobile radio", group = TimelineGroup.RADIO, format = TimelineValueFormat.BOOL, order = 400),
    metric("Mobile signal strength", "Mobile signal", "Mobile signal", TimelineGroup.RADIO, TimelineValueFormat.SIGNAL, 410),
    metric("Mobile network type", "Mobile network", group = TimelineGroup.RADIO, format = TimelineValueFormat.RAW, order = 420),
    metric("Phone state", "Phone state", group = TimelineGroup.RADIO, format = TimelineValueFormat.PHONE_STATE, order = 430),
    metric("Phone scanning", "Phone scanning", group = TimelineGroup.RADIO, format = TimelineValueFormat.BOOL, order = 440),
    metric("Cellular high TX power", "Cellular high TX", "Cell high TX", TimelineGroup.RADIO, TimelineValueFormat.BOOL, 450),
    metric("NR state", "NR state", group = TimelineGroup.RADIO, format = TimelineValueFormat.NR_STATE, order = 460),
    metric("Network connectivity", "Connectivity", group = TimelineGroup.RADIO, format = TimelineValueFormat.SERVICE, order = 470),
    metric("GPS", "GPS", group = TimelineGroup.GPS, format = TimelineValueFormat.BOOL, order = 500),
    metric("GPS signal quality", "GPS signal", group = TimelineGroup.GPS, format = TimelineValueFormat.SIGNAL, order = 510),
    metric("Bluetooth on", "Bluetooth", group = TimelineGroup.DEVICE, format = TimelineValueFormat.BOOL, order = 600),
    metric("Doze", "Doze", group = TimelineGroup.DEVICE, format = TimelineValueFormat.DOZE, order = 610),
    metric("Device active", "Device active", group = TimelineGroup.DEVICE, format = TimelineValueFormat.BOOL, order = 620),
    metric("USB data link", "USB data", "USB data", TimelineGroup.DEVICE, TimelineValueFormat.BOOL, 630),
    metric("Partial wakelock", "Wakelocks", group = TimelineGroup.WAKELOCK, format = TimelineValueFormat.SERVICE, order = 700),
    metric("Wakelock_in", "Wakelocks", group = TimelineGroup.WAKELOCK, format = TimelineValueFormat.SERVICE, order = 700),
    metric("Long Wakelocks", "Long wakelocks", "Long wakelocks", TimelineGroup.WAKELOCK, TimelineValueFormat.SERVICE, 710),
    metric("Top app", "Top app", group = TimelineGroup.TOP_APP, format = TimelineValueFormat.SERVICE, order = 800),
    metric("Foreground process", "Foreground app", "Foreground", TimelineGroup.TOP_APP, TimelineValueFormat.SERVICE, 810),
    metric("CPU running", "CPU", group = TimelineGroup.CPU, format = TimelineValueFormat.CPU, order = 900),
    metric("JobScheduler", "Jobs", group = TimelineGroup.APPS, format = TimelineValueFormat.SERVICE, order = 1000),
    metric("SyncManager", "Sync", group = TimelineGroup.APPS, format = TimelineValueFormat.SERVICE, order = 1010),
    metric("App Processor wakeup", "App wakeups", "App wakeups", TimelineGroup.APPS, TimelineValueFormat.SERVICE, 1020),
    metric("Temp White List", "Temp whitelist", "Whitelist", TimelineGroup.APPS, TimelineValueFormat.SERVICE, 1030),
    metric("Package install", "Package install", "Pkg install", TimelineGroup.APPS, TimelineValueFormat.SERVICE, 1040),
    metric("Package active", "Package active", "Pkg active", TimelineGroup.APPS, TimelineValueFormat.SERVICE, 1050),
    metric("Package inactive", "Package inactive", "Pkg inactive", TimelineGroup.APPS, TimelineValueFormat.SERVICE, 1060),
    metric("Package uninstall", "Package uninstall", "Pkg uninstall", TimelineGroup.APPS, TimelineValueFormat.SERVICE, 1070),
    metric("Temperature", "Temperature", group = TimelineGroup.DEVICE, format = TimelineValueFormat.TEMPERATURE, order = 1100),
    metric("Voltage", "Voltage", group = TimelineGroup.DEVICE, format = TimelineValueFormat.VOLTAGE, order = 1110),
    metric("Coulomb charge", "Charge counter", "Charge", TimelineGroup.DEVICE, TimelineValueFormat.COULOMB, 1120),
    metric("Brightness", "Brightness", group = TimelineGroup.DEVICE, format = TimelineValueFormat.BRIGHTNESS, order = 1130),
    metric("Health", "Battery health", "Health", TimelineGroup.DEVICE, TimelineValueFormat.HEALTH, 1140),
    metric("Audio", "Audio", group = TimelineGroup.MEDIA, format = TimelineValueFormat.BOOL, order = 1200),
    metric("Video", "Video", group = TimelineGroup.MEDIA, format = TimelineValueFormat.BOOL, order = 1210),
    metric("Camera", "Camera", group = TimelineGroup.MEDIA, format = TimelineValueFormat.BOOL, order = 1220),
    metric("Flashlight on", "Flashlight", group = TimelineGroup.MEDIA, format = TimelineValueFormat.BOOL, order = 1230),
    metric("Sensor", "Sensors", group = TimelineGroup.MEDIA, format = TimelineValueFormat.BOOL, order = 1240),
    metric("BLE scanning", "BLE scanning", group = TimelineGroup.MEDIA, format = TimelineValueFormat.BOOL, order = 1250),
    metric("State", "State (raw)", "State", TimelineGroup.SYSTEM, TimelineValueFormat.RAW, 1300),
    metric("Display state changed", "Display state (raw)", "Display state", TimelineGroup.SYSTEM, TimelineValueFormat.RAW, 1310),
).associateBy { it.metric }

/** Returns the display spec for [metric], with a readable fallback for unknown metrics. */
fun metricSpec(metric: String): TimelineMetricSpec =
    metricSpecs[metric] ?: TimelineMetricSpec(
        metric = metric,
        label = metric,
        chartLabel = metric,
        group = TimelineGroup.forMetric(metric),
        format = TimelineValueFormat.RAW,
        order = 2000,
    )

private fun signalLabel(raw: String): String = when (raw.lowercase()) {
    "none", "0" -> "None"
    "poor", "1" -> "Poor"
    "moderate", "2" -> "Moderate"
    "good", "3" -> "Good"
    "great", "4" -> "Great"
    else -> raw.replaceFirstChar { it.titlecase() }
}

private fun chargingStatusLabel(raw: String): String = when (raw) {
    "c" -> "Charging"
    "d" -> "Discharging"
    "f" -> "Full"
    "n" -> "Not charging"
    "?" -> "Unknown"
    else -> raw
}

private fun cpuLabel(raw: String): String {
    if (raw.equals("running", ignoreCase = true)) return "Running"
    val first = raw.substringBefore('|')
    val reason = first.split('~').getOrNull(2)?.trim().orEmpty()
    return when {
        reason.isEmpty() || reason.equals("Unknown wakeup reason", ignoreCase = true) -> "Running"
        else -> "Running · $reason"
    }
}

/** Formats a raw history value for the cursor readout. */
fun formatTimelineValue(spec: TimelineMetricSpec, entry: TimelineEntry): String {
    val raw = entry.value.trim()
    if (raw.isEmpty()) return ""
    return when (spec.format) {
        TimelineValueFormat.BOOL -> when (raw.lowercase()) {
            "true", "on", "1" -> "On"
            "false", "off", "0" -> "Off"
            else -> raw
        }

        TimelineValueFormat.SIGNAL -> signalLabel(raw)
        TimelineValueFormat.CHARGING_STATUS -> chargingStatusLabel(raw)
        TimelineValueFormat.DOZE -> when (raw.lowercase()) {
            "off" -> "Off"
            "light" -> "Light"
            "full" -> "Full"
            "???" -> "Unknown"
            else -> raw
        }

        TimelineValueFormat.PLUG -> when (raw.lowercase()) {
            "n" -> "None"
            "u" -> "USB"
            "a" -> "AC"
            "w" -> "Wireless"
            else -> raw
        }

        TimelineValueFormat.HEALTH -> when (raw.lowercase()) {
            "?" -> "Unknown"
            "g" -> "Good"
            "h" -> "Hot"
            "d" -> "Dead"
            "v" -> "Over voltage"
            "f" -> "Failure"
            "c" -> "Cold"
            else -> raw
        }

        TimelineValueFormat.BRIGHTNESS -> when (raw) {
            "0" -> "Dark"
            "1" -> "Dim"
            "2" -> "Medium"
            "3" -> "Light"
            "4" -> "Bright"
            else -> raw
        }

        TimelineValueFormat.TEMPERATURE -> raw.toFloatOrNull()
            ?.let { String.format(java.util.Locale.US, "%.1f °C", it / 10f) }
            ?: raw

        TimelineValueFormat.VOLTAGE -> raw.toFloatOrNull()?.let { millivolts ->
            if (millivolts >= 1_000f) {
                String.format(java.util.Locale.US, "%.3f V", millivolts / 1_000f)
            } else {
                "${millivolts.toInt()} mV"
            }
        } ?: raw

        TimelineValueFormat.COULOMB -> raw.toLongOrNull()?.let { "$it mAh" } ?: raw
        TimelineValueFormat.CPU -> cpuLabel(raw)
        TimelineValueFormat.PHONE_STATE -> when (raw.lowercase()) {
            "in" -> "In call"
            "out" -> "Outgoing"
            "em" -> "Emergency"
            "off" -> "Off"
            else -> raw
        }

        TimelineValueFormat.WIFI_SUPPLICANT -> when (raw.lowercase()) {
            "compl" -> "Completed"
            "asced" -> "Associated"
            "ascing" -> "Associating"
            "auth" -> "Authenticating"
            "dsc", "dis" -> "Disconnected"
            "scan" -> "Scanning"
            "inact" -> "Inactive"
            "dorm" -> "Dormant"
            "uninit" -> "Uninitialized"
            "inv" -> "Invalid"
            else -> raw
        }

        TimelineValueFormat.NR_STATE -> when (raw.lowercase()) {
            "none" -> "None"
            "restricted" -> "Restricted"
            "not_restricted" -> "Not restricted"
            "connected" -> "Connected"
            else -> raw
        }

        TimelineValueFormat.SERVICE,
        TimelineValueFormat.RAW,
        -> raw
    }
}

/**
 * A chronologically sorted series with a precomputed prefix maximum of end
 * times. [visible] can then find every interval that overlaps a viewport in
 * `O(log n + visible)` instead of scanning the whole report every frame.
 */
class TimelineSeries private constructor(
    val entries: List<TimelineEntry>,
    private val prefixMaxEndMs: LongArray,
) {
    val size: Int get() = entries.size

    fun visible(viewStartMs: Long, viewEndMs: Long): List<TimelineEntry> {
        if (entries.isEmpty() || viewEndMs < viewStartMs) return emptyList()

        // First index whose prefix max can reach the viewport start.
        var low = 0
        var high = prefixMaxEndMs.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (prefixMaxEndMs[mid] < viewStartMs) low = mid + 1 else high = mid
        }
        val earliest = low

        // First index that starts after the viewport end.
        low = 0
        high = entries.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (entries[mid].startMs <= viewEndMs) low = mid + 1 else high = mid
        }
        val endExclusive = low

        if (earliest >= endExclusive) return emptyList()

        val result = ArrayList<TimelineEntry>(endExclusive - earliest)
        for (index in earliest until endExclusive) {
            val entry = entries[index]
            val effectiveEnd = maxOf(entry.startMs, entry.endMs)
            if (effectiveEnd >= viewStartMs) {
                result.add(entry)
            }
        }
        return result
    }

    /**
     * Last entry covering [timeMs], or null when nothing is active.
     *
     * Used by the cursor readout so a point on the chart can be turned into
     * readable values without rescanning the whole series on every frame.
     */
    fun entryAt(timeMs: Long): TimelineEntry? {
        if (entries.isEmpty()) return null

        var low = 0
        var high = entries.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (entries[mid].startMs <= timeMs) low = mid + 1 else high = mid
        }

        var index = low - 1
        while (index >= 0 && prefixMaxEndMs[index] >= timeMs) {
            val entry = entries[index]
            if (maxOf(entry.startMs, entry.endMs) >= timeMs) return entry
            index--
        }
        return null
    }

    companion object {
        fun of(entries: Iterable<TimelineEntry>): TimelineSeries {
            val sorted = entries.sortedWith(
                compareBy<TimelineEntry> { it.startMs }.thenBy { it.endMs },
            )
            val prefix = LongArray(sorted.size)
            var maxEnd = Long.MIN_VALUE
            for (index in sorted.indices) {
                val entry = sorted[index]
                maxEnd = maxOf(maxEnd, maxOf(entry.startMs, entry.endMs))
                prefix[index] = maxEnd
            }
            return TimelineSeries(sorted, prefix)
        }
    }
}

data class TimelineRow(
    val spec: TimelineMetricSpec,
    val series: TimelineSeries,
) {
    val group: TimelineGroup get() = spec.group
    val metric: String get() = spec.metric
    val label: String get() = spec.label
}

data class VisibleRow(
    val row: TimelineRow,
    val entries: List<TimelineEntry>,
)

data class BatteryPoint(
    val timeMs: Long,
    val levelPercent: Float,
)

data class LaneEntry(
    val entry: TimelineEntry,
    val lane: Int,
)

/**
 * True for an interval that represents an inactive state (`off`, `false`, ...).
 *
 * Such intervals are not drawn as bars: the empty space between active bars
 * already communicates the off period, and hiding them prevents a row such as
 * Screen from becoming one solid block. Instant events are always kept.
 */
fun isInactiveInterval(entry: TimelineEntry): Boolean {
    if (entry.endMs <= entry.startMs) return false
    val value = entry.value.trim().lowercase()
    return when (value) {
        "off",
        "false",
        "unplugged",
        "disabled",
        "not charging",
        "none" -> true
        "0" -> entry.type.equals("bool", ignoreCase = true)
        else -> false
    }
}

/**
 * Stable alpha in the 0.62..0.94 range, used to distinguish different values in
 * the same group (for example consecutive top apps) without a lookup table.
 * The value is computed once when a bar is laid out, never while drawing.
 */
fun valueShadeAlpha(value: String): Float {
    if (value.isEmpty()) return 0.86f
    var hash = 17
    for (character in value) {
        hash = 31 * hash + character.code
    }
    val bucket = (hash and Int.MAX_VALUE) % 5
    return 0.62f + bucket * 0.08f
}

/** One point in the battery overlay, sorted by time. */
fun parseBatteryLevel(raw: String): Float? {
    val cleaned = raw.trim().removeSuffix("%").trim()
    val numeric = cleaned.toFloatOrNull() ?: return null
    if (!numeric.isFinite()) return null
    val percent = if (cleaned.contains('.') && numeric in 0f..1f) {
        numeric * 100f
    } else {
        numeric
    }
    return percent.takeIf { it in 0f..100f }
}

/** Builds a sorted, de-duplicated battery series from CSV-style entries. */
fun buildBatteryPoints(entries: List<TimelineEntry>): List<BatteryPoint> {
    if (entries.isEmpty()) return emptyList()
    val byTime = LinkedHashMap<Long, BatteryPoint>()
    for (entry in entries.sortedBy { it.startMs }) {
        val level = parseBatteryLevel(entry.value) ?: continue
        byTime[entry.startMs] = BatteryPoint(entry.startMs, level)
    }
    return byTime.values.toList()
}

/** Last battery level at or before [timeMs], or null when all points are later. */
fun batteryLevelAt(points: List<BatteryPoint>, timeMs: Long): Float? {
    if (points.isEmpty()) return null
    var low = 0
    var high = points.size - 1
    var found = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (points[mid].timeMs <= timeMs) {
            found = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return if (found >= 0) points[found].levelPercent else null
}

/**
 * Returns the battery points needed to draw [startMs]..[endMs], including the
 * predecessor and successor so the line reaches both edges of the viewport.
 */
fun batteryPointsInWindow(
    points: List<BatteryPoint>,
    startMs: Long,
    endMs: Long,
): List<BatteryPoint> {
    if (points.isEmpty() || endMs < startMs) return emptyList()

    var low = 0
    var high = points.size
    while (low < high) {
        val mid = (low + high) ushr 1
        if (points[mid].timeMs <= startMs) low = mid + 1 else high = mid
    }
    val startIndex = (low - 1).coerceAtLeast(0)

    low = 0
    high = points.size
    while (low < high) {
        val mid = (low + high) ushr 1
        if (points[mid].timeMs <= endMs) low = mid + 1 else high = mid
    }
    val endExclusive = (low + 1).coerceAtMost(points.size)
    if (startIndex >= endExclusive) return emptyList()
    return points.subList(startIndex, endExclusive).toList()
}

/**
 * Assigns each entry to one of [laneCount] horizontal tracks so overlapping
 * intervals stay readable. [entries] must be ordered by start time (as
 * returned by [TimelineSeries.visible]).
 *
 * When all tracks are occupied, the entry reuses the track that becomes free
 * first. That keeps long intervals on their own track where possible.
 */
fun assignLanes(entries: List<TimelineEntry>, laneCount: Int = 3): List<LaneEntry> {
    if (entries.isEmpty()) return emptyList()
    val tracks = laneCount.coerceAtLeast(1)
    val laneEnds = LongArray(tracks) { Long.MIN_VALUE }
    val result = ArrayList<LaneEntry>(entries.size)
    for (entry in entries) {
        var lane = -1
        for (index in 0 until tracks) {
            if (entry.startMs > laneEnds[index]) {
                lane = index
                break
            }
        }
        if (lane < 0) {
            lane = 0
            for (index in 1 until tracks) {
                if (laneEnds[index] < laneEnds[lane]) lane = index
            }
        }
        val end = maxOf(entry.startMs, entry.endMs)
        laneEnds[lane] = maxOf(laneEnds[lane], end)
        result.add(LaneEntry(entry, lane))
    }
    return result
}

/**
 * Prepared, immutable timeline data. Building this once per report (not per
 * frame) sorts all series and computes the full range; viewport changes only
 * cull the already-sorted series.
 */
data class TimelineData(
    val range: TimelineRange,
    val rows: List<TimelineRow>,
    /** Every metric present in the report, even when filtered out of [rows]. */
    val availableMetrics: List<TimelineMetricSpec>,
    val batteryPoints: List<BatteryPoint>,
    val reportTakenMs: Long?,
    /** Sorted unique timestamps at which any visible row starts, ends, or changes. */
    val changeTimesMs: LongArray = LongArray(0),
) {

    val isEmpty: Boolean get() = rows.isEmpty() && batteryPoints.isEmpty()

    /** First timestamp at which visible data changes, or null when there is none. */
    val firstChangeMs: Long? get() = changeTimesMs.firstOrNull()

    /** Last timestamp at which visible data changes, or null when there is none. */
    val lastChangeMs: Long? get() = changeTimesMs.lastOrNull()

    /** Clamps [timeMs] to the first/last visible data change, if any. */
    fun clampCursor(timeMs: Long): Long? {
        val first = firstChangeMs ?: return null
        val last = lastChangeMs ?: return null
        return timeMs.coerceIn(first, last)
    }

    val initialViewport: TimelineViewport get() = TimelineViewport.full(range)

    fun visibleRows(viewStartMs: Long, viewEndMs: Long): List<VisibleRow> =
        rows.map { row ->
            VisibleRow(row, row.series.visible(viewStartMs, viewEndMs))
        }

    /** First data-change timestamp strictly after [timeMs]. */
    fun nextChange(timeMs: Long): Long? {
        var low = 0
        var high = changeTimesMs.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (changeTimesMs[mid] <= timeMs) low = mid + 1 else high = mid
        }
        return changeTimesMs.getOrNull(low)
    }

    /** Last data-change timestamp strictly before [timeMs]. */
    fun previousChange(timeMs: Long): Long? {
        var low = 0
        var high = changeTimesMs.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (changeTimesMs[mid] < timeMs) low = mid + 1 else high = mid
        }
        return changeTimesMs.getOrNull(low - 1)
    }

    companion object {
        /**
         * Builds the canvas model.
         *
         * If [batteryEntries] is empty, battery rows are lifted out of
         * [entries] automatically. Battery metrics are always excluded from the
         * metric rows so they do not appear twice. Every other metric gets its
         * own row, ordered by [TimelineMetricSpec.order].
         */
        fun build(
            entries: List<TimelineEntry>,
            batteryEntries: List<TimelineEntry> = emptyList(),
            reportTakenMs: Long? = null,
            visibleMetrics: Set<String>? = null,
        ): TimelineData {
            val batterySource = if (batteryEntries.isNotEmpty()) {
                batteryEntries
            } else {
                entries.filter { TimelineGroup.forMetric(it.metric) == TimelineGroup.BATTERY }
            }
            val batteryPoints = buildBatteryPoints(batterySource)

            val grouped = LinkedHashMap<String, MutableList<TimelineEntry>>()
            for (entry in entries) {
                if (TimelineGroup.forMetric(entry.metric) == TimelineGroup.BATTERY) continue
                grouped.getOrPut(entry.metric) { mutableListOf() }.add(entry)
            }
            val allRows = grouped.entries
                .map { (metric, rowEntries) ->
                    TimelineRow(metricSpec(metric), TimelineSeries.of(rowEntries))
                }
                .sortedWith(
                    compareBy<TimelineRow> { it.spec.order }
                        .thenBy { it.spec.label }
                        .thenBy { it.metric },
                )
            val rows = if (visibleMetrics == null) {
                allRows
            } else {
                allRows.filter { it.metric in visibleMetrics }
            }

            // Change times follow the visible rows only, so the cursor arrows
            // never stop on a hidden metric's event.
            val changes = sortedSetOf<Long>()
            for (row in rows) {
                for (entry in row.series.entries) {
                    changes.add(entry.startMs)
                    changes.add(maxOf(entry.startMs, entry.endMs))
                }
            }
            // With no metric filter the battery is part of the visible data.
            // With a filter active, arrows follow only the selected rows; if
            // every row is filtered out, fall back to battery changes so the
            // cursor still has a usable tick set.
            if (visibleMetrics == null || rows.isEmpty()) {
                for (point in batteryPoints) {
                    changes.add(point.timeMs)
                }
            }

            // Fit the viewport to the visible data. Hidden metrics and the
            // report-taken instant no longer create empty time at the edges.
            val visibleEntries = rows.flatMap { it.series.entries }
            return TimelineData(
                range = TimelineMath.computeRange(visibleEntries, batterySource, null),
                rows = rows,
                availableMetrics = allRows.map { it.spec },
                batteryPoints = batteryPoints,
                reportTakenMs = reportTakenMs,
                changeTimesMs = changes.toLongArray(),
            )
        }
    }
}
