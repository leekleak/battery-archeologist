package com.leekleak.batteryarcheologist.ui.timeline

import com.leekleak.batteryarcheologist.core.model.TimelineEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineMathTest {
    private fun entry(
        startMs: Long,
        endMs: Long = startMs,
        metric: String = "Screen",
    ): TimelineEntry = TimelineEntry(
        metric = metric,
        type = "bool",
        startMs = startMs,
        endMs = endMs,
        value = "on",
    )

    @Test
    fun emptyRangeIsStillUsable() {
        val range = TimelineMath.computeRange(entries = emptyList())
        assertEquals(0L, range.startMs)
        assertEquals(TimelineMath.DEFAULT_MIN_SPAN_MS, range.endMs)
    }

    @Test
    fun rangeIncludesEntriesBatteryAndReportThenPads() {
        val range = TimelineMath.computeRange(
            entries = listOf(entry(0L, 600_000L)),
            batteryEntries = listOf(entry(-600_000L, metric = "Battery Level")),
            reportTakenMs = 1_200_000L,
        )

        assertEquals(-636_000L, range.startMs)
        assertEquals(1_236_000L, range.endMs)
        assertTrue(range.startMs < -600_000L)
        assertTrue(range.endMs > 1_200_000L)
    }

    @Test
    fun shortDataIsWidenedSymmetrically() {
        val range = TimelineMath.computeRange(
            entries = listOf(entry(10_000L, 20_000L)),
            batteryEntries = listOf(entry(5_000L, metric = "Battery Level")),
            reportTakenMs = 30_000L,
        )

        assertEquals(TimelineMath.DEFAULT_MIN_SPAN_MS, range.endMs - range.startMs)
        assertTrue(range.startMs <= 5_000L)
        assertTrue(range.endMs >= 30_000L)
    }

    @Test
    fun zoomKeepsTheFocusTimestampFixed() {
        val range = TimelineRange(0L, 100_000L)
        val viewport = TimelineViewport.full(range)

        val zoomed = TimelineMath.zoomViewport(
            viewport = viewport,
            range = range,
            zoomFactor = 2f,
            focusFraction = 0.25f,
        )

        assertEquals(12_500L, zoomed.startMs)
        assertEquals(62_500L, zoomed.endMs)
        assertEquals(25_000L, zoomed.startMs + (zoomed.spanMs * 0.25f).toLong())
    }

    @Test
    fun repeatedZoomStopsAtTheMinimumSpan() {
        val range = TimelineRange(0L, 100_000L)
        var viewport = TimelineViewport.full(range)

        repeat(20) {
            viewport = TimelineMath.zoomViewport(viewport, range, 2f, 0.5f)
        }

        assertEquals(TimelineMath.MIN_VIEWPORT_MS, viewport.spanMs)
        assertTrue(viewport.startMs >= range.startMs)
        assertTrue(viewport.endMs <= range.endMs)
    }

    @Test
    fun panMovesBackwardsForAPositiveDragAndClamps() {
        val range = TimelineRange(0L, 100_000L)
        val start = TimelineViewport(40_000L, 60_000L)

        val panned = TimelineMath.panViewport(start, range, 0.5f)
        assertEquals(30_000L, panned.startMs)
        assertEquals(50_000L, panned.endMs)

        val clamped = TimelineMath.panViewport(panned, range, -10f)
        assertEquals(80_000L, clamped.startMs)
        assertEquals(100_000L, clamped.endMs)
    }

    @Test
    fun transformAppliesZoomThenPan() {
        val range = TimelineRange(0L, 100_000L)
        val viewport = TimelineViewport.full(range)

        val transformed = TimelineMath.transformViewport(
            viewport = viewport,
            range = range,
            zoomFactor = 2f,
            panFraction = 0.5f,
            focusFraction = 0.5f,
        )

        assertEquals(0L, transformed.startMs)
        assertEquals(50_000L, transformed.endMs)
    }

    @Test
    fun tickStepsUseFriendlyWallClockUnits() {
        assertEquals(900_000L, TimelineMath.tickStepMs(3_600_000L, 5))
        assertEquals(3_600_000L, TimelineMath.tickStepMs(14_400_000L, 5))
        assertEquals(120_000L, TimelineMath.tickStepMs(600_000L, 5))
        assertEquals(10_000L, TimelineMath.tickStepMs(30_000L, 5))
        assertEquals(1_000L, TimelineMath.tickStepMs(1_000L, 5))
    }

    @Test
    fun ticksAlignToTheReportAnchor() {
        val anchor = java.time.Instant.parse("2023-11-14T22:00:00Z").toEpochMilli()
        val viewport = TimelineViewport(anchor - 3_600_000L, anchor + 3_600_000L)

        val ticks = TimelineMath.tickTimes(viewport, targetTicks = 5, anchorMs = anchor)

        assertEquals(
            listOf(
                anchor - 3_600_000L,
                anchor - 1_800_000L,
                anchor,
                anchor + 1_800_000L,
                anchor + 3_600_000L,
            ),
            ticks,
        )
        assertEquals(
            listOf("21:00", "21:30", "22:00", "22:30", "23:00"),
            ticks.map {
                TimelineMath.formatClockTime(
                    it,
                    spanMs = viewport.spanMs,
                    zoneId = java.time.ZoneOffset.UTC,
                )
            },
        )
    }

    @Test
    fun ticksAreInsideTheViewport() {
        val viewport = TimelineViewport(-100_000L, 100_000L)
        val ticks = TimelineMath.tickTimes(viewport, targetTicks = 5, anchorMs = 0L)

        assertEquals(listOf(-60_000L, 0L, 60_000L), ticks)
        assertTrue(ticks.all { it in viewport.startMs..viewport.endMs })
    }

    @Test
    fun clockTimesUseGlobalWallClockLabels() {
        val time = java.time.Instant.parse("2023-11-14T22:13:00Z").toEpochMilli()
        val utc = java.time.ZoneOffset.UTC

        assertEquals(
            "22:13",
            TimelineMath.formatClockTime(time, spanMs = 2 * 3_600_000L, zoneId = utc),
        )
        assertEquals(
            "22:13:00",
            TimelineMath.formatClockTime(time, spanMs = 30 * 60_000L, zoneId = utc),
        )
        assertTrue(
            TimelineMath.formatClockTime(time, spanMs = 3 * 86_400_000L, zoneId = utc)
                .contains("22:13"),
        )
        assertEquals(
            "2023-11-14T00:00:00Z",
            java.time.Instant
                .ofEpochMilli(TimelineMath.localDayStart(time, utc))
                .toString(),
        )
    }

    @Test
    fun cursorTimestampsIncludeSeconds() {
        val time = java.time.Instant.parse("2023-11-14T22:13:45Z").toEpochMilli()

        assertTrue(
            TimelineMath.formatClockDateTime(time, java.time.ZoneOffset.UTC)
                .endsWith("22:13:45"),
        )
        assertEquals(
            "22:13:45",
            TimelineMath.formatClockTimeSeconds(time, java.time.ZoneOffset.UTC),
        )
        assertTrue(
            TimelineMath.formatClockDate(time, java.time.ZoneOffset.UTC).isNotBlank(),
        )
    }

    @Test
    fun spansAreFormattedForHeaders() {
        assertEquals("42s", TimelineMath.formatSpan(42_000L))
        assertEquals("1m 30s", TimelineMath.formatSpan(90_000L))
        assertEquals("1h", TimelineMath.formatSpan(3_600_000L))
        assertEquals("1h 5m", TimelineMath.formatSpan(3_900_000L))
        assertEquals("1d 1h", TimelineMath.formatSpan(90_000_000L))
    }

    @Test
    fun tickCountFitsTheAvailableWidth() {
        assertEquals(5, TimelineMath.tickCountForWidth(1_000f, 200f))
        assertEquals(2, TimelineMath.tickCountForWidth(10f, 200f))
        assertEquals(2, TimelineMath.tickCountForWidth(0f, 200f))
    }
}
