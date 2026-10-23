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
 * Tests for the Kotlin port of third_party/battery-historian/csv/csv.go.
 */

package com.leekleak.batteryarcheologist.core.history.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvStateTest {
    @Test
    fun `writes the file header when requested`() {
        val out = StringBuilder()

        State(out, printHeader = true)

        assertEquals(Csv.FILE_HEADER + "\n", out.toString())
    }

    @Test
    fun `does not write the file header by default`() {
        val out = StringBuilder()

        State(out)

        assertEquals("", out.toString())
    }

    @Test
    fun `accepts a null writer`() {
        val state = State(null, printHeader = true)

        state.printInstantEvent(
            Entry(desc = "Charging status", startMs = 100, type = "string", value = "c"),
        )
        state.printAllReset(200)
    }

    @Test
    fun `starts and ends an event`() {
        val out = StringBuilder()
        val state = State(out)
        val charging = Entry(
            desc = "Charging status",
            startMs = 1000,
            type = "string",
            value = "c",
        )

        state.startEvent(charging)
        state.startEvent(charging)
        state.endEvent("Charging status", "", 2000)
        state.endEvent("Charging status", "", 3000)

        assertEquals("Charging status,string,1000,2000,c,\n", out.toString())
    }

    @Test
    fun `ending an inactive event does nothing`() {
        val out = StringBuilder()
        val state = State(out)

        state.endEvent("Charging status", "", 2000)
        state.printAllReset(3000)

        assertEquals("", out.toString())
    }

    @Test
    fun `second add prints the active entry and starts a new interval`() {
        val out = StringBuilder()
        val state = State(out)

        state.addEntry(
            "Mobile network type",
            Entry(desc = "Mobile network type", startMs = 1000, type = "string", value = "hspa"),
            1000,
        )
        state.addEntry(
            "Mobile network type",
            Entry(desc = "Mobile network type", startMs = 2000, type = "string", value = "lte"),
            2000,
        )
        state.addEntry(
            "Mobile network type",
            Entry(desc = "Mobile network type", startMs = 3000, type = "string", value = "lte"),
            3000,
        )
        state.addEntry(
            "Mobile network type",
            Entry(desc = "Mobile network type", startMs = 4000, type = "string", value = "umts"),
            4000,
        )

        assertEquals(
            "Mobile network type,string,1000,2000,hspa,\n" +
                "Mobile network type,string,3000,4000,lte,\n",
            out.toString(),
        )
    }

    @Test
    fun `entries with a zero start or empty value are ignored`() {
        val out = StringBuilder()
        val state = State(out)

        state.addEntry(
            "Charging status",
            Entry(desc = "Charging status", startMs = 0, type = "string", value = "c"),
            1000,
        )
        state.addEntry(
            "Charging status",
            Entry(desc = "Charging status", startMs = 1000, type = "string", value = ""),
            1000,
        )
        state.printAllReset(2000)

        assertEquals("", out.toString())
    }

    @Test
    fun `cpu running entries may have an empty value`() {
        val out = StringBuilder()
        val state = State(out)

        state.addEntry(Csv.CPU_RUNNING, cpuRunning(1000), 1000)
        state.printAllReset(2000)

        assertEquals(
            "CPU running,string,1000,2000,2000~Unknown wakeup reason,\n",
            out.toString(),
        )
    }

    @Test
    fun `prints and clears the reboot event`() {
        val out = StringBuilder()
        val state = State(out)

        assertFalse(state.hasRebootEvent())
        state.addRebootEvent(100)
        assertTrue(state.hasRebootEvent())
        state.printRebootEvent(200)
        assertFalse(state.hasRebootEvent())
        state.printRebootEvent(300)

        assertEquals("Reboot,bool,100,200,true,\n", out.toString())
    }

    @Test
    fun `strips surrounding quotes from values and opt`() {
        val out = StringBuilder()
        val state = State(out)

        state.printInstantEvent(
            Entry(
                desc = "Wakelock_in",
                startMs = 100,
                type = "service",
                value = "\"com.google.android.gm\"",
                opt = "\"10051\"",
            ),
        )

        assertEquals("Wakelock_in,service,100,100,com.google.android.gm,10051\n", out.toString())
    }

    @Test
    fun `escapes commas and quotes in csv output`() {
        val out = StringBuilder()
        val state = State(out)

        state.printInstantEvent(
            Entry(desc = "Custom", startMs = 1, type = "string", value = "a,\"b\""),
        )

        assertEquals("Custom,string,1,1,\"a,\"\"b\"\"\",\n", out.toString())
    }

    @Test
    fun `quotes fields that start with whitespace`() {
        val out = StringBuilder()
        val state = State(out)

        state.printInstantEvent(
            Entry(desc = "Custom", startMs = 1, type = "string", value = " hello"),
        )

        assertEquals("Custom,string,1,1,\" hello\",\n", out.toString())
    }

    @Test
    fun `print event writes the event fields`() {
        val out = StringBuilder()
        val state = State(out)

        state.printEvent(
            "Reboot",
            Event(type = "bool", startMs = 10, endMs = 20, value = "true"),
        )

        assertEquals("Reboot,bool,10,20,true,\n", out.toString())
    }

    @Test
    fun `add opt to entry updates an active entry`() {
        val out = StringBuilder()
        val state = State(out)

        state.addEntry(
            "Wakelock_in",
            Entry(
                desc = "Wakelock_in",
                startMs = 100,
                type = "service",
                value = "com.foo",
                identifier = "10051",
            ),
            100,
        )
        state.addOptToEntry(
            "Wakelock_in",
            Entry(desc = "Wakelock_in", identifier = "10051"),
            "42",
        )
        state.addOptToEntry(
            "Wakelock_in",
            Entry(desc = "Wakelock_in", identifier = "does-not-exist"),
            "99",
        )
        state.printActiveEvent("Wakelock_in", 200)

        assertEquals("Wakelock_in,service,100,200,com.foo,42\n", out.toString())
    }

    @Test
    fun `print active event prints only the requested metric`() {
        val out = StringBuilder()
        val state = State(out)

        state.addEntry(
            "Wakelock_in",
            Entry(
                desc = "Wakelock_in",
                startMs = 100,
                type = "service",
                value = "foo",
                identifier = "a",
            ),
            100,
        )
        state.addEntry(
            "Wakelock_in",
            Entry(
                desc = "Wakelock_in",
                startMs = 110,
                type = "service",
                value = "bar",
                identifier = "b",
            ),
            110,
        )
        state.addEntry(
            "Charging status",
            Entry(desc = "Charging status", startMs = 50, type = "string", value = "c"),
            50,
        )

        state.printActiveEvent("Wakelock_in", 200)
        state.printAllReset(300)

        assertEquals(
            "Wakelock_in,service,100,200,foo,\n" +
                "Wakelock_in,service,110,200,bar,\n" +
                "Charging status,string,50,300,c,\n",
            out.toString(),
        )
    }

    @Test
    fun `print all reset prints active entries and clears them`() {
        val out = StringBuilder()
        val state = State(out)

        state.addEntry(
            "Charging status",
            Entry(desc = "Charging status", startMs = 100, type = "string", value = "c"),
            100,
        )
        state.printAllReset(200)
        state.addEntry(
            "Charging status",
            Entry(desc = "Charging status", startMs = 300, type = "string", value = "d"),
            300,
        )
        state.printAllReset(400)

        assertEquals(
            "Charging status,string,100,200,c,\n" +
                "Charging status,string,300,400,d,\n",
            out.toString(),
        )
    }

    @Test
    fun `attaches wakeup reasons to a completed cpu running event`() {
        val out = StringBuilder()
        val state = State(out)

        state.addEntry(Csv.CPU_RUNNING, cpuRunning(1000), 1000)
        state.startWakeupReason("mcs", 1000)
        assertEquals(null, state.endWakeupReason("mcs", 1500))
        state.addEntry(Csv.CPU_RUNNING, cpuRunning(2000), 2000)
        state.addEntry(Csv.CPU_RUNNING, cpuRunning(3000), 3000)
        state.printAllReset(4000)

        assertEquals(
            "CPU running,string,1000,2000,1000~1500~mcs,\n" +
                "CPU running,string,3000,4000,4000~Unknown wakeup reason,\n",
            out.toString(),
        )
    }

    @Test
    fun `strips quotes from wakeup reason names`() {
        val out = StringBuilder()
        val state = State(out)

        state.addEntry(Csv.CPU_RUNNING, cpuRunning(1000), 1000)
        state.startWakeupReason("\"foo\"", 1000)
        state.endWakeupReason("\"foo\"", 1500)
        state.addEntry(Csv.CPU_RUNNING, cpuRunning(2000), 2000)
        state.printAllReset(2000)

        assertEquals("CPU running,string,1000,2000,1000~1500~foo,\n", out.toString())
    }

    @Test
    fun `joins multiple wakeup reasons with a pipe`() {
        val out = StringBuilder()
        val state = State(out)

        state.addEntry(Csv.CPU_RUNNING, cpuRunning(1000), 1000)
        state.startWakeupReason("a", 1000)
        state.endWakeupReason("a", 1500)
        state.startWakeupReason("b", 1600)
        state.endWakeupReason("b", 1800)
        state.addEntry(Csv.CPU_RUNNING, cpuRunning(2000), 2000)
        state.printAllReset(2000)

        assertEquals(
            "CPU running,string,1000,2000,1000~1500~a|1600~1800~b,\n",
            out.toString(),
        )
    }

    @Test
    fun `unknown wakeup reason starts when the running event started`() {
        val out = StringBuilder()
        val state = State(out)

        state.addEntry(Csv.CPU_RUNNING, cpuRunning(1000), 1000)
        state.addEntry(Csv.CPU_RUNNING, cpuRunning(2000), 2000)
        state.printAllReset(3000)

        assertEquals(
            "CPU running,string,1000,2000,1000~2000~Unknown wakeup reason,\n",
            out.toString(),
        )
    }

    @Test
    fun `wakeup reasons arriving after the running event are still attached`() {
        val out = StringBuilder()
        val state = State(out)

        state.addEntry(Csv.CPU_RUNNING, cpuRunning(1000), 1000)
        state.addEntry(Csv.CPU_RUNNING, cpuRunning(2000), 2000)
        assertEquals(null, state.endWakeupReason("late", 2500))
        state.printAllReset(3000)

        assertEquals("CPU running,string,1000,2000,2500~late,\n", out.toString())
    }

    @Test
    fun `ending a different wakeup reason reports an error`() {
        val state = State(StringBuilder())

        state.startWakeupReason("a", 100)
        assertEquals(
            "tried to end a different wakeup reason (\"b\") than was started (\"a\")",
            state.endWakeupReason("b", 200),
        )
        assertEquals(null, state.endWakeupReason("a", 250))
    }

    private fun cpuRunning(startMs: Long) = Entry(
        desc = Csv.CPU_RUNNING,
        startMs = startMs,
        type = "string",
        value = "",
    )
}
