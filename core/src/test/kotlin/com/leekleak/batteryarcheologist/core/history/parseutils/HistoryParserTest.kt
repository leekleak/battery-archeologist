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
 * Ports and adaptations of parseutils tests covering resets, battery level,
 * screen, charging, wakelocks, sync, and top apps.
 */

package com.leekleak.batteryarcheologist.core.history.parseutils

import com.leekleak.batteryarcheologist.core.history.csv.parseCsvRecords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryParserTest {
    private fun analyze(
        input: String,
        format: HistorySummaryFormat = HistorySummaryFormat.TOTAL_TIME,
    ): HistoryResult = HistoryParser.analyze(input, format, emptyList())

    private fun csvRows(csv: String): List<List<String>> =
        parseCsvRecords(csv).filterNot { it.firstOrNull() == "metric" }

    @Test
    fun `multiple resets produce one summary per session`() {
        val input = listOf(
            "9,0,i,vers,11,116,LMY06B,LMY06B",
            "9,h,0:RESET:TIME:1422620444417", // fixTimeline changes this to ...441417.
            "9,h,2000,+S",
            "9,h,7000,-S",
            "9,h,0:RESET:TIME:1422620450417",
            "9,h,1000,+S",
            "9,h,100,-S",
        ).joinToString("\n")

        val result = analyze(input)
        assertEquals(emptyList<String>(), result.errors)
        assertEquals(2, result.summaries.size)

        val first = result.summaries[0]
        assertEquals(1422620441417, first.startTimeMs)
        assertEquals(1422620450417, first.endTimeMs)
        assertEquals(Dist(1, 7000, 7000), first.screenOnSummary)

        val second = result.summaries[1]
        assertEquals(1422620450417, second.startTimeMs)
        assertEquals(1422620451517, second.endTimeMs)
        assertEquals(Dist(1, 100, 100), second.screenOnSummary)
    }

    @Test
    fun `battery level format splits summaries on level drops`() {
        val input = listOf(
            "9,h,0:RESET:TIME:1000000000000",
            "9,h,0,Bl=100,Bs=d",
            "9,h,1000,Bl=99",
            "9,h,1000,Bl=98",
        ).joinToString("\n")

        val result = analyze(input, HistorySummaryFormat.BATTERY_LEVEL)
        assertEquals(emptyList<String>(), result.errors)
        assertEquals(2, result.summaries.size)

        val first = result.summaries[0]
        assertEquals("LEVEL", first.reason)
        assertEquals(100, first.initialBatteryLevel)
        assertEquals(99, first.finalBatteryLevel)
        assertEquals(1000000000000, first.startTimeMs)
        assertEquals(1000000001000, first.endTimeMs)

        val second = result.summaries[1]
        assertEquals(99, second.initialBatteryLevel)
        assertEquals(98, second.finalBatteryLevel)

        val levelCsv = result.levelCsv ?: error("level CSV missing")
        val rows = parseCsvRecords(levelCsv)
        // Header plus two drops.
        assertEquals(3, rows.size)
        assertEquals("StartTime", rows[0][0])
        assertEquals("1000000000000", rows[1][0])
        assertEquals("1000000001000", rows[1][1])
        assertEquals("1000", rows[1][2])
        assertEquals("LEVEL", rows[1][3])
        assertEquals("100", rows[1][4])
        assertEquals("99", rows[1][5])
        // 1% per second is 3600% per hour.
        assertEquals("3600.000000", rows[1][6])
    }

    @Test
    fun `charging on transitions`() {
        val input = listOf(
            "9,h,0:RESET:TIME:1432964300076",
            "9,h,2242,+ch",
            "9,h,64,-ch",
            "9,h,1000,+ch",
            "9,h,1500,-ch",
        ).joinToString("\n")

        val result = analyze(input)
        assertEquals(emptyList<String>(), result.errors)
        assertEquals(1, result.summaries.size)
        assertEquals(Dist(2, 1564, 1500), result.summaries[0].chargingOnSummary)
        assertEquals(
            listOf(
                listOf("Charging on", "bool", "1432964302318", "1432964302382", "true", ""),
                listOf("Charging on", "bool", "1432964303382", "1432964304882", "true", ""),
            ),
            csvRows(result.csv),
        )
    }

    @Test
    fun `negative first charging transition starts at the summary start`() {
        val input = listOf(
            "9,h,0:RESET:TIME:1432964300076",
            "9,h,64,-ch",
            "9,h,1000,+ch",
            "9,h,1500,-ch",
        ).joinToString("\n")

        val result = analyze(input)
        assertEquals(emptyList<String>(), result.errors)
        assertEquals(Dist(2, 1564, 1500), result.summaries[0].chargingOnSummary)
        assertEquals(
            listOf(
                listOf("Charging on", "bool", "1432964300076", "1432964300140", "true", ""),
                listOf("Charging on", "bool", "1432964301140", "1432964302640", "true", ""),
            ),
            csvRows(result.csv),
        )
    }

    @Test
    fun `two consecutive negative charging transitions report an error`() {
        val input = listOf(
            "9,h,0:RESET:TIME:1432964300076",
            "9,h,218,-ch",
            "9,h,2021,-ch",
        ).joinToString("\n")

        val result = analyze(input)
        assertEquals(1, result.errors.size)
        assertEquals(
            "** Error in 9,h,2021,-ch with -ch : " +
                "two negative transitions for \"Charging on\":\"-\"",
            result.errors[0],
        )
        assertEquals(Dist(1, 218, 218), result.summaries[0].chargingOnSummary)
        assertEquals(
            listOf(
                listOf("Charging on", "bool", "1432964300076", "1432964300294", "true", ""),
            ),
            csvRows(result.csv),
        )
    }

    @Test
    fun `partial wakelock parse including a missing positive transition`() {
        val input = listOf(
            "9,0,i,vers,11,116,LMY06B,LMY06B",
            "9,hsp,17,1010054,\"com.google.android.apps.docs.editors.punch/com.google/noogler@google.com\"",
            "9,h,0:RESET:TIME:1422620451417",
            "9,h,1000,+w=17",
            "9,h,10000,-w",
            "9,h,2000,-w", // Negative transition without a positive one.
            "9,h,1000,+w=17", // Positive transition without a negative one.
            "9,h,3,+r", // Gives the previous +w a non-zero duration.
        ).joinToString("\n")

        val result = analyze(input)
        assertEquals(emptyList<String>(), result.errors)
        assertEquals(1, result.summaries.size)
        val summary = result.summaries[0]
        assertEquals(1422620451417, summary.startTimeMs)
        assertEquals(1422620465420, summary.endTimeMs)
        assertEquals(
            mapOf(
                "\"com.google.android.apps.docs.editors.punch/com.google/XXX@google.com\"" to
                    Dist(2, 10003, 10000),
            ),
            summary.wakeLockSummary,
        )
        assertEquals(
            listOf(
                listOf(
                    "Partial wakelock",
                    "service",
                    "1422620452417",
                    "1422620462417",
                    "com.google.android.apps.docs.editors.punch/com.google/XXX@google.com",
                    "",
                ),
                listOf(
                    "Partial wakelock",
                    "error",
                    "1422620464417",
                    "1422620464417",
                    "missing corresponding +w",
                    "",
                ),
                listOf(
                    "Partial wakelock",
                    "service",
                    "1422620465417",
                    "1422620465420",
                    "com.google.android.apps.docs.editors.punch/com.google/XXX@google.com",
                    "",
                ),
                listOf(
                    "CPU running",
                    "string",
                    "1422620465420",
                    "1422620465420",
                    "1422620465420~Unknown wakeup reason",
                    "",
                ),
            ),
            csvRows(result.csv),
        )
    }

    @Test
    fun `in progress screen and sync events are summarized`() {
        val input = listOf(
            "9,hsp,0,10086,\"gmail-ls/com.google/XXX@gmail.com\"",
            "9,h,0:RESET:TIME:1422681992795",
            "9,h,4321,-Esy=0", // Sync in progress at the beginning.
            "9,h,111,-S", // Screen was on at the beginning.
            "9,h,4000,+Esy=0",
            "9,h,1234,-Esy=0",
            "9,h,1000,+S", // In-progress screen on.
            "9,h,9876,+Esy=0", // In-progress sync at the end, zero duration.
        ).joinToString("\n")

        val result = analyze(input)
        assertEquals(emptyList<String>(), result.errors)
        assertEquals(1, result.summaries.size)
        val summary = result.summaries[0]
        assertEquals(Dist(3, 5555, 4321), summary.totalSyncSummary)
        assertEquals(Dist(2, 14308, 9876), summary.screenOnSummary)
    }

    @Test
    fun `battery level timeline rows are emitted`() {
        val input = listOf(
            "9,h,0:RESET:TIME:1000000000000",
            "9,h,0,Bl=100,Bs=d",
            "9,h,1000,Bl=99",
        ).joinToString("\n")

        val result = analyze(input)
        val rows = csvRows(result.csv).filter { it[0] == HistoryMetrics.BATTERY_LEVEL }
        assertEquals(
            listOf(
                listOf("Battery Level", "int", "1000000000000", "1000000001000", "100", ""),
                listOf("Battery Level", "int", "1000000001000", "1000000001000", "99", ""),
            ),
            rows,
        )
    }

    @Test
    fun `connectivity transitions are summarized and emitted`() {
        val input = listOf(
            "9,0,i,vers,11,116,LMY06B,LMY06B",
            "9,hsp,3,1,\"CONNECTED\"",
            "9,hsp,28,1,\"DISCONNECTED\"",
            "9,hsp,30,0,\"CONNECTED\"",
            "9,hsp,46,0,\"DISCONNECTED\"",
            "9,h,0:RESET:TIME:1422620451417",
            "9,h,1000,Ecn=3",
            "9,h,2000,Ecn=28",
            "9,h,2000,Ecn=28",
            "9,h,1000,Ecn=30",
            "9,h,1000,Ecn=46",
            "9,h,1000,Ecn=3",
            "9,h,1000,Ecn=28",
        ).joinToString("\n")

        val result = analyze(input)
        assertEquals(emptyList<String>(), result.errors)
        assertEquals(1, result.summaries.size)
        val connectivity = result.summaries.first().connectivitySummary
        assertEquals(Dist(2, 3000, 2000), connectivity["TYPE_WIFI:\"CONNECTED\""])
        assertEquals(Dist(1, 1000, 1000), connectivity["TYPE_MOBILE:\"CONNECTED\""])

        val rows = csvRows(result.csv).filter { it[0] == "Network connectivity" }
        assertEquals(3, rows.size)
    }

    @Test
    fun `doze transitions produce timeline rows and summaries`() {
        val input = listOf(
            "9,0,i,vers,15,130,LMY06B,LMY06B",
            "9,h,0:RESET:TIME:1422620500000",
            "9,h,1000,-di",
            "9,h,6000,+di",
            "9,h,5000,-di",
            "9,h,4000,+di",
            "9,h,50,+Wl",
        ).joinToString("\n")

        val result = analyze(input)
        assertEquals(emptyList<String>(), result.errors)
        assertEquals(1, result.summaries.size)

        val idle = result.summaries.first().idleModeSummary
        assertEquals(Dist(3, 6050, 5000), idle["full"])
        assertEquals(Dist(2, 10000, 6000), idle["off"])

        val dozeRows = csvRows(result.csv).filter { it[0] == "Doze" }
        assertEquals(5, dozeRows.size)
        assertTrue(result.csv.contains("Doze,string"))
    }

    @Test
    fun `jobs and temp white list are summarized`() {
        val input = listOf(
            "9,hsp,17,1010054,\"com.google.android.apps.docs.editors.punch/com.google/noogler@google.com\"",
            "9,hsp,30,1000,\"job/service\"",
            "9,hsp,31,1000,\"177eb81 android.safetycenter.action.REFRESH_SAFETY_SOURCES/u0\"",
            "9,h,0:RESET:TIME:1000000000000",
            "9,h,1000,+Ejb=30",
            "9,h,2000,-Ejb=30",
            "9,h,1000,+Etw=31",
            "9,h,3000,-Etw=31",
        ).joinToString("\n")

        val result = analyze(input)
        assertEquals(emptyList<String>(), result.errors)
        val summary = result.summaries[0]
        assertEquals(mapOf("\"job/service\"" to Dist(1, 2000, 2000)), summary.scheduledJobSummary)
        assertEquals(
            mapOf(
                "\"177eb81 android.safetycenter.action.REFRESH_SAFETY_SOURCES/u0\"" to
                    Dist(1, 3000, 3000),
            ),
            summary.tmpWhiteListSummary,
        )
    }

    @Test
    fun `top app follows screen transitions`() {
        val input = listOf(
            "9,hsp,2,10191,\"com.example.launcher\"",
            "9,h,0:RESET:TIME:1000000000000",
            "9,h,1000,+S",
            "9,h,0,+Etp=2",
            "9,h,5000,-S",
        ).joinToString("\n")

        val result = analyze(input)
        assertEquals(emptyList<String>(), result.errors)
        val summary = result.summaries[0]
        assertEquals(mapOf("\"com.example.launcher\"" to Dist(1, 5000, 5000)), summary.topApplicationSummary)
        val topRows = csvRows(result.csv).filter { it[0] == HistoryMetrics.TOP }
        // One row when the top app starts and is closed by the screen-off.
        assertEquals(1, topRows.size)
        assertTrue(topRows.all { it[1] == "service" && it[5] == "10191" })
    }
}
