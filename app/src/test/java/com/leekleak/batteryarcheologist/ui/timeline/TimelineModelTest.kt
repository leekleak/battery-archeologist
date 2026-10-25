package com.leekleak.batteryarcheologist.ui.timeline

import com.leekleak.batteryarcheologist.core.model.TimelineEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineModelTest {
    private fun entry(
        metric: String,
        startMs: Long,
        endMs: Long = startMs,
        value: String = "on",
    ): TimelineEntry = TimelineEntry(
        metric = metric,
        type = "bool",
        startMs = startMs,
        endMs = endMs,
        value = value,
    )

    @Test
    fun metricsMapToCanonicalGroups() {
        assertEquals(TimelineGroup.BATTERY, TimelineGroup.forMetric("Battery Level"))
        assertEquals(TimelineGroup.CHARGING, TimelineGroup.forMetric("Charging on"))
        assertEquals(TimelineGroup.CHARGING, TimelineGroup.forMetric("Charging status"))
        assertEquals(TimelineGroup.SCREEN, TimelineGroup.forMetric("Screen"))
        assertEquals(TimelineGroup.WIFI, TimelineGroup.forMetric("Wifi on"))
        assertEquals(TimelineGroup.WAKELOCK, TimelineGroup.forMetric("Wakelock_in"))
        assertEquals(TimelineGroup.TOP_APP, TimelineGroup.forMetric("Top app"))
        assertEquals(TimelineGroup.CPU, TimelineGroup.forMetric("CPU running"))
        assertEquals(TimelineGroup.OTHER, TimelineGroup.forMetric("Reboot"))
    }

    @Test
    fun visibleCullsNonOverlappingEntries() {
        val series = TimelineSeries.of(
            listOf(
                entry("Screen", 0L, 100L),
                entry("Screen", 200L, 300L),
                entry("Screen", 250L, 260L),
                entry("Screen", 400L, 400L),
            ),
        )

        assertEquals(
            listOf(200L, 250L),
            series.visible(240L, 270L).map { it.startMs },
        )
        assertEquals(emptyList<TimelineEntry>(), series.visible(150L, 180L))
        assertEquals(4, series.visible(0L, 1_000L).size)
    }

    @Test
    fun visibleIncludesLongIntervalsThatStartBeforeTheWindow() {
        val series = TimelineSeries.of(
            listOf(
                entry("Wakelock_in", 0L, 1_000L),
                entry("Wakelock_in", 500L, 510L),
            ),
        )

        assertEquals(listOf(0L), series.visible(900L, 950L).map { it.startMs })
    }

    @Test
    fun visibleHandlesInstantEventsOnTheBoundary() {
        val series = TimelineSeries.of(listOf(entry("Charging status", 100L, 100L)))

        assertEquals(1, series.visible(100L, 100L).size)
        assertEquals(0, series.visible(101L, 200L).size)
    }

    @Test
    fun lanesKeepOverlappingIntervalsApartAndReuseFreeTracks() {
        val lanes = assignLanes(
            listOf(
                entry("Wakelock_in", 0L, 100L),
                entry("Wakelock_in", 10L, 20L),
                entry("Wakelock_in", 30L, 40L),
                entry("Wakelock_in", 200L, 210L),
            ),
        )

        assertEquals(listOf(0, 1, 1, 0), lanes.map { it.lane })
    }

    @Test
    fun lanesFallBackToTheEarliestFreeTrack() {
        val lanes = assignLanes(
            listOf(
                entry("Wakelock_in", 0L, 100L),
                entry("Wakelock_in", 1L, 100L),
                entry("Wakelock_in", 2L, 100L),
                entry("Wakelock_in", 3L, 100L),
            ),
        )

        assertEquals(listOf(0, 1, 2, 0), lanes.map { it.lane })
    }

    @Test
    fun inactiveIntervalsAreHiddenButInstantEventsRemain() {
        assertTrue(isInactiveInterval(entry("Screen", 0L, 100L, value = "off")))
        assertTrue(isInactiveInterval(entry("Screen", 0L, 100L, value = "false")))
        assertTrue(
            isInactiveInterval(
                TimelineEntry("Wifi on", "bool", 0L, 100L, "0"),
            ),
        )
        assertFalse(
            isInactiveInterval(
                TimelineEntry("Wifi signal strength", "int", 0L, 100L, "0"),
            ),
        )
        assertFalse(isInactiveInterval(entry("Screen", 100L, 100L, value = "off")))
        assertFalse(isInactiveInterval(entry("Wifi on", 0L, 100L, value = "on")))
    }

    @Test
    fun valueShadeIsStableAndBounded() {
        val shade = valueShadeAlpha("com.android.chrome")

        assertEquals(shade, valueShadeAlpha("com.android.chrome"), 0.0001f)
        assertTrue(shade in 0.62f..0.94f)
        assertEquals(0.86f, valueShadeAlpha(""), 0.0001f)
    }

    @Test
    fun batteryValuesAcceptPercentAndFractionForms() {
        assertEquals(85f, parseBatteryLevel("85") ?: Float.NaN, 0.001f)
        assertEquals(85f, parseBatteryLevel("85%") ?: Float.NaN, 0.001f)
        assertEquals(85f, parseBatteryLevel("0.85") ?: Float.NaN, 0.001f)
        assertEquals(100f, parseBatteryLevel("1.0") ?: Float.NaN, 0.001f)
        assertEquals(1f, parseBatteryLevel("1") ?: Float.NaN, 0.001f)
        assertNull(parseBatteryLevel("101"))
        assertNull(parseBatteryLevel("-1"))
        assertNull(parseBatteryLevel("n/a"))
    }

    @Test
    fun batteryPointsAreSortedAndDeduplicated() {
        val points = buildBatteryPoints(
            listOf(
                entry("Battery Level", 200L, value = "80"),
                entry("Battery Level", 100L, value = "90"),
                entry("Battery Level", 200L, value = "70"),
            ),
        )

        assertEquals(listOf(100L, 200L), points.map { it.timeMs })
        assertEquals(listOf(90f, 70f), points.map { it.levelPercent })
    }

    @Test
    fun batteryLookupUsesTheLastKnownLevel() {
        val points = listOf(
            BatteryPoint(0L, 90f),
            BatteryPoint(100L, 80f),
            BatteryPoint(200L, 60f),
        )

        assertNull(batteryLevelAt(points, -1L))
        assertEquals(90f, batteryLevelAt(points, 0L) ?: Float.NaN, 0.001f)
        assertEquals(80f, batteryLevelAt(points, 150L) ?: Float.NaN, 0.001f)
        assertEquals(60f, batteryLevelAt(points, 999L) ?: Float.NaN, 0.001f)
    }

    @Test
    fun batteryWindowIncludesNeighbourPoints() {
        val points = listOf(
            BatteryPoint(0L, 90f),
            BatteryPoint(100L, 80f),
            BatteryPoint(200L, 60f),
        )

        assertEquals(
            listOf(0L, 100L, 200L),
            batteryPointsInWindow(points, 50L, 150L).map { it.timeMs },
        )
        assertEquals(
            listOf(0L, 100L),
            batteryPointsInWindow(points, 0L, 0L).map { it.timeMs },
        )
        assertEquals(
            listOf(200L),
            batteryPointsInWindow(points, 300L, 400L).map { it.timeMs },
        )
    }

    @Test
    fun builderLiftsBatteryRowsOutOfMetricRows() {
        val entries = listOf(
            entry("Battery Level", 0L, value = "90"),
            entry("Battery Level", 100L, value = "80"),
            entry("Screen", 0L, 100L),
            entry("Wifi on", 0L, 100L),
            entry("Reboot", 50L, 50L),
        )

        val data = TimelineData.build(entries)

        assertEquals(2, data.batteryPoints.size)
        assertEquals(
            listOf(TimelineGroup.SCREEN, TimelineGroup.WIFI, TimelineGroup.OTHER),
            data.rows.map { it.group },
        )
        assertTrue(data.rows.none { it.group == TimelineGroup.BATTERY })
    }

    @Test
    fun builderAcceptsABatterySeriesThatIsNotInEntries() {
        val entries = listOf(entry("Screen", 0L, 100L))
        val battery = listOf(entry("Battery Level", 0L, value = "95"))

        val data = TimelineData.build(entries, batteryEntries = battery)

        assertEquals(1, data.rows.size)
        assertEquals(1, data.batteryPoints.size)
        assertEquals(95f, data.batteryPoints.single().levelPercent, 0.001f)
    }

    @Test
    fun everyMetricGetsItsOwnNamedRowInsteadOfFallingIntoOther() {
        val data = TimelineData.build(
            listOf(
                entry("Coulomb charge", 0L, 100L, value = "819"),
                entry("GPS signal quality", 0L, 100L, value = "good"),
                entry("Charging on", 0L, 100L, value = "true"),
            ),
        )

        assertEquals(
            listOf("Charging", "GPS signal", "Charge counter"),
            data.rows.map { it.label },
        )
        assertTrue(data.rows.none { it.label == "Other" })
        val charge = data.rows.single { it.metric == "Coulomb charge" }
        assertEquals(
            "819 mAh",
            formatTimelineValue(charge.spec, charge.series.entries.single()),
        )
    }

    @Test
    fun filteringKeepsOnlySelectedRowsAndTheirChangeTimes() {
        val data = TimelineData.build(
            entries = listOf(
                entry("Screen", 0L, 100L),
                entry("Wifi on", 50L, 60L),
                entry("Coulomb charge", 70L, 70L, value = "819"),
            ),
            visibleMetrics = setOf("Screen"),
        )

        assertEquals(listOf("Screen"), data.rows.map { it.metric })
        assertEquals(
            setOf("Screen", "Wifi on", "Coulomb charge"),
            data.availableMetrics.map { it.metric }.toSet(),
        )
        assertEquals(listOf(0L, 100L), data.changeTimesMs.toList())
        assertEquals(100L, data.nextChange(0L))
        assertEquals(0L, data.previousChange(50L))
        assertNull(data.nextChange(100L))
    }

    @Test
    fun filteredArrowTicksIgnoreBatteryOnlyChanges() {
        val battery = listOf(
            entry("Battery Level", 0L, 0L, value = "90"),
            entry("Battery Level", 50L, 50L, value = "80"),
            entry("Battery Level", 100L, 100L, value = "70"),
        )
        val data = TimelineData.build(
            entries = listOf(entry("Screen", 0L, 100L)),
            batteryEntries = battery,
            visibleMetrics = setOf("Screen"),
        )

        assertEquals(listOf(0L, 100L), data.changeTimesMs.toList())
    }

    @Test
    fun cursorCannotEnterThePaddingOutsideTheVisibleChanges() {
        val data = TimelineData.build(
            entries = listOf(entry("Screen", 100L, 200L)),
            visibleMetrics = setOf("Screen"),
        )

        assertEquals(100L, data.clampCursor(0L))
        assertEquals(100L, data.clampCursor(100L))
        assertEquals(150L, data.clampCursor(150L))
        assertEquals(200L, data.clampCursor(500L))
    }

    @Test
    fun cursorSteppingUsesTheNextAndPreviousDataChange() {
        val data = TimelineData.build(
            listOf(
                entry("Screen", 100L, 200L),
                entry("Wifi on", 150L, 160L),
            ),
        )

        assertNull(data.previousChange(100L))
        assertEquals(100L, data.previousChange(120L))
        assertEquals(150L, data.nextChange(120L))
        assertEquals(160L, data.nextChange(150L))
        assertNull(data.nextChange(200L))
    }

    @Test
    fun valuesAreFormattedForTheReadout() {
        assertEquals(
            "Discharging",
            formatTimelineValue(
                metricSpec("Charging status"),
                entry("Charging status", 0L, value = "d"),
            ),
        )
        assertEquals(
            "Great",
            formatTimelineValue(
                metricSpec("Wifi signal strength"),
                entry("Wifi signal strength", 0L, value = "great"),
            ),
        )
        assertEquals(
            "21.9 °C",
            formatTimelineValue(
                metricSpec("Temperature"),
                entry("Temperature", 0L, value = "219"),
            ),
        )
        assertEquals(
            "Running · Alarm",
            formatTimelineValue(
                metricSpec("CPU running"),
                entry("CPU running", 0L, value = "1~2~Alarm"),
            ),
        )
    }

    @Test
    fun visibleRowsKeepEveryRowForStableLabels() {
        val data = TimelineData.build(
            listOf(
                entry("Screen", 0L, 100L),
                entry("Wifi on", 1_000L, 2_000L),
            ),
        )

        val visible = data.visibleRows(0L, 100L)

        assertEquals(2, visible.size)
        assertEquals(1, visible[0].entries.size)
        assertEquals(0, visible[1].entries.size)
    }
}
