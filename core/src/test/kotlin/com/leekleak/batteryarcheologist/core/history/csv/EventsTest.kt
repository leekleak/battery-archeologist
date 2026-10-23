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
 * Kotlin port of third_party/battery-historian/csv/events_test.go.
 */

package com.leekleak.batteryarcheologist.core.history.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventsTest {
    @Test
    fun `extract events matches multiple metrics`() {
        val input = listOf(
            Csv.FILE_HEADER,
            "Mobile network type,string,1422620452417,1422620453917,hspa,",
            "Charging status,string,1422620452417,1422620453917,c,",
            "Mobile network type,string,1422620453917,1422620454417,lte,",
            """Wakelock_in,service,1422620456417,1422620458417,"com.google.android.apps.docs/com.google/noogler@google.com",10051""",
            "Reboot,bool,1422620454417,1430000000000,true,",
            """CPU running,string,1430000001000,1430000003000,"1430000001000~57:qcom,smd-modem:200:qcom,smd-rpm|1430000003000~Abort:Some devices failed to suspend",""",
            """CPU running,string,1430000003000,1430000003000,"1430000003000~57:qcom,smd-modem:200:qcom,smd-rpm",""",
        ).joinToString("\n")

        val result = extractEvents(
            input,
            metrics = listOf("CPU running", "Charging status", "Wakelock_in"),
        )

        assertTrue(result.errors.toString(), result.errors.isEmpty())
        val expected: Map<String, List<Event>> = mapOf(
            "CPU running" to listOf(
                Event(
                    type = "string",
                    startMs = 1430000001000,
                    endMs = 1430000003000,
                    value = "1430000001000~57:qcom,smd-modem:200:qcom,smd-rpm|" +
                        "1430000003000~Abort:Some devices failed to suspend",
                ),
                Event(
                    type = "string",
                    startMs = 1430000003000,
                    endMs = 1430000003000,
                    value = "1430000003000~57:qcom,smd-modem:200:qcom,smd-rpm",
                ),
            ),
            "Charging status" to listOf(
                Event(
                    type = "string",
                    startMs = 1422620452417,
                    endMs = 1422620453917,
                    value = "c",
                ),
            ),
            "Wakelock_in" to listOf(
                Event(
                    type = "service",
                    startMs = 1422620456417,
                    endMs = 1422620458417,
                    value = "com.google.android.apps.docs/com.google/noogler@google.com",
                    opt = "10051",
                ),
            ),
        )
        assertEquals(expected, result.events)
    }

    @Test
    fun `extract events returns empty lists for non matching metrics`() {
        val input = listOf(
            Csv.FILE_HEADER,
            "Mobile network type,string,1422620452417,1422620453917,hspa,",
            "Charging status,string,1422620452417,1422620453917,c,",
            "Mobile network type,string,1422620453917,1422620454417,lte,",
            "Reboot,bool,1422620454417,1430000000000,true,",
            """CPU running,string,1430000001000,1430000003000,"1430000001000~57:qcom,smd-modem:200:qcom,smd-rpm|1430000003000~Abort:Some devices failed to suspend",""",
            """CPU running,string,1430000003000,1430000003000,"1430000003000~57:qcom,smd-modem:200:qcom,smd-rpm",""",
        ).joinToString("\n")

        val result = extractEvents(input, metrics = listOf("Temperature", "Level"))

        assertTrue(result.errors.toString(), result.errors.isEmpty())
        val expected: Map<String, List<Event>> = mapOf(
            "Temperature" to emptyList(),
            "Level" to emptyList(),
        )
        assertEquals(expected, result.events)
    }

    @Test
    fun `extract events matches all events when metrics is null`() {
        val input = listOf(
            Csv.FILE_HEADER,
            "Mobile network type,string,1422620452417,1422620453917,hspa,",
            "Charging status,string,1422620452417,1422620453917,c,",
        ).joinToString("\n")

        val result = extractEvents(input)

        assertTrue(result.errors.toString(), result.errors.isEmpty())
        val expected: Map<String, List<Event>> = mapOf(
            "Mobile network type" to listOf(
                Event(
                    type = "string",
                    startMs = 1422620452417,
                    endMs = 1422620453917,
                    value = "hspa",
                ),
            ),
            "Charging status" to listOf(
                Event(
                    type = "string",
                    startMs = 1422620452417,
                    endMs = 1422620453917,
                    value = "c",
                ),
            ),
        )
        assertEquals(expected, result.events)
    }

    @Test
    fun `extract events collects parse errors and keeps valid records`() {
        val input = listOf(
            Csv.FILE_HEADER,
            "Mobile network type,string,1422620452417,1422620453917,hspa,",
            "Reboot,bool,notanumber,1430000000000,1430000000000,",
            "Reboot,bool,1422620454417,1430000000000,",
            "Charging status,string,1422620452417,1422620453917,c,",
            "Mobile network type,string,1422620453917,1422620454417,lte,",
            "Reboot,bool,1422620454417,1430000000000,true,",
        ).joinToString("\n")

        val result = extractEvents(input, metrics = listOf("Reboot"))

        assertEquals(
            listOf(
                "record 2: strconv.ParseInt: parsing \"notanumber\": invalid syntax",
                "record 3: non matching [Reboot bool 1422620454417 1430000000000 ], len was 5",
            ),
            result.errors,
        )
        val expected: Map<String, List<Event>> = mapOf(
            "Reboot" to listOf(
                Event(
                    type = "bool",
                    startMs = 1422620454417,
                    endMs = 1430000000000,
                    value = "true",
                ),
            ),
        )
        assertEquals(expected, result.events)
    }

    @Test
    fun `extract events handles a header only input`() {
        val result = extractEvents(Csv.FILE_HEADER + "\n")

        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(emptyMap<String, List<Event>>(), result.events)
    }

    @Test
    fun `extract events reports empty input like the go parser`() {
        val result = extractEvents("")

        assertEquals(emptyMap<String, List<Event>>(), result.events)
        assertEquals(listOf("nil result generated by ParseCSV"), result.errors)
    }

    @Test
    fun `merge events leaves non overlapping events unchanged`() {
        val input = listOf(event(0, 1), event(2, 3), event(4, 5), event(8, 10))
        assertEquals(input, mergeEvents(input))
    }

    @Test
    fun `merge events collapses events contained in one big event`() {
        val input = listOf(
            event(0, 10),
            event(0, 2),
            event(4, 5),
            event(7, 12),
            event(1, 3),
        )
        assertEquals(listOf(event(0, 12)), mergeEvents(input))
    }

    @Test
    fun `merge events joins partially overlapping events`() {
        assertEquals(
            listOf(event(0, 8)),
            mergeEvents(listOf(event(0, 5), event(3, 8))),
        )
        assertEquals(
            listOf(event(2, 8)),
            mergeEvents(listOf(event(4, 8), event(2, 5))),
        )
    }

    @Test
    fun `merge events joins events connected by their edges`() {
        val input = listOf(event(1, 4), event(4, 8), event(8, 10))
        assertEquals(listOf(event(1, 10)), mergeEvents(input))
    }

    @Test
    fun `merge events handles mixed overlaps`() {
        val input = listOf(
            event(0, 1),
            event(3, 4),
            event(5, 10),
            event(6, 8),
            event(7, 9),
            event(12, 16),
            event(11, 15),
            event(16, 18),
            event(20, 22),
            event(26, 29),
            event(25, 27),
            event(30, 33),
        )
        val expected = listOf(
            event(0, 1),
            event(3, 4),
            event(5, 10),
            event(11, 18),
            event(20, 22),
            event(25, 29),
            event(30, 33),
        )
        assertEquals(expected, mergeEvents(input))
    }

    @Test
    fun `merge events drops extra fields from merged intervals`() {
        val first = Event(
            type = "string",
            startMs = 0,
            endMs = 5,
            value = "a",
            opt = "1",
            appName = "app",
        )
        val second = Event(type = "string", startMs = 3, endMs = 8, value = "b")

        assertEquals(listOf(Event(startMs = 0, endMs = 8)), mergeEvents(listOf(first, second)))
    }

    @Test
    fun `merge events returns an empty list for empty input`() {
        assertEquals(emptyList<Event>(), mergeEvents(emptyList()))
    }

    private fun event(start: Long, end: Long) = Event(startMs = start, endMs = end)
}
