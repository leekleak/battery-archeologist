package com.leekleak.batteryarcheologist.ui.timeline

import com.leekleak.batteryarcheologist.core.model.TimelineEntry

/**
 * Visual grouping for the timeline rows.
 *
 * The metrics mirror the names used by Battery Historian's CSV output
 * (`Battery Level`, `Screen`, `Wifi on`, `Wakelock_in`, `Top app`,
 * `Charging on`, ...), but unknown metrics fall back to [OTHER] instead of
 * being dropped.
 */
enum class TimelineGroup(val label: String) {
    BATTERY("Battery"),
    CHARGING("Charging"),
    SCREEN("Screen"),
    WIFI("Wi-Fi"),
    WAKELOCK("Wakelocks"),
    TOP_APP("Top app"),
    CPU("CPU"),
    OTHER("Other"),
    ;

    companion object {
        fun forMetric(metric: String): TimelineGroup {
            val name = metric.trim().lowercase()
            return when {
                name.contains("battery") -> BATTERY
                name.contains("charg") -> CHARGING
                name.contains("wakelock") || name.contains("wake lock") -> WAKELOCK
                name.contains("wifi") || name.contains("wi-fi") -> WIFI
                name == "screen" || name.startsWith("screen ") -> SCREEN
                name == "top" ||
                    name.startsWith("top app") ||
                    name.contains("top app") ||
                    name.contains("top_application") -> TOP_APP
                name == "cpu" || name.startsWith("cpu ") -> CPU
                else -> OTHER
            }
        }
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
    val group: TimelineGroup,
    val series: TimelineSeries,
)

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
    val batteryPoints: List<BatteryPoint>,
    val reportTakenMs: Long?,
) {
    val eventCount: Int get() = rows.sumOf { it.series.size } + batteryPoints.size

    val isEmpty: Boolean get() = rows.isEmpty() && batteryPoints.isEmpty()

    val initialViewport: TimelineViewport get() = TimelineViewport.full(range)

    fun visibleRows(viewStartMs: Long, viewEndMs: Long): List<VisibleRow> =
        rows.map { row ->
            VisibleRow(row, row.series.visible(viewStartMs, viewEndMs))
        }

    companion object {
        /**
         * Builds the canvas model.
         *
         * If [batteryEntries] is empty, battery rows are lifted out of
         * [entries] automatically. Battery metrics are always excluded from the
         * metric rows so they do not appear twice.
         */
        fun build(
            entries: List<TimelineEntry>,
            batteryEntries: List<TimelineEntry> = emptyList(),
            reportTakenMs: Long? = null,
        ): TimelineData {
            val batterySource = if (batteryEntries.isNotEmpty()) {
                batteryEntries
            } else {
                entries.filter { TimelineGroup.forMetric(it.metric) == TimelineGroup.BATTERY }
            }
            val batteryPoints = buildBatteryPoints(batterySource)

            val grouped = LinkedHashMap<TimelineGroup, MutableList<TimelineEntry>>()
            for (entry in entries) {
                val group = TimelineGroup.forMetric(entry.metric)
                if (group == TimelineGroup.BATTERY) continue
                grouped.getOrPut(group) { mutableListOf() }.add(entry)
            }
            val rows = grouped.entries
                .sortedBy { it.key.ordinal }
                .map { (group, rowEntries) ->
                    TimelineRow(group, TimelineSeries.of(rowEntries))
                }

            return TimelineData(
                range = TimelineMath.computeRange(entries, batteryEntries, reportTakenMs),
                rows = rows,
                batteryPoints = batteryPoints,
                reportTakenMs = reportTakenMs,
            )
        }
    }
}
