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
 * Port of TestOverflow from
 * third_party/battery-historian/parseutils/parseutils_test.go.
 */

package com.leekleak.batteryarcheologist.core.history.parseutils

import com.leekleak.batteryarcheologist.core.history.csv.parseCsvRecords
import org.junit.Assert.assertEquals
import org.junit.Test

class ParseutilsOverflowTest {
    @Test
    fun `battery levels continue after overflow while summaries stop`() {
        val input = listOf(
            "9,0,i,vers,12,116,LVX72L,LVY29G",
            "9,hsp,94,10011,\"com.google.android.gms.people/com.google/test@google.com\"",
            "9,h,0:RESET:TIME:1400000030000", // fixTimeline changes this to 1400000000000.
            "9,h,0,Bl=52",
            "9,h,2000,-Esy=94",
            "9,h,5000,+Esy=94",
            "9,h,0:*OVERFLOW*",
            "9,h,1000,Bl=51,Bt=236,Bv=3820,Pss=3,w=14,wr=18,+Esy=10",
            "9,h,2000,Bl=50,Bv=3791,-w=134,+Pr,Pcn=lte,Pss=2,wr=18,-Esy=94",
            "9,h,0:TIME:1400000010000",
            "9,h,2000,Bl=49,Bs=c,Bh=d,Bp=a,Bt=282,Bv=3706,+r,+BP,wr=203",
            "9,h,0:*OVERFLOW*",
            "9,h,3000,Bl=48,Bt=294,Bv=3785,-w=13,-s,-a,-S,Pss=3,Sb=0,Wss=2,Wsp=compl,wr=18,-Esy=94",
        ).joinToString("\n")

        val expectedCsv = listOf(
            listOf("Battery Level", "int", "1400000000000", "1400000008000", "52", ""),
            listOf(
                "SyncManager",
                "service",
                "1400000000000",
                "1400000002000",
                "com.google.android.gms.people/com.google/test@google.com",
                "10011",
            ),
            listOf(
                "SyncManager",
                "service",
                "1400000007000",
                "1400000007000",
                "com.google.android.gms.people/com.google/test@google.com",
                "10011",
            ),
            listOf("Battery Level", "int", "1400000008000", "1400000010000", "51", ""),
            listOf("Battery Level", "int", "1400000010000", "1400000012000", "50", ""),
            listOf("Battery Level", "int", "1400000012000", "1400000015000", "49", ""),
            listOf("Battery Level", "int", "1400000015000", "1400000015000", "48", ""),
        )

        val buffer = StringBuilder()
        val report = analyzeHistory(
            csvWriter = buffer,
            history = input,
            format = HistorySummaryFormat.TOTAL_TIME,
            pum = PackageUIDMapping(),
            scrubPII = false,
        )

        assertEquals(1400000007000L, report.overflowMs)
        assertEquals(1, report.summaries.size)
        val summary = report.summaries[0]
        assertEquals(1400000000000L, summary.startTimeMs)
        assertEquals(1400000007000L, summary.endTimeMs)
        assertEquals(
            mapOf(
                "\"com.google.android.gms.people/com.google/test@google.com\"" to
                    Dist(2, 2000, 2000),
            ),
            summary.perAppSyncSummary,
        )

        val actual = parseCsvRecords(buffer.toString()).filter { it.first() != "metric" }
        assertEquals(
            expectedCsv.map { it.joinToString(",") }.sorted(),
            actual.map { it.joinToString(",") }.sorted(),
        )
    }
}
