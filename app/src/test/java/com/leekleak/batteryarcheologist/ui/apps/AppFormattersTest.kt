package com.leekleak.batteryarcheologist.ui.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class AppFormattersTest {

    @Test
    fun `seconds drop trailing zeros`() {
        assertEquals("0 s", formatSeconds(0f))
        assertEquals("83 s", formatSeconds(83_000f))
        assertEquals("83.2 s", formatSeconds(83_200f))
        assertEquals("<0.1 s", formatSeconds(10f))
    }

    @Test
    fun `milliamp hours keep up to four decimals without trailing zeros`() {
        assertEquals("0 mAh", formatMah(0f))
        assertEquals("1423 mAh", formatMah(1423.0000f))
        assertEquals("0.0209 mAh", formatMah(0.0209f))
        assertEquals("0.344 mAh", formatMah(0.3440f))
        assertEquals("<0.0001 mAh", formatMah(0.00001f))
    }
}
