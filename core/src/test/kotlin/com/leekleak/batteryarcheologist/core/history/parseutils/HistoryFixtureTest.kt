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
 * Golden-fixture test for the API 36 history capture. Compares the supported
 * metrics against tools/fixtures/api36/expected/. Metrics the slice does not
 * parse yet (Network connectivity, Doze, low power states) are excluded.
 */

package com.leekleak.batteryarcheologist.core.history.parseutils

import com.leekleak.batteryarcheologist.core.history.csv.parseCsvRecords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HistoryFixtureTest {
    private val fixtureDir: File by lazy {
        val candidates = listOf(
            File("../tools/fixtures/api36"),
            File("tools/fixtures/api36"),
            File("../../tools/fixtures/api36"),
        )
        candidates.firstOrNull { it.isDirectory }
            ?: error("api36 fixture directory not found; cwd=${File(".").absolutePath}")
    }

    private val checkin: String by lazy {
        File(fixtureDir, "checkin.txt").readText()
    }

    /** Metrics this vertical slice is expected to reproduce byte-for-byte. */
    private val supportedTimelineMetrics = setOf(
        HistoryMetrics.BATTERY_LEVEL,
        "Screen",
        "Charging on",
        "Plugged",
        "Wifi on",
        "CPU running",
        "Partial wakelock",
        "Top app",
        "Mobile radio active",
        "Mobile signal strength",
        "Mobile network type",
        "Phone state",
        "Wifi supplicant",
        "Wifi signal strength",
        "JobScheduler",
        "Temp White List",
        "Long Wakelocks",
        "Foreground process",
        "App Processor wakeup",
        "Package install",
        "Package active",
        "Sensor",
        "Wifi scan",
        "Wifi radio",
        "Charging status",
        "Health",
        "Plug",
        "Brightness",
        "Temperature",
        "Voltage",
        "Coulomb charge",
        "Phone scanning",
    )

    private fun rows(csv: String): List<List<String>> =
        parseCsvRecords(csv).filter { it.isNotEmpty() }

    @Test
    fun `api36 fixture produces the golden summaries and supported timeline rows`() {
        val result = HistoryParser.analyze(
            checkin,
            HistorySummaryFormat.BATTERY_LEVEL,
            emptyList(),
        )

        // No crash, and the report version is detected.
        assertEquals(36, result.reportVersion)
        assertTrue(result.timestampsAltered)
        // Android 16 state flags/events are modeled rather than reported as unknown keys.
        // The low-power SubsystemPowerState payload is still on the backlog.
        assertEquals(setOf("SubsystemPowerState"), result.unsupportedKeys.keys)
        assertFalse(result.errors.any { it.contains("unknown key") })
        assertEquals(4, result.summaries.size)
        assertEquals(
            listOf("RESET", "LEVEL", "LEVEL", "END"),
            result.summaries.map { it.reason },
        )
        assertEquals(
            listOf(
                Triple(1792752870253L, 1792753703285L, 100 to 100),
                Triple(1792753801943L, 1792753803954L, 70 to 60),
                Triple(1792753803954L, 1792753805965L, 60 to 50),
                Triple(1792753805965L, 1792754222959L, 50 to 50),
            ),
            result.summaries.map {
                Triple(
                    it.startTimeMs,
                    it.endTimeMs,
                    it.initialBatteryLevel to it.finalBatteryLevel,
                )
            },
        )
        assertFalse(result.summaries[0].active)

        val levelSummary = result.summaries[1]
        assertEquals(Dist(1, 2011, 2011), levelSummary.screenOnSummary)
        assertEquals(Dist(1, 2011, 2011), levelSummary.wifiOnSummary)
        assertEquals(Dist(1, 2011, 2011), levelSummary.cpuRunningSummary)
        assertEquals(
            mapOf("\"com.leekleak.batteryarcheologist\"" to Dist(1, 2011, 2011)),
            levelSummary.topApplicationSummary,
        )

        val endSummary = result.summaries[3]
        assertEquals(Dist(1, 416994, 416994), endSummary.screenOnSummary)
        assertEquals(Dist(1, 10159, 10159), endSummary.mobileRadioOnSummary)
        assertEquals(Dist(1, 416994, 416994), endSummary.cpuRunningSummary)
        assertEquals(Dist(2, 684, 536), endSummary.sensorOnSummary)
        assertEquals(Dist(3, 129, 45), endSummary.wifiScanSummary)
        assertEquals(Dist(3, 55523, 20193), endSummary.wifiRadioSummary)
        assertEquals(
            mapOf("\"com.leekleak.batteryarcheologist\"" to Dist(1, 416994, 416994)),
            endSummary.topApplicationSummary,
        )

        // Level CSV is the user-facing discharge summary. Compare it exactly.
        val levelCsv = result.levelCsv ?: error("level CSV missing")
        assertEquals(
            rows(File(fixtureDir, "expected/history-level.csv").readText()),
            rows(levelCsv),
        )

        // Timeline: compare every metric the slice supports. Unsupported
        // upstream metrics are filtered out of the golden side.
        val timelineResult = HistoryParser.analyze(
            checkin,
            HistorySummaryFormat.TOTAL_TIME,
            emptyList(),
        )
        assertTrue("timeline should not be empty", timelineResult.entries.isNotEmpty())

        val expectedTimeline = rows(
            File(fixtureDir, "expected/history-timeline.csv").readText(),
        ).filter { it.first() in supportedTimelineMetrics }
        val actualTimeline = rows(timelineResult.csv)
            .filter { it.first() in supportedTimelineMetrics }

        assertEquals(
            expectedTimeline.sortedBy { it.joinToString(",") },
            actualTimeline.sortedBy { it.joinToString(",") },
        )
    }
}
