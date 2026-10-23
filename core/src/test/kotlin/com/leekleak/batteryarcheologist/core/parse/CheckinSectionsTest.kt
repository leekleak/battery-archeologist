package com.leekleak.batteryarcheologist.core.parse

import org.junit.Assert.assertEquals
import org.junit.Test

class CheckinSectionsTest {
    private val raw = """
        9,0,i,vers,36,216,K,L
        9,0,l,bt,0,1000,2000,3000,4000,1700000000000,500,600
        9,hsp,0,1000,"com.example"
        9,h,0:RESET:TIME:1700000000000
        9,h,10:+screen=1
    """.trimIndent()

    @Test
    fun `extracts version`() {
        assertEquals(36, CheckinSections.version(raw))
    }

    @Test
    fun `extracts history and string pool lines`() {
        assertEquals(3, CheckinSections.historyLines(raw).size)
        assertEquals(
            listOf(
                "9,hsp,0,1000,\"com.example\"",
                "9,h,0:RESET:TIME:1700000000000",
                "9,h,10:+screen=1",
            ),
            CheckinSections.historyLines(raw),
        )
    }
}
