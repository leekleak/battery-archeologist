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
//
// Kotlin port of third_party/battery-historian/historianutils/historianutils.go.
// The Go command helper is intentionally not ported: :core never shells out.

package com.leekleak.batteryarcheologist.core.historianutils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Common utility functions for Battery Historian processing.
 *
 * Ported from the Go `historianutils` package. All functions are pure and have no
 * Android dependencies.
 */
object HistorianUtils {
    /** Matches the beginning of a service dump. */
    val serviceDumpRe = Regex("""^DUMP\s+OF\s+SERVICE\s+(?<service>\S+):""")

    // Matches any PII string of the form abc@xxx.yyy.
    private val piiEmailRe = Regex("""(?<prefix>\S+/)?(?<account>\S+)@(?<suffix>\S+\.\S+)""")

    // Matches any PII string of the form *sync*/blah/blah/pii.
    private val piiSyncRe = Regex("""(?<prefix>\*sync\*/\S+/)(?<account>\S+)""")

    /**
     * Scrubs any part of the string that looks like PII (e.g. an email address).
     *
     * An identifier such as
     * `com.google.android.apps.plus.content.EsProvider/com.google/john.doe@gmail.com/extra`
     * becomes `com.google.android.apps.plus.content.EsProvider/com.google/XXX@gmail.com/extra`.
     * A sync identifier ending in an account name has that account name replaced by
     * `XXX`, even when the name is not an email address.
     */
    fun scrubPII(input: String): String {
        subexpNames(piiEmailRe, input)?.let { result ->
            return "${result["prefix"].orEmpty()}XXX@${result["suffix"].orEmpty()}"
        }
        subexpNames(piiSyncRe, input)?.let { result ->
            // Syncs often have the PII at the end, though not always in email form.
            return "${result["prefix"].orEmpty()}XXX"
        }
        return input
    }

    /**
     * Returns a mapping of named sub-expressions to values if [regex] matches [input],
     * otherwise `null`.
     *
     * Semantics follow Go's `SubexpNames`: the input and every captured value are
     * trimmed, unmatched groups map to the empty string, and the whole match is
     * stored under the empty key. Go's RE2 exposes sub-expression names directly;
     * the JVM does not, so the names are read from the pattern source.
     */
    fun subexpNames(regex: Regex, input: String): Map<String, String>? {
        val match = regex.find(input.trim()) ?: return null
        val names = capturingGroupNames(regex.pattern)
        val result = LinkedHashMap<String, String>()
        match.groupValues.forEachIndexed { index, value ->
            val name = if (index == 0) "" else names.getOrNull(index - 1).orEmpty()
            result[name] = value.trim()
        }
        return result
    }

    /**
     * Converts an array of errors into a newline-delimited string.
     *
     * Mirrors Go's `errorsToString`, including the trailing newline.
     */
    fun errorsToString(errors: List<Throwable>): String = buildString {
        for (error in errors) {
            append(error.message ?: error.toString())
            append('\n')
        }
    }

    /**
     * Compresses byte data with gzip.
     *
     * Port of Go's `GzipCompress`; unlike the Go version, failures surface as
     * [java.io.IOException] instead of a separate error result.
     */
    fun gzipCompress(uncompressed: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(uncompressed) }
        return out.toByteArray()
    }

    /**
     * Decompresses gzip data.
     *
     * Not present in the upstream Go package; added because bug report imports are
     * occasionally gzipped. Throws [java.io.IOException] for invalid input.
     */
    fun gzipDecompress(compressed: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }

    /**
     * Parses a duration string and returns milliseconds, e.g. `3d1h2m`.
     *
     * This is the same as Go's `time.ParseDuration`, but also handles days. Days are
     * assumed to be 24 hours, which is not exact but usually good enough for what we
     * care about.
     *
     * @throws IllegalArgumentException if the input is empty or malformed.
     */
    fun parseDurationWithDays(input: String): Long {
        if (input.isEmpty()) {
            throw IllegalArgumentException("cannot parse duration from empty string")
        }
        var remaining = input
        var totalNanos = 0L

        val dayIndex = remaining.indexOf('d')
        // Go's time.ParseDuration rejects strings containing days, so the day part is
        // parsed and removed before handing the rest to the duration parser.
        if (dayIndex >= 0) {
            val days = remaining.substring(0, dayIndex).toIntOrNull()
                ?: throw NumberFormatException("invalid day component in duration: \"$input\"")
            totalNanos += days.toLong() * NANOS_PER_DAY
            remaining = remaining.substring(dayIndex + 1)
        }
        if (remaining.isNotEmpty()) {
            totalNanos += parseGoDurationNanos(remaining)
        }
        return totalNanos / NANOS_PER_MILLI
    }

    /**
     * Reads the names of capturing groups from a Java regular expression, in group
     * index order. Returns `null` entries for unnamed capturing groups so callers
     * can align them with [MatchResult.groupValues].
     */
    private fun capturingGroupNames(pattern: String): List<String?> {
        val names = ArrayList<String?>()
        var i = 0
        var inCharacterClass = false
        while (i < pattern.length) {
            when (val c = pattern[i]) {
                '\\' -> i++ // Skip the escaped character; the loop advances again below.
                '[' -> if (!inCharacterClass) inCharacterClass = true
                ']' -> if (inCharacterClass) inCharacterClass = false
                '(' -> if (!inCharacterClass) {
                    if (i + 1 < pattern.length && pattern[i + 1] == '?') {
                        val nameStart = i + 3
                        val isNamedGroup = i + 2 < pattern.length &&
                            pattern[i + 2] == '<' &&
                            nameStart < pattern.length &&
                            (pattern[nameStart].isLetter() || pattern[nameStart] == '_')
                        if (isNamedGroup) {
                            val nameEnd = pattern.indexOf('>', nameStart)
                            if (nameEnd > nameStart) {
                                names += pattern.substring(nameStart, nameEnd)
                                i = nameEnd
                            }
                        }
                        // (?:...), (?=...), (?!...), (?<=...) and (?<!...) are not
                        // capturing groups and need no entry.
                    } else {
                        names += null
                    }
                }
            }
            i++
        }
        return names
    }

    /**
     * Port of Go's `time.ParseDuration` for a single duration string. Returns
     * nanoseconds, truncating fractional nanoseconds toward zero.
     */
    private fun parseGoDurationNanos(input: String): Long {
        if (input.isEmpty()) {
            throw IllegalArgumentException("time: invalid duration \"$input\"")
        }
        var remaining = input
        var negative = false
        val sign = remaining[0]
        if (sign == '-' || sign == '+') {
            negative = sign == '-'
            remaining = remaining.substring(1)
        }
        // Special case: if all that is left is "0", this is zero.
        if (remaining == "0") return 0
        if (remaining.isEmpty()) {
            throw IllegalArgumentException("time: invalid duration \"$input\"")
        }

        var totalNanos = 0L
        while (remaining.isNotEmpty()) {
            val first = remaining[0]
            if (first != '.' && !first.isDigit()) {
                throw IllegalArgumentException("time: invalid duration \"$input\"")
            }

            var index = 0
            while (index < remaining.length && remaining[index].isDigit()) index++
            val integerPart = remaining.substring(0, index)

            var fractionPart = ""
            var hasFraction = false
            if (index < remaining.length && remaining[index] == '.') {
                hasFraction = true
                index++
                val fractionStart = index
                while (index < remaining.length && remaining[index].isDigit()) index++
                fractionPart = remaining.substring(fractionStart, index)
            }
            if (integerPart.isEmpty() && fractionPart.isEmpty()) {
                throw IllegalArgumentException("time: invalid duration \"$input\"")
            }

            val unitStart = index
            while (index < remaining.length && !remaining[index].isDigit() && remaining[index] != '.') {
                index++
            }
            if (index == unitStart) {
                throw IllegalArgumentException("time: missing unit in duration \"$input\"")
            }
            val unit = remaining.substring(unitStart, index)
            val unitNanos = DURATION_UNIT_NANOS[unit]
                ?: throw IllegalArgumentException("time: unknown unit \"$unit\" in duration \"$input\"")

            val integerValue = if (integerPart.isEmpty()) {
                0L
            } else {
                integerPart.toLongOrNull()
                    ?: throw IllegalArgumentException("time: invalid duration \"$input\"")
            }
            var nanos = integerValue * unitNanos
            if (hasFraction) {
                nanos += BigDecimal("0.$fractionPart")
                    .multiply(BigDecimal.valueOf(unitNanos))
                    .toLong()
            }
            totalNanos += nanos
            remaining = remaining.substring(index)
        }
        return if (negative) -totalNanos else totalNanos
    }

    private const val NANOS_PER_MILLI = 1_000_000L
    private const val NANOS_PER_DAY = 24L * 60L * 60L * 1_000_000_000L

    private val DURATION_UNIT_NANOS = mapOf(
        "ns" to 1L,
        "us" to 1_000L,
        "µs" to 1_000L, // U+00B5 micro sign
        "μs" to 1_000L, // U+03BC Greek small letter mu
        "ms" to 1_000_000L,
        "s" to 1_000_000_000L,
        "m" to 60L * 1_000_000_000L,
        "h" to 60L * 60L * 1_000_000_000L,
    )
}
