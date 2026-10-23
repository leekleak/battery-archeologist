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
 * Kotlin port of the CSV parsing and encoding behavior used by
 * third_party/battery-historian/csv/csv.go (encoding/csv plus
 * checkinutil.ParseCSV: lazy quotes, leading-space trimming, variable field
 * counts, and whitespace trimming of every field).
 */

package com.leekleak.batteryarcheologist.core.history.csv

/**
 * Encodes [fields] as one CSV record terminated by `\n`.
 *
 * This follows Go's `encoding/csv.Writer` defaults: a field is quoted when it
 * contains a comma, quote, carriage return, or newline, when it starts with
 * whitespace, or when it is exactly `\.`; embedded quotes are doubled.
 */
internal fun encodeCsvRecord(fields: List<String>): String =
    fields.joinToString(separator = ",", postfix = "\n") { encodeCsvField(it) }

internal fun encodeCsvField(field: String): String {
    if (field.isEmpty()) return ""
    val needsQuotes = field == "\\." ||
        field.any { it == ',' || it == '"' || it == '\r' || it == '\n' } ||
        isCsvSpace(field[0])
    if (!needsQuotes) return field

    return buildString(field.length + 2) {
        append('"')
        for (c in field) {
            if (c == '"') append("\"\"") else append(c)
        }
        append('"')
    }
}

/**
 * Parses CSV text into records.
 *
 * Mirrors the reader configured by `checkinutil.ParseCSV`:
 * `FieldsPerRecord = -1`, `LazyQuotes = true`, `TrimLeadingSpace = true`, then
 * `strings.TrimSpace` on every field. It also mirrors Go's handling of `\r\n`
 * line endings and quoted fields spanning multiple lines.
 *
 * The upstream helper returns `nil` on a read error. With an in-memory string
 * and lazy quotes there is no reachable error path, so this function always
 * returns the records it parsed.
 */
internal fun parseCsvRecords(input: String): List<List<String>> =
    CsvRecordReader(input).readAll()

/** Removes one pair of surrounding double quotes, if both are present. */
internal fun stripQuotes(value: String): String =
    if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
        value.substring(1, value.length - 1)
    } else {
        value
    }

/** Matches Go's `unicode.IsSpace`. */
private fun isCsvSpace(c: Char): Boolean = c.isWhitespace() || Character.isSpaceChar(c)

/** Matches Go's `strings.TrimSpace`. */
private fun trimCsvSpace(value: String): String {
    var start = 0
    var end = value.length
    while (start < end && isCsvSpace(value[start])) start++
    while (end > start && isCsvSpace(value[end - 1])) end--
    return value.substring(start, end)
}

/**
 * A copy of the relevant parts of Go's `encoding/csv.Reader`.
 *
 * The Go reader works on lines (including their trailing `\n`) so that
 * leading-space trimming never crosses a line boundary. This reader keeps the
 * same line-oriented structure to preserve that behavior.
 */
private class CsvRecordReader(private val input: String) {
    private var offset = 0

    fun readAll(): List<List<String>> {
        val records = ArrayList<List<String>>()
        while (true) {
            val record = readRecord() ?: break
            records.add(record.map(::trimCsvSpace))
        }
        return records
    }

    /** Reads one record, or returns `null` at end of input. */
    private fun readRecord(): List<String>? {
        var line: String
        // Skip blank lines (a line containing only its terminator).
        while (true) {
            line = readLine() ?: return null
            if (line == "\n") continue
            break
        }

        val fields = ArrayList<String>()
        var pos = 0
        while (true) {
            // TrimLeadingSpace, on this line only.
            while (pos < line.length && isCsvSpace(line[pos])) pos++

            if (pos >= line.length || line[pos] != '"') {
                // Non-quoted field.
                val comma = line.indexOf(',', pos)
                val end = if (comma >= 0) {
                    comma
                } else {
                    line.length - if (line.endsWith('\n')) 1 else 0
                }
                fields.add(line.substring(pos, end.coerceAtLeast(pos)))
                if (comma >= 0) {
                    pos = comma + 1
                    continue
                }
                return fields
            }

            // Quoted field.
            pos++
            val field = StringBuilder()
            while (true) {
                val quote = line.indexOf('"', pos)
                if (quote >= 0) {
                    field.append(line, pos, quote)
                    pos = quote + 1
                    when {
                        pos < line.length && line[pos] == '"' -> {
                            // "" inside a quoted field: a literal quote.
                            field.append('"')
                            pos++
                        }
                        pos < line.length && line[pos] == ',' -> {
                            // Closing quote followed by the field separator.
                            fields.add(field.toString())
                            pos++
                            break
                        }
                        pos >= line.length || line[pos] == '\n' -> {
                            // Closing quote at the end of the line.
                            fields.add(field.toString())
                            return fields
                        }
                        else -> {
                            // A bare quote inside a quoted field (LazyQuotes).
                            field.append('"')
                        }
                    }
                } else {
                    // No quote on this line: the field continues on the next
                    // line. Go keeps the newline as part of the value.
                    field.append(line, pos, line.length)
                    val next = readLine()
                    if (next == null) {
                        fields.add(field.toString())
                        return fields
                    }
                    line = next
                    pos = 0
                }
            }
        }
    }

    /**
     * Returns the next line including its trailing `\n`, or `null` at end of
     * input. A lone `\r` at end of input is dropped; `\r\n` is normalized to
     * `\n`, matching Go's `encoding/csv` reader.
     */
    private fun readLine(): String? {
        if (offset >= input.length) return null

        val newline = input.indexOf('\n', offset)
        return if (newline < 0) {
            var line = input.substring(offset)
            offset = input.length
            if (line.endsWith('\r')) line = line.dropLast(1)
            line
        } else {
            var line = input.substring(offset, newline + 1)
            offset = newline + 1
            if (line.length >= 2 && line[line.length - 2] == '\r') {
                line = line.dropLast(2) + "\n"
            }
            line
        }
    }
}
