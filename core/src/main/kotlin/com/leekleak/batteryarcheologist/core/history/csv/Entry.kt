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
 * Kotlin port of the Entry, Key, and EntryState types in
 * third_party/battery-historian/csv/csv.go.
 */

package com.leekleak.batteryarcheologist.core.history.csv

/**
 * Unique identifier for an active entry.
 *
 * One metric can have several concurrent entries as long as their
 * [identifier] differs (for example one entry per UID).
 *
 * Port of Go `csv.Key`.
 */
data class Key(
    val metric: String,
    val identifier: String,
)

/**
 * Common interface for values that can be stored in a [State].
 *
 * Port of Go `csv.EntryState`. All times are epoch milliseconds.
 */
interface EntryState {
    /** Start time in epoch milliseconds. Port of Go `GetStartTime`. */
    val startTimeMs: Long

    /**
     * Entry type: `string`, `bool`, `float`, `group`, `int`, `service`, or
     * `summary`.
     */
    val type: String

    /** Stored value. Port of Go `GetValue`. */
    val value: String

    /**
     * Key for this entry under the given metric.
     *
     * Port of Go `GetKey`. Note that [desc] (the metric name) is supplied by
     * the caller and is not necessarily this entry's [Entry.desc].
     */
    fun key(desc: String): Key
}

/**
 * Details of the start of a state.
 *
 * Port of Go `csv.Entry`. Go's `Start` field is named [startMs] here to make
 * the unit explicit and to match `TimelineEntry.startMs`; the CSV wire format
 * is unchanged.
 */
data class Entry(
    val desc: String = "",
    val startMs: Long = 0L,
    override val type: String = "",
    override val value: String = "",
    val opt: String = "",
    /**
     * Unique identifier for the event, for example the name or UID of the app
     * that triggered it. Port of Go `Entry.Identifier`.
     */
    val identifier: String = "",
) : EntryState {
    override val startTimeMs: Long get() = startMs

    override fun key(desc: String): Key = Key(desc, identifier)
}
