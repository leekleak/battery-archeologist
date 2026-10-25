package com.leekleak.batteryarcheologist.ui.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineFixturesTest {
    @Test
    fun fixtureIsDeterministic() {
        assertEquals(TimelineFixtures.build(), TimelineFixtures.build())
    }

    @Test
    fun fixtureCoversEveryTimelineGroup() {
        val fixture = TimelineFixtures.build()
        val data = TimelineData.build(
            entries = fixture.entries,
            batteryEntries = fixture.batteryEntries,
            reportTakenMs = fixture.reportTakenMs,
        )

        assertEquals(
            listOf(
                "Charging",
                "Charging status",
                "Screen",
                "Wi-Fi",
                "Wakelocks",
                "Top app",
                "CPU",
            ),
            data.rows.map { it.label },
        )
        assertEquals(
            setOf(
                TimelineGroup.CHARGING,
                TimelineGroup.SCREEN,
                TimelineGroup.WIFI,
                TimelineGroup.WAKELOCK,
                TimelineGroup.TOP_APP,
                TimelineGroup.CPU,
            ),
            data.rows.map { it.group }.toSet(),
        )
        assertTrue(data.batteryPoints.isNotEmpty())
    }

    @Test
    fun fixtureBatterySeriesIsCompleteAndSorted() {
        val fixture = TimelineFixtures.build()

        assertEquals(25, fixture.batteryEntries.size)
        assertTrue(fixture.entries.containsAll(fixture.batteryEntries))
        assertEquals(88f, fixture.batteryEntries.first().value.toFloat(), 0.001f)
        assertEquals(77f, fixture.batteryEntries.last().value.toFloat(), 0.001f)
        assertTrue(fixture.batteryEntries.zipWithNext().all { (a, b) -> a.startMs < b.startMs })
        assertTrue(fixture.batteryEntries.all { parseBatteryLevel(it.value) != null })
    }

    @Test
    fun fixtureReportMarkerIsInsideTheComputedRange() {
        val fixture = TimelineFixtures.build()
        val data = TimelineData.build(
            entries = fixture.entries,
            batteryEntries = fixture.batteryEntries,
            reportTakenMs = fixture.reportTakenMs,
        )

        assertEquals(TimelineFixtures.EPOCH_MS + TimelineFixtures.DURATION_MS, fixture.reportTakenMs)
        assertNotNull(data.reportTakenMs)
        assertTrue(fixture.reportTakenMs >= data.range.startMs)
        assertTrue(fixture.reportTakenMs <= data.range.endMs)
        assertTrue(fixture.entries.all { it.endMs >= it.startMs })
    }
}
