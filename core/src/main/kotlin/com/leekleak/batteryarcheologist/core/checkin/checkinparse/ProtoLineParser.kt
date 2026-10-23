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
// The original code reflects over generated Go structs. The Kotlin port instead uses the
// protobuf runtime's own descriptors, which preserve declaration order and the exact
// scalar field types, so the same "fill fields in proto order" behaviour is retained.
// These helpers only support messages whose fields are all scalar; every message that the
// upstream code parses with parseLine has that shape.

package com.leekleak.batteryarcheologist.core.checkin.checkinparse

import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Message
import com.leekleak.batteryarcheologist.core.checkin.sliceparse.SliceParseException
import com.leekleak.batteryarcheologist.core.checkin.sliceparse.SliceReader

/** Fatal checkin parsing problem. Unknown checkin sections are warnings, not this. */
class CheckinParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Parses [record] into [builder] field by field.
 *
 * If the record has more fields than the proto, the extra fields are ignored and the
 * upstream warning text is returned. A short record is fine: the remaining proto fields
 * are simply left unset. Invalid values throw [CheckinParseException].
 *
 * @return a warning, or null when no warning was produced.
 */
internal fun parseLine(section: String, record: List<String>, builder: Message.Builder): String? {
    val fields = scalarFields(builder)
    var num = record.size
    var warning: String? = null
    if (record.size > fields.size) {
        num = fields.size
        warning = "The underlying format for $section has ${record.size - num} additional " +
            "field(s) that are not captured."
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
        builder.setField(field, value)
    }
    return warning
}

/**
 * Parses [record] into a fresh instance of [target]'s message type, then adds every
 * parsed numeric value to [target].
 */
internal fun parseAndAccumulate(
    section: String,
    record: List<String>,
    target: Message.Builder,
): String? {
    val temp = target.defaultInstanceForType.newBuilderForType()
    val warning = parseLine(section, record, temp)
    accumulate(target, temp.build())
    return warning
}

/**
 * Adds the scalar numeric fields of [source] to [target].
 *
 * String, enum and message fields are left alone. [skipFields] lets callers protect
 * numeric identifier fields (for example a sensor number) from being summed.
 */
internal fun accumulate(
    target: Message.Builder,
    source: Message,
    skipFields: Set<String> = emptySet(),
) {
    val descriptor = target.descriptorForType
    for (field in descriptor.fields) {
        if (field.isRepeated || field.name in skipFields) continue
        if (field.javaType == FieldDescriptor.JavaType.MESSAGE) continue
        if (field.javaType == FieldDescriptor.JavaType.ENUM) continue
        if (!source.hasField(field)) continue

        val add = source.getField(field)
        if (add !is Number) continue

        if (!target.hasField(field)) {
            target.setField(field, add)
            continue
        }
        val current = target.getField(field) as? Number ?: continue
        val sum: Number = when (field.javaType) {
            FieldDescriptor.JavaType.INT -> current.toInt() + add.toInt()
            FieldDescriptor.JavaType.LONG -> current.toLong() + add.toLong()
            FieldDescriptor.JavaType.FLOAT -> current.toFloat() + add.toFloat()
            FieldDescriptor.JavaType.DOUBLE -> current.toDouble() + add.toDouble()
            else -> continue
        }
        target.setField(field, sum)
    }
}

/**
 * Returns the non-repeated, non-message fields of [builder] in declaration order.
 *
 * Enum fields are kept so a caller that accidentally passes an enum-bearing message gets a
 * clear parse error instead of silently shifting every following field.
 */
private fun scalarFields(builder: Message.Builder): List<FieldDescriptor> =
    builder.descriptorForType.fields.filter { field ->
        !field.isRepeated && field.javaType != FieldDescriptor.JavaType.MESSAGE
    }

/** Converts [raw] to the Java value expected by [field]'s setter. */
private fun parseValue(raw: String, field: FieldDescriptor): Any {
    return when (field.type) {
        FieldDescriptor.Type.STRING -> raw
        FieldDescriptor.Type.INT32,
        FieldDescriptor.Type.SINT32,
        FieldDescriptor.Type.SFIXED32,
        FieldDescriptor.Type.UINT32,
        FieldDescriptor.Type.FIXED32,
        -> SliceReader.parseInt32(raw)

        FieldDescriptor.Type.INT64,
        FieldDescriptor.Type.SINT64,
        FieldDescriptor.Type.SFIXED64,
        FieldDescriptor.Type.UINT64,
        FieldDescriptor.Type.FIXED64,
        -> SliceReader.parseInt64(raw)

        FieldDescriptor.Type.FLOAT -> raw.toFloatOrNull()
            ?: throw SliceParseException("cannot parse float32 from '$raw'")

        FieldDescriptor.Type.DOUBLE -> raw.toDoubleOrNull()
            ?: throw SliceParseException("cannot parse float64 from '$raw'")

        FieldDescriptor.Type.BOOL -> when (raw.lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> throw SliceParseException("cannot parse bool from '$raw'")
        }

        FieldDescriptor.Type.ENUM ->
            throw CheckinParseException("enum fields are not supported by parseLine (${field.fullName})")

        else ->
            throw CheckinParseException("unsupported field type ${field.type} for ${field.fullName}")
    }
}
