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

// Kotlin port of the reflection-driven helpers at the end of
// third_party/battery-historian/checkinparse/checkin_parse.go (parseLine,
// parseAndAccumulate and accumulate).
//
// The original code reflects over generated Go structs. The Kotlin port used the
// protobuf runtime's descriptors; Wire has no descriptor API, so each message that the
// checkin format parses positionally declares its scalar fields in a [MessageSpec].
// Declaration order is the column order, exactly as in the .proto file.

package com.leekleak.batteryarcheologist.core.checkin.checkinparse

import com.leekleak.batteryarcheologist.core.checkin.sliceparse.SliceParseException
import com.leekleak.batteryarcheologist.core.checkin.sliceparse.SliceReader

/** Fatal checkin parsing problem. Unknown checkin sections are warnings, not this. */
class CheckinParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Scalar type of a checkin column. */
internal enum class FieldType {
    STRING,
    INT32,
    INT64,
    FLOAT,
    DOUBLE,
    BOOL,
    ENUM,
    ;

    val isNumeric: Boolean
        get() = this == INT32 || this == INT64 || this == FLOAT || this == DOUBLE
}

/** One scalar field of a Wire message builder, in .proto declaration order. */
internal class FieldSpec<B : Any>(
    val name: String,
    val type: FieldType,
    val get: (B) -> Any?,
    val set: (B, Any) -> Unit,
)

/** Scalar fields and a fresh builder factory for one Wire message. */
internal class MessageSpec<B : Any>(
    val fields: List<FieldSpec<B>>,
    val factory: () -> B,
)

/**
 * Parses [record] into [builder] field by field.
 *
 * If the record has more fields than the proto, the extra fields are ignored and the
 * upstream warning text is returned. A short record is fine: the remaining proto fields
 * are simply left unset. Invalid values throw [CheckinParseException].
 *
 * @return a warning, or null when no warning was produced.
 */
internal fun <B : Any> parseLine(
    section: String,
    record: List<String>,
    builder: B,
    spec: MessageSpec<B>,
): String? {
    val fields = spec.fields
    val num = minOf(record.size, fields.size)
    val warning = if (record.size > fields.size) {
        "The underlying format for $section has ${record.size - fields.size} additional " +
            "field(s) that are not captured."
    } else {
        null
    }
    for (i in 0 until num) {
        val field = fields[i]
        val value = try {
            parseValue(record[i], field)
        } catch (e: SliceParseException) {
            throw CheckinParseException("error parsing $section: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw CheckinParseException("error parsing $section: ${e.message}", e)
        }
        field.set(builder, value)
    }
    return warning
}

/**
 * Parses [record] into a fresh instance of [spec]'s message type, then adds every parsed
 * numeric value to [target].
 */
internal fun <B : Any> parseAndAccumulate(
    section: String,
    record: List<String>,
    target: B,
    spec: MessageSpec<B>,
): String? {
    val temp = spec.factory()
    val warning = parseLine(section, record, temp, spec)
    accumulate(target, temp, spec)
    return warning
}

/**
 * Adds the scalar numeric fields of [source] to [target].
 *
 * String, enum and message fields are left alone. [skipFields] lets callers protect
 * numeric identifier fields (for example a sensor number) from being summed.
 */
internal fun <B : Any> accumulate(
    target: B,
    source: B,
    spec: MessageSpec<B>,
    skipFields: Set<String> = emptySet(),
) {
    for (field in spec.fields) {
        if (field.name in skipFields) continue
        if (!field.type.isNumeric) continue

        val add = field.get(source) ?: continue
        val current = field.get(target)
        val sum: Number = when (field.type) {
            FieldType.INT32 -> (current as? Int ?: 0) + (add as Int)
            FieldType.INT64 -> (current as? Long ?: 0L) + (add as Long)
            FieldType.FLOAT -> (current as? Float ?: 0f) + (add as Float)
            FieldType.DOUBLE -> (current as? Double ?: 0.0) + (add as Double)
            else -> continue
        }
        field.set(target, sum)
    }
}

/** Converts [raw] to the Kotlin value expected by [field]'s setter. */
private fun parseValue(raw: String, field: FieldSpec<*>): Any {
    return when (field.type) {
        FieldType.STRING -> raw

        FieldType.INT32 -> SliceReader.parseInt32(raw)
        FieldType.INT64 -> SliceReader.parseInt64(raw)

        FieldType.FLOAT -> raw.toFloatOrNull()
            ?: throw SliceParseException("cannot parse float32 from '$raw'")

        FieldType.DOUBLE -> raw.toDoubleOrNull()
            ?: throw SliceParseException("cannot parse float64 from '$raw'")

        FieldType.BOOL -> when (raw.lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> throw SliceParseException("cannot parse bool from '$raw'")
        }

        FieldType.ENUM ->
            throw CheckinParseException("enum fields are not supported by parseLine (${field.name})")
    }
}
