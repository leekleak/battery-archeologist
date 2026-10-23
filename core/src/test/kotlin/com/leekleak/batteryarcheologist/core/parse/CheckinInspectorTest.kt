package com.leekleak.batteryarcheologist.core.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckinInspectorTest {
    @Test
    fun `extracts version and history counts`() {
        val raw = """
            9,0,i,vers,36,305,K,L
            9,0,l,bt,0,1000,2000,3000,4000,1700000000000,500,600
            9,hsp,0,1000,"com.example"
            9,h,0:RESET:TIME:1700000000000
            9,h,10:START
            9,h,20:+screen=1
        """.trimIndent()

        val result = CheckinInspector.inspect(raw)

        assertEquals(36, result.checkinVersion)
        assertEquals(4, result.historyLines + result.historyStringPoolLines)
        assertEquals(3, result.historyLines)
        assertEquals(1, result.historyStringPoolLines)
        assertEquals(1, result.resetEvents)
        assertTrue(result.hasBatteryTime)
    }
}
