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
 * Kotlin port of the history regexes and `fixTimeline` from
 * third_party/battery-historian/parseutils/parseutils.go.
 */

package com.leekleak.batteryarcheologist.core.history.parseutils

/**
 * Regexes used by the history parser.
 *
 * Go compiles these as `regexp.MustCompile` with named groups; Kotlin's
 * regexes expose the same capture positions. Matching is performed against
 * trimmed lines, mirroring `historianutils.SubexpNames`.
 */
internal object HistoryRegex {
    const val BATTERY_STATS_CHECKIN_VERSION = "9"
    const val HISTORY_STRING_POOL = "hsp"
    const val HISTORY_DATA = "h"

    val reset = Regex("""^9,h,(\d+):RESET:TIME:(\d+)""")
    val shutdown = Regex("""^9,h,(\d+):SHUTDOWN""")
    val start = Regex("""^9,h,(\d+):START""")
    val time = Regex("""^9,h,(\d+):TIME:(\d+)""")
    val genericHistoryLine = Regex("""^9,h,(\d+).*""")
    val genericHistoryStringPoolLine = Regex("""^9,hsp,(\d+),(-?\d+),(.+)""")
    val versionLine = Regex("""^9,\d+,i,vers,(\d+),\d+,.*""")
    val data = Regex("""([+-]?)(\w+)(,?(=?(?<value>\S+))?)""")
    val overflow = Regex("""^9,h,\d+:\*OVERFLOW\*""")
    val checkinApkLine = Regex("""(\d+,)?(?<uid>\d+),l,apk,\d+,(?<pkgName>[^,]+),.*""")
    val nextLine = Regex("""^NEXT: (\d+)""")
    val oldHistoryLine = Regex("""^7,h""")
}

/** Result of [fixTimeline]. */
data class FixTimelineResult(
    /** History lines with normalized `TIME`/`RESET` timestamps. */
    val lines: List<String>,
    /** Whether any timestamp was rewritten. */
    val timestampsAltered: Boolean,
    /** A parse error; when set, [lines] is empty, like upstream. */
    val error: String? = null,
)

/**
 * Filters a raw checkin capture down to its history lines and normalizes
 * `TIME` / `RESET:TIME` timestamps.
 *
 * The function assumes the last time statement between reboots is the most
 * accurate one. Walking backwards, it carries that timestamp into earlier
 * lines, subtracting each line's delta, and stops carrying it across
 * `START` or `SHUTDOWN` events (whose preceding boot has its own accurate
 * `TIME` statement).
 *
 * Port of Go `fixTimeline`.
 */
fun fixTimeline(history: String): FixTimelineResult {
    val lines = ArrayList<String>()
    for (rawLine in history.split('\n')) {
        val line = rawLine.trim()
        if (HistoryRegex.genericHistoryLine.containsMatchIn(line) ||
            HistoryRegex.genericHistoryStringPoolLine.containsMatchIn(line) ||
            HistoryRegex.versionLine.containsMatchIn(line)
        ) {
            lines.add(line)
        }
    }

    var changed = false
    var time = 0L
    var timeFound = false

    for (index in lines.indices.reversed()) {
        var line = lines[index]
        if (timeFound) {
            if (HistoryRegex.start.containsMatchIn(line) || HistoryRegex.shutdown.containsMatchIn(line)) {
                // START/SHUTDOWN begin a new boot; earlier lines belong to a
                // different timeline and need their own TIME statement.
                timeFound = false
                continue
            }
            val separator = line.split(":TIME:")
            if (separator.size == 2) {
                if (time < 0) {
                    return FixTimelineResult(emptyList(), changed, "negative time calculated")
                }
                line = "${separator[0]}:TIME:$time"
                lines[index] = line
                changed = true
            }
            val match = HistoryRegex.genericHistoryLine.find(line)
            if (match != null) {
                val delta = match.groupValues[1].toLongOrNull()
                    ?: return FixTimelineResult(
                        emptyList(),
                        changed,
                        "strconv.ParseInt: parsing \"${match.groupValues[1]}\": invalid syntax",
                    )
                time -= delta
            }
        } else {
            var match = HistoryRegex.time.find(line)
            var timestamp = ""
            var delta = ""
            if (match != null) {
                timeFound = true
                timestamp = match.groupValues[2]
                delta = match.groupValues[1]
            } else {
                match = HistoryRegex.reset.find(line)
                if (match != null) {
                    timeFound = true
                    timestamp = match.groupValues[2]
                    delta = match.groupValues[1]
                }
            }
            if (timeFound) {
                val parsedTime = timestamp.toLongOrNull()
                    ?: return FixTimelineResult(
                        emptyList(),
                        changed,
                        "strconv.ParseInt: parsing \"$timestamp\": invalid syntax",
                    )
                val parsedDelta = delta.toLongOrNull()
                    ?: return FixTimelineResult(
                        emptyList(),
                        changed,
                        "strconv.ParseInt: parsing \"$delta\": invalid syntax",
                    )
                time = parsedTime - parsedDelta
            }
        }
    }
    return FixTimelineResult(lines, changed)
}
