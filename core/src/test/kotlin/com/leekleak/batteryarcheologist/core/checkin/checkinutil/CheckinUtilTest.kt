// Copyright 2016 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Kotlin tests for the checkinutil port.

package com.leekleak.batteryarcheologist.core.checkin.checkinutil

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckinUtilTest {
    @Test
    fun `csv keeps commas inside quoted fields`() {
        val rows = CheckinUtil.parseCsv(
            "9,0,i,uid,1000,\"com.example,app\"\n" +
                "9,0,l,wl,\"lock,with,commas\",1,2",
        )

        assertEquals(2, rows.size)
        assertEquals("com.example,app", rows[0][5])
        assertEquals("lock,with,commas", rows[1][4])
        assertEquals("2", rows[1][6])
    }

    @Test
    fun `csv handles escaped and lazy quotes`() {
        val rows = CheckinUtil.parseCsv(
            "9,0,l,wl,\"name with \"\"quotes\"\"\",0\n" +
                "9,0,l,wl,ba\"re quote,1",
        )

        assertEquals("name with \"quotes\"", rows[0][4])
        assertEquals("ba\"re quote", rows[1][4])
    }

    @Test
    fun `csv trims leading space and fields`() {
        val rows = CheckinUtil.parseCsv(" 9, 0 , i , vers , 36 \n")

        assertEquals(listOf("9", "0", "i", "vers", "36"), rows.single())
    }

    @Test
    fun `extractCsv keeps only info and since charged rows with enough fields`() {
        val raw = """
            9,0,i,vers,36,216,BUILD,BUILD
            9,hsp,0,1000,"com.example"
            9,h,0:RESET:TIME:1700000000000
            9,0,l,bt,0,1,2,3,4,5,6,7
            9,0,x,bt,0,1,2,3
        """.trimIndent()

        val rows = CheckinUtil.extractCsv(raw)

        assertEquals(2, rows.size)
        assertEquals("vers", rows[0][3])
        assertEquals("bt", rows[1][3])
    }

    @Test
    fun `createBatteryReport takes the build id from the vers row`() {
        val raw = """
            9,0,i,vers,36,216,BE4B.251210.005,BE4B.251210.005
            9,0,i,uid,1000,android
            9,hsp,0,1000,"com.example"
            9,0,l,bt,0,1,2,3,4,5,6,7
        """.trimIndent()

        val report = CheckinUtil.createBatteryReport(raw)

        assertEquals("BE4B.251210.005", report.buildId)
        // vers, uid and bt are all info/SINCE_CHARGED rows; the history string-pool row is not.
        assertEquals(3, report.rawBatteryStats.size)
    }

    @Test
    fun `IntCounter accumulates values`() {
        val counter = IntCounter()
        counter.count("a", 2)
        counter.count("b", 3)

        assertEquals(5, counter.value)
    }

    @Test
    fun `PrefixCounter prefixes names and replaces null characters`() {
        val seen = ArrayList<String>()
        val counter = PrefixCounter("prefix") { name, _ -> seen += name }

        counter.count("a\u0000b", 1)

        assertEquals(listOf("prefix-anullb"), seen)
    }

    @Test
    fun `csv skips blank lines but preserves empty fields`() {
        val rows = CheckinUtil.parseCsv("a,b\n\nc,\n")

        assertEquals(2, rows.size)
        assertTrue(rows[1][0] == "c")
        assertEquals("", rows[1][1])
    }
}
