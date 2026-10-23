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

// Kotlin tests for the sliceparse port.

package com.leekleak.batteryarcheologist.core.checkin.sliceparse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SliceParseTest {
    @Test
    fun `reads typed values and leaves the remaining tail`() {
        val reader = SliceReader(listOf("name", "42", "9000000000", "1.5", "2.25", "tail"))

        assertEquals("name", reader.readString())
        assertEquals(42, reader.readInt32())
        assertEquals(9_000_000_000L, reader.readInt64())
        assertEquals(1.5f, reader.readFloat32())
        assertEquals(2.25, reader.readFloat64(), 0.0)
        assertEquals(listOf("tail"), reader.remaining)
        assertTrue(reader.hasRemaining)
    }

    @Test
    fun `skips fields like a nil output`() {
        val reader = SliceReader(listOf("a", "f", "1", "b"))

        assertEquals("a", reader.readString())
        reader.skip()
        assertEquals(1, reader.readInt32())
        assertEquals("b", reader.readString())
        assertFalse(reader.hasRemaining)
    }

    @Test
    fun `integer reads ignore a fractional suffix`() {
        assertEquals(7, SliceReader.parseInt32("7.0"))
        assertEquals(-1L, SliceReader.parseInt64("-1.5"))
    }

    @Test
    fun `too few input elements throws`() {
        val reader = SliceReader(listOf("a"))
        reader.readString()
        try {
            reader.readInt32()
            fail("expected SliceParseException")
        } catch (e: SliceParseException) {
            assertTrue(e.message!!.contains("ended at position"))
        }
    }

    @Test
    fun `invalid number throws`() {
        try {
            SliceReader.parseInt32("not-a-number")
            fail("expected SliceParseException")
        } catch (e: SliceParseException) {
            assertTrue(e.message!!.contains("not-a-number"))
        }
    }

    @Test
    fun `skipping past the end throws`() {
        val reader = SliceReader(emptyList())
        try {
            reader.skip()
            fail("expected SliceParseException")
        } catch (e: SliceParseException) {
            assertEquals("input of size 0 ended at position 0", e.message)
        }
    }
}
