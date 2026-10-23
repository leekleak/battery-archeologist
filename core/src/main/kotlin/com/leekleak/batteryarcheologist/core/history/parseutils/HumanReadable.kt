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
 * Kotlin port of third_party/battery-historian/parseutils/human_readable.go.
 */

package com.leekleak.batteryarcheologist.core.history.parseutils

/**
 * Maps adjusted history timestamps to the cumulative, human-readable deltas
 * shown by the human-readable battery history.
 *
 * Port of Go `deltaMapping`.
 */
internal class DeltaMapping {
    /** Total of all deltas read so far. */
    var cumulativeDeltaMs: Long = 0L
        private set

    /** Unix timestamp in milliseconds to human readable delta (`+1h00m50s002ms`). */
    val timeToDelta: MutableMap<String, String> = LinkedHashMap()

    /**
     * Adds [deltaMs] to the cumulative delta and records a mapping for
     * [timestampMs] when it is non-zero.
     *
     * @return an error message when the cumulative delta becomes negative,
     *   or `null` on success.
     */
    fun addMapping(timestampMs: Long, deltaMs: Long): String? {
        cumulativeDeltaMs += deltaMs
        if (timestampMs != 0L) {
            val formatted = try {
                formatDelta(cumulativeDeltaMs)
            } catch (e: IllegalArgumentException) {
                return e.message
            }
            timeToDelta[timestampMs.toString()] = formatted
        }
        return null
    }
}

/**
 * Returns the human readable format for a non-negative delta, matching the
 * deltas in the human readable battery history.
 *
 * Units are zero padded unless they are the leading unit, and the leading unit
 * is non-zero, except for the special case of a zero delta.
 * For example: `+1d01h33m33s000ms`.
 *
 * Port of Go `formatDelta`.
 *
 * @throws IllegalArgumentException when [deltaMs] is negative.
 */
fun formatDelta(deltaMs: Long): String {
    require(deltaMs >= 0) { "negative delta $deltaMs not allowed" }
    if (deltaMs == 0L) return "0"

    var remaining = deltaMs
    val milliseconds = remaining % 1000
    remaining /= 1000
    if (remaining == 0L) {
        return "+${milliseconds}ms"
    }
    var result = "${milliseconds.toString().padStart(3, '0')}ms"

    val seconds = remaining % 60
    remaining /= 60
    if (remaining == 0L) {
        return "+${seconds}s$result"
    }
    result = "${seconds.toString().padStart(2, '0')}s$result"

    val minutes = remaining % 60
    remaining /= 60
    if (remaining == 0L) {
        return "+${minutes}m$result"
    }
    result = "${minutes.toString().padStart(2, '0')}m$result"

    val hours = remaining % 24
    remaining /= 24
    if (remaining == 0L) {
        return "+${hours}h$result"
    }
    result = "${hours.toString().padStart(2, '0')}h$result"

    return "+${remaining}d$result"
}
