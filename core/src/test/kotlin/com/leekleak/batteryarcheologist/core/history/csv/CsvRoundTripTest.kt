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
 * Round-trip tests for the Kotlin port of third_party/battery-historian/csv.
 */

package com.leekleak.batteryarcheologist.core.history.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvRoundTripTest {
    @Test
    fun `state output parses back into events`() {
        val out = StringBuilder()
        val state = State(out, printHeader = true)
        val entries = listOf(
            Entry(
                desc = "Mobile network type",
                startMs = 1422620452417,
                type = "string",
                value = "hspa",
            ),
            Entry(
                desc = "Charging status",
                startMs = 1422620452417,
                type = "string",
                value = "c",
            ),
            Entry(
                desc = "Wakelock_in",
                startMs = 1422620456417,
                type = "service",
                value = "com.google.android.apps.docs/com.google/noogler@google.com",
                opt = "10051",
            ),
            Entry(
                desc = "Custom",
                startMs = 5,
                type = "string",
                value = "a,\"b\"\n c",
            ),
        )
        entries.forEach(state::printInstantEvent)

        val result = extractEvents(out.toString())

        assertTrue(result.errors.toString(), result.errors.isEmpty())
        val expected = entries.associate { entry ->
            entry.desc to listOf(
                Event(
                    type = entry.type,
                    startMs = entry.startMs,
                    endMs = entry.startMs,
                    value = entry.value,
                    opt = entry.opt,
                ),
            )
        }
        assertEquals(expected, result.events)
    }

    @Test
    fun `start and end events survive a round trip`() {
        val out = StringBuilder()
        val state = State(out, printHeader = true)
        state.startEvent(
            Entry(
                desc = "Charging status",
                startMs = 1000,
                type = "string",
                value = "c",
            ),
        )
        state.endEvent("Charging status", "", 2000)

        val result = extractEvents(out.toString())

        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(
            mapOf(
                "Charging status" to listOf(
                    Event(type = "string", startMs = 1000, endMs = 2000, value = "c"),
                ),
            ),
            result.events,
        )
    }

    @Test
    fun `crlf input parses the same as lf input`() {
        val out = StringBuilder()
        val state = State(out, printHeader = true)
        state.printInstantEvent(
            Entry(
                desc = "Custom",
                startMs = 5,
                type = "string",
                value = "a,\"b\"\n c",
            ),
        )

        val lfResult = extractEvents(out.toString())
        val crlfResult = extractEvents(out.toString().replace("\n", "\r\n"))

        assertEquals(lfResult, crlfResult)
        assertEquals(
            "a,\"b\"\n c",
            crlfResult.events.getValue("Custom").single().value,
        )
    }

    @Test
    fun `fields are trimmed and metrics are filtered`() {
        val input = Csv.FILE_HEADER + "\n" +
            "Mobile network type, string , 10, 20, hspa , \n"

        val all = extractEvents(input)
        assertTrue(all.errors.toString(), all.errors.isEmpty())
        assertEquals(
            mapOf(
                "Mobile network type" to listOf(
                    Event(type = "string", startMs = 10, endMs = 20, value = "hspa"),
                ),
            ),
            all.events,
        )

        val filtered = extractEvents(input, metrics = listOf("Charging status"))
        assertEquals(
            mapOf("Charging status" to emptyList<Event>()),
            filtered.events,
        )
    }
}
