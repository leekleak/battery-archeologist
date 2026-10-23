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
//
// Kotlin port of third_party/battery-historian/historianutils/historianutils_test.go,
// with a few extra cases for the Kotlin helpers.

package com.leekleak.batteryarcheologist.core.historianutils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Assert.assertThrows

class HistorianUtilsTest {
    @Test
    fun `scrubs PII from identifiers`() {
        val tests = mapOf(
            "pureemail@google.com" to "XXX@google.com",
            "hyphen-ated@google.com" to "XXX@google.com",
            "under_score@google.com" to "XXX@google.com",
            "with.dot@google.com" to "XXX@google.com",
            "notAn-email" to "notAn-email",
            "incomplete@" to "incomplete@",
            // There are some wakelocks with this name format.
            "wake.lock@1a23b4" to "wake.lock@1a23b4",
            "com.android.calendar/com.google/noogley@google.com" to
                "com.android.calendar/com.google/XXX@google.com",
            "lot-o-prefixes/with//com.google/noogley@google.com" to
                "lot-o-prefixes/with//com.google/XXX@google.com",
            // Syncs often have the PII at the end, though not always in email form.
            "*sync*/com.android.contacts/com.google/noogler@google.com" to
                "*sync*/com.android.contacts/com.google/XXX@google.com",
            // There can be spaces in the string.
            "*sync*/com.app1.android.conversations/com.app1.android.account/Mr. Noogler" to
                "*sync*/com.app1.android.conversations/com.app1.android.account/XXX",
            "*sync*/com.app2.android.provider.App2Provider/com.app2.android.auth.login/noogler314159" to
                "*sync*/com.app2.android.provider.App2Provider/com.app2.android.auth.login/XXX",
        )

        for ((input, want) in tests) {
            assertEquals("ScrubPII($input)", want, HistorianUtils.scrubPII(input))
        }
    }

    @Test
    fun `parses durations with days`() {
        assertEquals(259200001L, HistorianUtils.parseDurationWithDays("3d1ms"))
        assertEquals(259200000L, HistorianUtils.parseDurationWithDays("3d"))
        assertThrows(IllegalArgumentException::class.java) {
            HistorianUtils.parseDurationWithDays("d")
        }
        assertThrows(IllegalArgumentException::class.java) {
            HistorianUtils.parseDurationWithDays("")
        }
        assertEquals(3600000L, HistorianUtils.parseDurationWithDays("1h"))
    }

    @Test
    fun `parses Go duration syntax`() {
        assertEquals(5400000L, HistorianUtils.parseDurationWithDays("1h30m"))
        assertEquals(5400000L, HistorianUtils.parseDurationWithDays("1.5h"))
        assertEquals(1800000L, HistorianUtils.parseDurationWithDays(".5h"))
        assertEquals(-3600000L, HistorianUtils.parseDurationWithDays("-1h"))
        assertEquals(500L, HistorianUtils.parseDurationWithDays("500ms"))
        assertEquals(0L, HistorianUtils.parseDurationWithDays("1us"))
        assertEquals(90061001L, HistorianUtils.parseDurationWithDays("1d1h1m1s1ms"))
        assertThrows(IllegalArgumentException::class.java) {
            HistorianUtils.parseDurationWithDays("1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            HistorianUtils.parseDurationWithDays("1x")
        }
        assertThrows(IllegalArgumentException::class.java) {
            HistorianUtils.parseDurationWithDays("1h-1m")
        }
    }

    @Test
    fun `named group helper trims and maps captures`() {
        val regex = Regex("""^(?<first>\S+)\s+(?<second>\S+)$""")

        val result = HistorianUtils.subexpNames(regex, "  alpha   beta  ")

        assertEquals("alpha   beta", result?.get(""))
        assertEquals("alpha", result?.get("first"))
        assertEquals("beta", result?.get("second"))
        assertNull(HistorianUtils.subexpNames(regex, "only-one-token"))
    }

    @Test
    fun `named group helper handles unnamed groups and unmatched captures`() {
        val regex = Regex("""(?:prefix)-(unnamed)-(?<value>\d+)(?:-(?<optional>\S+))?""")

        val result = HistorianUtils.subexpNames(regex, "prefix-unnamed-42")!!

        // Unnamed groups share the empty key, matching Go's SubexpNames behaviour.
        // The last capture wins over the whole-match entry.
        assertEquals("unnamed", result[""])
        assertEquals("42", result["value"])
        assertEquals("", result["optional"])
    }

    @Test
    fun `gzip helpers round trip`() {
        val input = "9,0,i,vers,36,305,K,L\n".repeat(100).toByteArray()

        val compressed = HistorianUtils.gzipCompress(input)

        assertEquals(0x1F, compressed[0].toInt() and 0xFF)
        assertEquals(0x8B, compressed[1].toInt() and 0xFF)
        assertArrayEquals(input, HistorianUtils.gzipDecompress(compressed))
    }

    @Test
    fun `errors are newline delimited`() {
        val errors = listOf(RuntimeException("boom"), IllegalStateException("bang"))

        assertEquals("boom\nbang\n", HistorianUtils.errorsToString(errors))
    }
}
