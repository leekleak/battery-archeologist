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
 * Kotlin port of third_party/battery-historian/csv/csv.go.
 */

package com.leekleak.batteryarcheologist.core.history.csv

import java.io.Flushable

/**
 * Constants shared by the CSV writer and parser.
 *
 * Port of the constants declared in Go `csv.go`.
 */
object Csv {
    /** First line written to CSV files (Go `FileHeader`). */
    const val FILE_HEADER = "metric,type,start_time,end_time,value,opt"

    /** Used for running events that have no wakeup reason (Go `UnknownWakeup`). */
    const val UNKNOWN_WAKEUP = "Unknown wakeup reason"

    /** Metric name of CPU running events (Go `CPURunning`). */
    const val CPU_RUNNING = "CPU running"

    /** Metric name of reboot events (Go `Reboot`). */
    const val REBOOT = "Reboot"
}

/**
 * Accumulates battery history events and serializes completed intervals as CSV.
 *
 * Port of Go `csv.State`. Timestamps are epoch milliseconds stored as [Long],
 * mirroring Go's `int64` millisecond timestamps and the six-column CSV format
 * (`metric,type,start_time,end_time,value,opt`).
 *
 * The Go implementation writes through a buffered `encoding/csv.Writer` to an
 * `io.Writer`; this class appends to an [Appendable]. If that appendable is a
 * [Flushable] (for example a `java.io.Writer`), it is flushed after every
 * record, matching the Go code's `csv.Writer.Flush` calls.
 *
 * @param writer CSV destination. `null` disables output, which is the safe
 *   interpretation of Go's nil-writer case.
 * @param printHeader when `true`, writes [Csv.FILE_HEADER] before any records.
 */
class State(
    private val writer: Appendable?,
    printHeader: Boolean = false,
) {
    /** Currently active entries, keyed by metric and identifier. */
    private val entries = LinkedHashMap<Key, Entry>()

    /**
     * The last CPU running event, held back until the next running event (or a
     * reset) so that wakeup reasons that arrive late can still be attached.
     */
    private var runningEvent: RunningEvent? = null

    /**
     * Wakeup reasons for the current running event, kept separately because
     * they can arrive before or after the CPU running transition.
     */
    private val wakeupReasonBuffer = StringBuilder()

    /** Wakeup reason currently in progress, if any. */
    private var currentWakeupReason: WakeupReason? = null

    private var rebootEvent: Entry? = null

    init {
        if (writer != null && printHeader) {
            writer.append(Csv.FILE_HEADER).append('\n')
            flush()
        }
    }

    /** Returns whether a reboot event is currently stored. */
    fun hasRebootEvent(): Boolean = rebootEvent != null

    /**
     * Stores the entry for a reboot event, using [curTimeMs] as its start.
     *
     * Port of Go `AddRebootEvent`.
     */
    fun addRebootEvent(curTimeMs: Long) {
        rebootEvent = Entry(
            desc = Csv.REBOOT,
            startMs = curTimeMs,
            type = "bool",
            value = "true",
        )
    }

    /**
     * Prints the stored reboot event using [curTimeMs] as its end time and
     * clears it.
     *
     * Port of Go `PrintRebootEvent`.
     */
    fun printRebootEvent(curTimeMs: Long) {
        val event = rebootEvent ?: return
        print(event.desc, event.type, event.startMs, curTimeMs, event.value, event.opt)
        rebootEvent = null
    }

    /**
     * Adds an entry with no optional value.
     *
     * Port of Go `AddEntry`.
     */
    fun addEntry(desc: String, newState: EntryState, curTimeMs: Long) {
        addEntryWithOpt(desc, newState, curTimeMs, "")
    }

    /**
     * Adds an entry, storing [opt] as its optional value.
     *
     * If an entry with the same key is already active, that older entry is
     * printed with [curTimeMs] as its end time and removed; the new entry is
     * not stored in that case. Callers that want an instantaneous event based
     * on this behavior must call this method twice.
     *
     * Entries with a zero start time are ignored. So are entries with an empty
     * value, except for [Csv.CPU_RUNNING] entries (whose value is filled in
     * from the wakeup reasons later).
     *
     * Port of Go `AddEntryWithOpt`.
     */
    fun addEntryWithOpt(
        desc: String,
        newState: EntryState,
        curTimeMs: Long,
        opt: String,
    ) {
        val key = newState.key(desc)
        val existing = entries[key]
        if (existing != null) {
            if (desc == Csv.CPU_RUNNING) {
                // Hold the running event back: wakeup reasons can arrive after
                // the running interval ends.
                assignRunningEvent(RunningEvent(existing, curTimeMs))
            } else {
                print(
                    existing.desc,
                    existing.type,
                    existing.startMs,
                    curTimeMs,
                    existing.value,
                    existing.opt,
                )
            }
            entries.remove(key)
            return
        }
        // Running events may legitimately have an empty value, because the
        // wakeup reason is filled in later. All other empty values are ignored.
        if (newState.startTimeMs == 0L ||
            (newState.value.isEmpty() && desc != Csv.CPU_RUNNING)
        ) {
            return
        }
        if (desc == Csv.CPU_RUNNING) {
            // Flush the previous running event, if any, before starting a new
            // one.
            assignRunningEvent(null)
        }
        entries[key] = Entry(
            desc = desc,
            startMs = curTimeMs,
            type = newState.type,
            value = newState.value,
            opt = opt,
        )
    }

    /**
     * Adds [opt] to an already active entry with the same key.
     *
     * No change is made if the entry does not exist.
     *
     * Port of Go `AddOptToEntry`.
     */
    fun addOptToEntry(desc: String, state: EntryState, opt: String) {
        val key = state.key(desc)
        val existing = entries[key] ?: return
        entries[key] = existing.copy(opt = opt)
    }

    /**
     * Marks an event as beginning at [Entry.startMs].
     *
     * Does nothing if an event with the same metric and identifier is already
     * active. For events without a duration, use [printInstantEvent].
     *
     * Port of Go `StartEvent`.
     */
    fun startEvent(e: Entry) {
        if (hasEvent(e.desc, e.identifier)) return
        addEntryWithOpt(e.desc, e, e.startMs, e.opt)
    }

    /**
     * Returns whether an event for [metric] with [eventIdentifier] is active.
     *
     * Port of Go `HasEvent`.
     */
    fun hasEvent(metric: String, eventIdentifier: String): Boolean =
        entries.containsKey(Key(metric, eventIdentifier))

    /**
     * Marks an event as finished at [curTimeMs].
     *
     * Does nothing if the event is not currently active.
     *
     * Port of Go `EndEvent`.
     */
    fun endEvent(metric: String, eventIdentifier: String, curTimeMs: Long) {
        if (!hasEvent(metric, eventIdentifier)) return
        val end = Entry(
            desc = metric,
            startMs = curTimeMs,
            identifier = eventIdentifier,
        )
        addEntry(metric, end, curTimeMs)
    }

    /**
     * Directly prints one CSV record.
     *
     * Leading and trailing double quotes are stripped from [value] and [opt]
     * before encoding; internal quotes are escaped by the CSV encoder.
     *
     * Port of Go `Print`.
     */
    fun print(
        desc: String,
        metricType: String,
        startMs: Long,
        endMs: Long,
        value: String,
        opt: String,
    ) {
        val sink = writer ?: return
        sink.append(
            encodeCsvRecord(
                listOf(
                    desc,
                    metricType,
                    startMs.toString(),
                    endMs.toString(),
                    stripQuotes(value),
                    stripQuotes(opt),
                ),
            ),
        )
        flush()
    }

    /**
     * Writes an event produced by [extractEvents].
     *
     * Port of Go `PrintEvent`.
     */
    fun printEvent(metric: String, event: Event) {
        print(metric, event.type, event.startMs, event.endMs, event.value, event.opt)
    }

    /**
     * Writes an instantaneous event (end time equals start time).
     *
     * Port of Go `PrintInstantEvent`.
     */
    fun printInstantEvent(e: Entry) {
        print(e.desc, e.type, e.startMs, e.startMs, e.value, e.opt)
    }

    /**
     * Replaces the previous running event with [newEvent], printing the
     * previous one if it exists.
     *
     * Only used for CPU running events.
     */
    private fun assignRunningEvent(newEvent: RunningEvent?) {
        runningEvent?.let { previous ->
            val entry = previous.entry
            val value = wakeupReasons(previous.endMs)
            wakeupReasonBuffer.setLength(0)
            print(entry.desc, entry.type, entry.startMs, previous.endMs, value, entry.opt)
        }
        runningEvent = newEvent
    }

    /**
     * Starts a wakeup reason at [curTimeMs], first appending any wakeup reason
     * that was already in progress.
     *
     * Port of Go `StartWakeupReason`.
     */
    fun startWakeupReason(service: String, curTimeMs: Long) {
        currentWakeupReason?.let { appendWakeupReason(it, curTimeMs) }
        currentWakeupReason = WakeupReason(service, curTimeMs)
    }

    /**
     * Ends the wakeup reason [service] at [curTimeMs] and appends it to the
     * buffer used for the next CPU running event.
     *
     * If no wakeup reason is in progress, an instantaneous reason is recorded.
     * If a different reason is in progress, nothing is recorded and a
     * human-readable error message is returned; this mirrors the Go method's
     * `error` return value.
     *
     * Port of Go `EndWakeupReason`.
     */
    fun endWakeupReason(service: String, curTimeMs: Long): String? {
        val current = currentWakeupReason
        if (current != null) {
            if (current.name != service) {
                return "tried to end a different wakeup reason " +
                    "(\"$service\") than was started (\"${current.name}\")"
            }
            appendWakeupReason(current, curTimeMs)
            currentWakeupReason = null
            return null
        }
        // No current wakeup reason: "start" and end this one at the same time.
        appendWakeupReason(WakeupReason(service, curTimeMs), curTimeMs)
        return null
    }

    /**
     * Appends a wakeup reason and its start/end times to the buffer.
     *
     * Each entry is `start~end~name`, or `time~name` for an instantaneous
     * reason. Entries are separated by `|`. Surrounding quotes are stripped
     * from the name.
     */
    private fun appendWakeupReason(reason: WakeupReason, curTimeMs: Long) {
        if (wakeupReasonBuffer.isNotEmpty()) {
            wakeupReasonBuffer.append('|')
        }
        val name = stripQuotes(reason.name)
        if (reason.startMs == curTimeMs) {
            wakeupReasonBuffer.append(curTimeMs).append('~').append(name)
        } else {
            wakeupReasonBuffer.append(reason.startMs)
                .append('~')
                .append(curTimeMs)
                .append('~')
                .append(name)
        }
    }

    /**
     * Returns the buffered wakeup reasons, adding an [Csv.UNKNOWN_WAKEUP]
     * reason if none were recorded. The result is wrapped in double quotes,
     * like the Go implementation.
     */
    private fun wakeupReasons(curTimeMs: Long): String {
        currentWakeupReason?.let { appendWakeupReason(it, curTimeMs) }
        if (wakeupReasonBuffer.isEmpty()) {
            val startMs = runningEvent?.entry?.startMs ?: curTimeMs
            appendWakeupReason(WakeupReason(Csv.UNKNOWN_WAKEUP, startMs), curTimeMs)
        }
        return "\"$wakeupReasonBuffer\""
    }

    /**
     * Prints all active entries with [curTimeMs] as their end time, then
     * clears the map and any pending running event.
     *
     * Port of Go `PrintAllReset`.
     */
    fun printAllReset(curTimeMs: Long) {
        for (entry in entries.values) {
            var value = entry.value
            if (entry.desc == Csv.CPU_RUNNING) {
                value = wakeupReasons(curTimeMs)
                wakeupReasonBuffer.setLength(0)
            }
            print(entry.desc, entry.type, entry.startMs, curTimeMs, value, entry.opt)
        }
        assignRunningEvent(null)
        entries.clear()
    }

    /**
     * Prints all active entries for [metric] with [endMs] as their end time
     * and removes them.
     *
     * Port of Go `PrintActiveEvent`.
     */
    fun printActiveEvent(metric: String, endMs: Long) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            if (entry.desc == metric) {
                print(entry.desc, entry.type, entry.startMs, endMs, entry.value, entry.opt)
                iterator.remove()
            }
        }
    }

    private fun flush() {
        (writer as? Flushable)?.flush()
    }

    /** A completed CPU running interval that is waiting for wakeup reasons. */
    private data class RunningEvent(val entry: Entry, val endMs: Long)

    /** A wakeup reason and the time at which it started. */
    private data class WakeupReason(val name: String, val startMs: Long)
}
