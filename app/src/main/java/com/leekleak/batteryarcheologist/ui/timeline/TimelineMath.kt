package com.leekleak.batteryarcheologist.ui.timeline

import com.leekleak.batteryarcheologist.core.model.TimelineEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Absolute time span covered by the timeline.
 *
 * All values are epoch milliseconds, like [TimelineEntry.startMs]. The range is
 * always non-empty so that it can safely be used as a divisor and a viewport
 * bound.
 */
data class TimelineRange(
    val startMs: Long,
    val endMs: Long,
) {
    init {
        require(endMs >= startMs) { "endMs ($endMs) must be >= startMs ($startMs)" }
    }

    val spanMs: Long get() = (endMs - startMs).coerceAtLeast(1L)

    /** Position of [timeMs] in this range, where 0 is the start and 1 is the end. */
    fun fraction(timeMs: Long): Float =
        ((timeMs - startMs).toDouble() / spanMs.toDouble()).toFloat()

    fun clamp(timeMs: Long): Long = timeMs.coerceIn(startMs, endMs)
}

/** The portion of a [TimelineRange] that is currently visible. */
data class TimelineViewport(
    val startMs: Long,
    val endMs: Long,
) {
    init {
        require(endMs > startMs) { "viewport must be non-empty" }
    }

    val spanMs: Long get() = endMs - startMs

    fun fraction(timeMs: Long): Float =
        ((timeMs - startMs).toDouble() / spanMs.toDouble()).toFloat()

    companion object {
        fun full(range: TimelineRange): TimelineViewport =
            TimelineViewport(range.startMs, range.endMs)
    }
}

/**
 * Pure timeline math shared by the canvas, the controls, and unit tests.
 *
 * Keeping these functions free of Compose and Android imports lets the JVM
 * tests exercise the interaction and culling logic directly.
 */
object TimelineMath {
    /** Hard lower bound for pinch/button zoom. */
    const val MIN_VIEWPORT_MS: Long = 10_000L

    /** Range used when there is no data yet. */
    const val DEFAULT_MIN_SPAN_MS: Long = 60_000L

    private const val TICK_LIMIT = 64

    /**
     * Human-friendly time steps. These are deliberately all multiples of
     * seconds/minutes/hours instead of a generic 1-2-5 decimal sequence, which
     * would produce labels like "8m20s".
     */
    private val TICK_STEPS_MS = longArrayOf(
        1_000L,
        2_000L,
        5_000L,
        10_000L,
        15_000L,
        30_000L,
        60_000L,
        2L * 60_000L,
        5L * 60_000L,
        10L * 60_000L,
        15L * 60_000L,
        30L * 60_000L,
        60L * 60_000L,
        2L * 60L * 60_000L,
        3L * 60L * 60_000L,
        6L * 60L * 60_000L,
        12L * 60L * 60_000L,
        24L * 60L * 60_000L,
        2L * 24L * 60L * 60_000L,
        7L * 24L * 60L * 60_000L,
    )

    /**
     * Computes the full range covered by [entries], the optional battery
     * series, and the report-taken marker.
     *
     * The result gets a small amount of horizontal breathing room and is
     * widened symmetrically when the data is very short. If there are no
     * timestamps at all, a [DEFAULT_MIN_SPAN_MS] range starting at zero is
     * returned so the canvas can still render a valid axis.
     */
    fun computeRange(
        entries: List<TimelineEntry>,
        batteryEntries: List<TimelineEntry> = emptyList(),
        reportTakenMs: Long? = null,
        minSpanMs: Long = DEFAULT_MIN_SPAN_MS,
    ): TimelineRange {
        var firstMs = Long.MAX_VALUE
        var lastMs = Long.MIN_VALUE

        fun include(timeMs: Long) {
            if (timeMs < firstMs) firstMs = timeMs
            if (timeMs > lastMs) lastMs = timeMs
        }

        for (entry in entries) {
            include(entry.startMs)
            include(maxOf(entry.startMs, entry.endMs))
        }
        for (entry in batteryEntries) {
            include(entry.startMs)
            include(maxOf(entry.startMs, entry.endMs))
        }
        reportTakenMs?.let(::include)

        if (firstMs == Long.MAX_VALUE) {
            return TimelineRange(0L, minSpanMs.coerceAtLeast(1L))
        }

        val minimum = minSpanMs.coerceAtLeast(1L)
        var start = firstMs
        var end = lastMs
        val span = end - start

        if (span < minimum) {
            val target = minimum
            val center = start + span / 2L
            start = center - target / 2L
            end = start + target
        } else {
            val padding = (span / 50L).coerceAtLeast(1_000L)
            start -= padding
            end += padding
        }
        return TimelineRange(start, end)
    }

    /**
     * Fits [viewport] inside [range] and enforces [minSpanMs].
     *
     * If the whole range is shorter than [minSpanMs], the viewport covers the
     * whole range instead of refusing to render.
     */
    fun clampViewport(
        viewport: TimelineViewport,
        range: TimelineRange,
        minSpanMs: Long = MIN_VIEWPORT_MS,
    ): TimelineViewport {
        val lowerSpan = minSpanMs.coerceIn(1L, range.spanMs)
        val span = viewport.spanMs.coerceIn(lowerSpan, range.spanMs)
        val maxStart = range.endMs - span
        val start = viewport.startMs.coerceIn(range.startMs, maxOf(range.startMs, maxStart))
        return TimelineViewport(start, start + span)
    }

    /**
     * Zooms around [focusFraction] (0 = left edge, 1 = right edge).
     *
     * A [zoomFactor] greater than one zooms in, matching the value reported by
     * `detectTransformGestures`.
     */
    fun zoomViewport(
        viewport: TimelineViewport,
        range: TimelineRange,
        zoomFactor: Float,
        focusFraction: Float = 0.5f,
    ): TimelineViewport {
        if (!zoomFactor.isFinite() || zoomFactor <= 0f) return viewport
        val focus = focusFraction.coerceIn(0f, 1f)
        val focusTime = viewport.startMs + (viewport.spanMs * focus).roundToLong()
        val newSpan = (viewport.spanMs / zoomFactor)
            .roundToLong()
            .coerceIn(MIN_VIEWPORT_MS.coerceAtMost(range.spanMs), range.spanMs)
        val newStart = focusTime - (newSpan * focus).roundToLong()
        return clampViewport(
            TimelineViewport(newStart, newStart + newSpan),
            range,
        )
    }

    /**
     * Pans the viewport. A positive [deltaFraction] means the content is
     * dragged to the right, so the viewport moves backwards in time.
     */
    fun panViewport(
        viewport: TimelineViewport,
        range: TimelineRange,
        deltaFraction: Float,
    ): TimelineViewport {
        if (!deltaFraction.isFinite()) return viewport
        val deltaMs = (viewport.spanMs * deltaFraction).roundToLong()
        val start = viewport.startMs - deltaMs
        return clampViewport(TimelineViewport(start, start + viewport.spanMs), range)
    }

    /**
     * Applies one gesture frame: zoom around [focusFraction], then pan.
     * [panFraction] is measured in viewport widths, as reported by the canvas.
     */
    fun transformViewport(
        viewport: TimelineViewport,
        range: TimelineRange,
        zoomFactor: Float,
        panFraction: Float,
        focusFraction: Float,
    ): TimelineViewport {
        val zoomed = zoomViewport(viewport, range, zoomFactor, focusFraction)
        return panViewport(zoomed, range, panFraction)
    }

    /**
     * Chooses a human-friendly tick interval (seconds, minutes, hours, days)
     * for a viewport of [spanMs] with roughly [targetTicks] labels.
     */
    fun tickStepMs(spanMs: Long, targetTicks: Int): Long {
        val ticks = targetTicks.coerceAtLeast(1)
        val rough = (spanMs.coerceAtLeast(1L) / ticks.toDouble()).coerceAtLeast(1.0)
        for (step in TICK_STEPS_MS) {
            if (step.toDouble() >= rough) return step
        }
        return TICK_STEPS_MS.last()
    }

    /**
     * Tick timestamps within [viewport], aligned to [anchorMs].
     *
     * For the global clock axis the anchor is the start of the local day, so
     * labels fall on clean local times (for example 08:00, 09:00) even when the
     * viewport is padded.
     */
    fun tickTimes(
        viewport: TimelineViewport,
        targetTicks: Int,
        anchorMs: Long = viewport.endMs,
    ): List<Long> {
        val step = tickStepMs(viewport.spanMs, targetTicks)
        val offset = viewport.startMs - anchorMs
        var index = Math.floorDiv(offset, step)
        if (index * step < offset) index++
        var tick = anchorMs + index * step

        val result = ArrayList<Long>(targetTicks.coerceIn(1, TICK_LIMIT))
        var guard = 0
        while (tick <= viewport.endMs && guard < TICK_LIMIT) {
            if (tick >= viewport.startMs) result.add(tick)
            tick += step
            guard++
        }
        return result
    }

    private val CLOCK_TIME: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val CLOCK_TIME_SECONDS: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())
    private val CLOCK_DATE_TIME: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.getDefault())
    private val CLOCK_DATE_TIME_SECONDS: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d HH:mm:ss", Locale.getDefault())
    private val CLOCK_DATE: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    /**
     * Formats an epoch timestamp as a global wall-clock time.
     *
     * Spans over a day include the date; short spans include seconds so ticks
     * do not repeat the same minute.
     */
    fun formatClockTime(
        timeMs: Long,
        spanMs: Long = DEFAULT_MIN_SPAN_MS,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val formatter = when {
            spanMs >= DAY_MS -> CLOCK_DATE_TIME
            spanMs < HOUR_MS -> CLOCK_TIME_SECONDS
            else -> CLOCK_TIME
        }
        return formatter.withZone(zoneId).format(Instant.ofEpochMilli(timeMs))
    }

    /** Date part of the cursor timestamp, for example `Nov 14`. */
    fun formatClockDate(
        timeMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String = CLOCK_DATE.withZone(zoneId).format(Instant.ofEpochMilli(timeMs))

    /** Time part of the cursor timestamp, including seconds. */
    fun formatClockTimeSeconds(
        timeMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String = CLOCK_TIME_SECONDS.withZone(zoneId).format(Instant.ofEpochMilli(timeMs))

    /** Full global timestamp with seconds, used by tests and diagnostics. */
    fun formatClockDateTime(
        timeMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String = CLOCK_DATE_TIME_SECONDS.withZone(zoneId).format(Instant.ofEpochMilli(timeMs))

    /** Start of the local day containing [timeMs], used to align clock ticks. */
    fun localDayStart(
        timeMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long = Instant.ofEpochMilli(timeMs)
        .atZone(zoneId)
        .toLocalDate()
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()

    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 86_400_000L

    /** Formats a duration compactly for headers: `42s`, `12m`, `3h 5m`, `2d 4h`. */
    fun formatSpan(spanMs: Long): String {
        val magnitude = abs(spanMs)
        return when {
            magnitude < 60_000L -> "${magnitude / 1_000L}s"
            magnitude < 3_600_000L -> {
                val minutes = magnitude / 60_000L
                val seconds = (magnitude % 60_000L) / 1_000L
                if (seconds == 0L) "${minutes}m" else "${minutes}m ${seconds}s"
            }
            magnitude < 86_400_000L -> {
                val hours = magnitude / 3_600_000L
                val minutes = (magnitude % 3_600_000L) / 60_000L
                if (minutes == 0L) "${hours}h" else "${hours}h ${minutes}m"
            }
            else -> {
                val days = magnitude / 86_400_000L
                val hours = (magnitude % 86_400_000L) / 3_600_000L
                if (hours == 0L) "${days}d" else "${days}d ${hours}h"
            }
        }
    }

    /** Maps a viewport to a clamped fraction, useful for gesture bookkeeping. */
    fun viewportFraction(viewport: TimelineViewport, timeMs: Long): Float =
        ((timeMs - viewport.startMs).toDouble() / viewport.spanMs.toDouble())
            .toFloat()
            .coerceIn(0f, 1f)

    /** Number of ticks that comfortably fits into [widthPx] at [minSpacingPx]. */
    fun tickCountForWidth(widthPx: Float, minSpacingPx: Float): Int {
        if (!widthPx.isFinite() || widthPx <= 0f) return 2
        if (!minSpacingPx.isFinite() || minSpacingPx <= 0f) return 4
        return ceil(widthPx / minSpacingPx).toInt().coerceIn(2, 7)
    }
}
