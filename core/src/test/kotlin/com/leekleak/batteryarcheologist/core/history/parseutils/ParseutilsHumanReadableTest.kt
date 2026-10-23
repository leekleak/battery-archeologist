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
 * Port of TestParseHistory from
 * third_party/battery-historian/parseutils/human_readable_test.go, plus
 * formatDelta edge cases.
 */

package com.leekleak.batteryarcheologist.core.history.parseutils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ParseutilsHumanReadableTest {
    @Test
    fun `formatDelta matches the human readable battery history`() {
        assertEquals("0", formatDelta(0))
        assertEquals("+100ms", formatDelta(100))
        assertEquals("+1s000ms", formatDelta(1000))
        assertEquals("+1m00s001ms", formatDelta(60001))
        assertEquals("+1h00m50s002ms", formatDelta(3650002))
        assertEquals("+1d00h00m00s000ms", formatDelta(86400000))
        assertEquals("+2d05h41m40s003ms", formatDelta(193300003))
    }

    @Test
    fun `formatDelta rejects negative values`() {
        assertThrows(IllegalArgumentException::class.java) { formatDelta(-1) }
    }

    @Test
    fun `Go duration formatting`() {
        assertEquals("0s", formatGoDurationMillis(0))
        assertEquals("1.5s", formatGoDurationMillis(1500))
        assertEquals("1.005s", formatGoDurationMillis(1005))
        assertEquals("1m30s", formatGoDurationMillis(90000))
        assertEquals("1m0s", formatGoDurationMillis(60000))
        assertEquals("1h0m0s", formatGoDurationMillis(3600000))
        assertEquals("1h1m1.001s", formatGoDurationMillis(3661001))
    }

    @Test
    fun `shutdown event maps timestamps to cumulative deltas`() {
        val input = listOf(
            "9,hsp,3,10010,\"com.google.android.gms/.gcm.nts.TaskExecutionService\"",
            "9,h,0:RESET:TIME:1424553841351",
            "9,h,334,+Ejb=3",
            "9,h,22,-Ejb=3",
            "9,h,2000:SHUTDOWN",
            "9,h,23:START",
            "9,h,0:TIME:1424664683701",
            "9,h,10000,Bl=85,Bs=d,Bh=g,Bp=n,Bt=283,Bv=4057,+r,wr=92",
        ).joinToString("\n")

        val result = HistoryParser.analyze(input, HistorySummaryFormat.TOTAL_TIME, emptyList())
        assertEquals(
            mapOf(
                "1424553841351" to "0",
                "1424553841685" to "+334ms",
                "1424553841707" to "+356ms",
                "1424553843707" to "+2s356ms",
                "1424664683701" to "+2s379ms",
                "1424664693701" to "+12s379ms",
            ),
            result.timeToDelta,
        )
    }

    @Test
    fun `multiple time statements are normalized`() {
        val input = listOf(
            "9,hsp,176,10117,\"com.northpark.drinkwater",
            "9,h,0:RESET:TIME:1439187859870",
            "9,h,1000,Wsp=scan",
            "9,h,1000:TIME:1424664752248",
            "9,h,4,Wsp=asced",
        ).joinToString("\n")

        val result = HistoryParser.analyze(input, HistorySummaryFormat.TOTAL_TIME, emptyList())
        assertEquals(
            mapOf(
                "1424664750248" to "0",
                "1424664751248" to "+1s000ms",
                "1424664752248" to "+2s000ms",
                "1424664752252" to "+2s004ms",
            ),
            result.timeToDelta,
        )
    }
}
