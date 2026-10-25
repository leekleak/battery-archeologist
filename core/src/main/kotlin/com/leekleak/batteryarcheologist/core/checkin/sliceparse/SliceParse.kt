// Copyright 2015 Google Inc. All Rights Reserved.
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

// Kotlin port of third_party/battery-historian/sliceparse/sliceparse.go.
//
// The Go version uses pointers and accepts nil outputs to mean "skip this field".
// Kotlin has no equivalent of a typed nil pointer parameter, so the port exposes a
// small typed reader instead: every read consumes one element, [skip] consumes one
// element without storing it, and [remaining] returns the unconsumed tail. Error
// behaviour matches Consume: too few input elements or an unparsable value throws.

package com.leekleak.batteryarcheologist.core.checkin.sliceparse

/** Thrown when a slice element cannot be consumed as the requested type. */
class SliceParseException(message: String) : IllegalArgumentException(message)

/**
 * Typed sequential reader over one checkin CSV row.
 *
 * Integers are parsed from the part before the first `.` (the device prints some
 * integer fields with a fractional suffix, for example `0.0`), matching upstream.
 */
class SliceReader(private val input: List<String>) {
    private var index = 0

    /** Elements that have not been consumed yet. */
    val remaining: List<String>
        get() = if (index >= input.size) emptyList() else input.subList(index, input.size)

    val hasRemaining: Boolean
        get() = index < input.size

    /** Consumes one element and returns it unchanged. */
    fun readString(): String {
        return input.getOrNull(index++)
            ?: throw SliceParseException("input of size ${input.size} ended at position ${index - 1}")
    }

    /** Consumes one element as an int32. */
    fun readInt32(): Int {
        val raw = readString()
        return parseInt32(raw)
    }

    /** Consumes one element as an int64. */
    fun readInt64(): Long {
        val raw = readString()
        return parseInt64(raw)
    }

    /** Consumes one element as a float32. */
    fun readFloat32(): Float {
        val raw = readString()
        return raw.toFloatOrNull()
            ?: throw SliceParseException("cannot parse float32 from '$raw'")
    }

    /** Consumes one element as a float64. */
    fun readFloat64(): Double {
        val raw = readString()
        return raw.toDoubleOrNull()
            ?: throw SliceParseException("cannot parse float64 from '$raw'")
    }

    /** Consumes one element without storing it (`nil` output in the Go API). */
    fun skip(): SliceReader {
        if (index >= input.size) {
            throw SliceParseException("input of size ${input.size} ended at position $index")
        }
        index++
        return this
    }

    /** Consumes [count] elements without storing them. */
    fun skip(count: Int): SliceReader {
        repeat(count) { skip() }
        return this
    }

    companion object {
        /** Parses an int32, ignoring a fractional suffix. */
        fun parseInt32(raw: String): Int {
            val whole = raw.substringBefore('.')
            return whole.toIntOrNull()
                ?: throw SliceParseException("cannot parse int32 from '$raw'")
        }

        /** Parses an int64, ignoring a fractional suffix. */
        fun parseInt64(raw: String): Long {
            val whole = raw.substringBefore('.')
            return whole.toLongOrNull()
                ?: throw SliceParseException("cannot parse int64 from '$raw'")
        }
    }
}
