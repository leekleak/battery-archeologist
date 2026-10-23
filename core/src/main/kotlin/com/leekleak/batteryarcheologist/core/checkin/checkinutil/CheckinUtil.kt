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

// Kotlin port of third_party/battery-historian/checkinutil/checkin_util.go.
// The session protobuf is not required by the app-facing API: a BatteryReport is
// built directly from the raw `dumpsys batterystats --checkin` text instead.
//
// The CSV reader mirrors Go's encoding/csv settings used by the upstream parser:
// variable field counts, lazy quotes (bug reports may contain bare quotes), leading
// space trimming, and a final trim of every field.

package com.leekleak.batteryarcheologist.core.checkin.checkinutil

/** Linkage information for an application child. */
data class ChildInfo(
    /** Predefined parent name (for example GOOGLE_SERVICES for gms and gsf). */
    val parent: String = "",
    /** True if the app is a HeadChild. */
    val head: Boolean = false,
)

/**
 * Lightweight representation of an Android checkin report.
 *
 * This intentionally carries less information than the generated BatteryStats proto; it
 * only holds the fields the parser needs while reading the CSV rows.
 */
data class BatteryReport(
    /** End time, i.e. the time this report was taken, in microseconds. */
    val timeUsec: Long = 0L,
    val timeZone: String = "",
    val androidId: Long = 0L,
    val deviceGroup: List<String> = emptyList(),
    val checkinRule: List<String> = emptyList(),
    /** Build fingerprint or build ID from the `vers` line. */
    val buildId: String = "",
    val product: String = "",
    val radio: String = "",
    val bootloader: String = "",
    val sdkVersion: Int = 0,
    /** True if the Android Wear device is paired to an ALT-mode companion. */
    val isAltMode: Boolean = false,
    val cellOperator: String = "",
    val countryCode: String = "",
    val rawBatteryStats: List<List<String>> = emptyList(),
    val rawPackageManager: List<List<String>> = emptyList(),
)

/** A counter compatible with the upstream mapreduce-style counters. */
fun interface Counter {
    fun count(name: String, inc: Int)
}

/** Simple in-memory implementation of [Counter]. */
class IntCounter(var value: Int = 0) : Counter {
    override fun count(name: String, inc: Int) {
        value += inc
    }
}

/**
 * Counter wrapper that prefixes every counted name.
 *
 * Null characters in names are replaced by "null" to avoid "counter name contains embedded
 * null" errors, matching the upstream implementation.
 */
class PrefixCounter(val prefix: String, val counter: Counter) : Counter {
    override fun count(name: String, inc: Int) {
        counter.count((prefix + "-" + name).replace("\u0000", "null"), inc)
    }
}

/** Utilities shared by the checkin parser and its tests. */
object CheckinUtil {
    /** Minimum number of fields any type of battery stats line can have. */
    const val MIN_NUM_FIELDS = 4

    /**
     * Parses CSV content into rows of trimmed string fields.
     *
     * Quoted fields may contain commas, doubled quotes escape a literal quote, and a quote
     * that is not followed by a delimiter is treated as a literal character (Go's
     * `LazyQuotes` behaviour). Blank lines are skipped.
     */
    fun parseCsv(content: String): List<List<String>> {
        val records = ArrayList<List<String>>()
        var record = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var atFieldStart = true
        var i = 0

        fun endField() {
            record.add(field.toString())
            field.setLength(0)
            atFieldStart = true
        }

        fun endRecord() {
            endField()
            if (record.size > 1 || record[0].isNotBlank()) {
                records.add(record.map { it.trim() })
            }
            record = ArrayList()
        }

        while (i < content.length) {
            val c = content[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < content.length && content[i + 1] == '"') {
                        // Escaped quote inside a quoted field.
                        field.append('"')
                        i += 2
                        continue
                    }
                    val next = content.getOrNull(i + 1)
                    if (next == null || next == ',' || next == '\n' || next == '\r') {
                        inQuotes = false
                        i++
                        continue
                    }
                    // Lazy quote: a bare quote inside a quoted field.
                    field.append('"')
                    i++
                    continue
                }
                field.append(c)
                i++
                continue
            }

            when (c) {
                ' ', '\t' -> {
                    if (atFieldStart) {
                        // TrimLeadingSpace: spaces before a field or its opening quote are dropped.
                        i++
                    } else {
                        field.append(c)
                        i++
                    }
                }
                '"' -> {
                    if (atFieldStart) {
                        inQuotes = true
                        atFieldStart = false
                    } else {
                        field.append('"')
                    }
                    i++
                }
                ',' -> {
                    endField()
                    i++
                }
                '\n' -> {
                    endRecord()
                    i++
                }
                '\r' -> {
                    endRecord()
                    i += if (i + 1 < content.length && content[i + 1] == '\n') 2 else 1
                }
                else -> {
                    field.append(c)
                    atFieldStart = false
                    i++
                }
            }
        }

        if (field.isNotEmpty() || record.isNotEmpty()) {
            endRecord()
        }
        return records
    }

    /**
     * Extracts the info and SINCE_CHARGED rows from a checkin capture.
     *
     * Only `i` (info) and `l` (SINCE_CHARGED) rows are meaningful here; unplugged data is
     * deprecated and history rows are handled by the separate history parser.
     */
    fun extractCsv(input: String): List<List<String>> = parseCsv(input).filter { row ->
        row.size >= MIN_NUM_FIELDS && (row[2] == "i" || row[2] == "l")
    }

    /**
     * Creates a [BatteryReport] from raw checkin text.
     *
     * The raw checkin format does not carry the session metadata (time zone, radio, SDK
     * level, ...) that the upstream Session protobuf does, so those fields stay at their
     * defaults. The build ID is taken from the trailing field of the `vers` row.
     */
    fun createBatteryReport(raw: String): BatteryReport {
        val rows = extractCsv(raw)
        val versionRow = rows.firstOrNull { it.size > 3 && it[3] == "vers" }
        // The oracle and upstream both take the trailing field of the vers row.
        val buildId = versionRow?.lastOrNull().orEmpty()
        return BatteryReport(
            buildId = buildId,
            rawBatteryStats = rows,
        )
    }
}
