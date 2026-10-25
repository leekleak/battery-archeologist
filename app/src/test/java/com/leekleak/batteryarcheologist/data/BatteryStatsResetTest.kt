package com.leekleak.batteryarcheologist.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryStatsResetTest {
    @Test
    fun successMessageIsAccepted() {
        assertNull(batteryStatsResetError(0, "Battery stats reset.", ""))
    }

    @Test
    fun emptyOutputIsAccepted() {
        assertNull(batteryStatsResetError(0, "", ""))
    }

    @Test
    fun permissionDenialWithExitZeroFails() {
        val message = batteryStatsResetError(
            exitCode = 0,
            stdout = "",
            stderr = "Permission Denial: android.permission.DUMP from pid=1234",
        )

        assertNotNull(message)
        assertTrue(message.orEmpty().contains("Permission Denial"))
    }

    @Test
    fun unknownOptionWithExitZeroFails() {
        val message = batteryStatsResetError(
            exitCode = 0,
            stdout = "Unknown option: --reset",
            stderr = "",
        )

        assertNotNull(message)
        assertTrue(message.orEmpty().contains("Unknown option"))
    }

    @Test
    fun nonzeroExitPrefersStderr() {
        val message = batteryStatsResetError(
            exitCode = 1,
            stdout = "stdout detail",
            stderr = "Failed to reset battery stats",
        )

        assertEquals("Failed to reset battery stats", message)
    }

    @Test
    fun nonzeroExitWithoutOutputMentionsExitCode() {
        val message = batteryStatsResetError(exitCode = 11, stdout = "", stderr = "")

        assertNotNull(message)
        assertTrue(message.orEmpty().contains("11"))
    }

    @Test
    fun timeoutExitCodeReportsTimeout() {
        val message = batteryStatsResetError(exitCode = -2, stdout = "", stderr = "")

        assertEquals(
            "Reset timed out; battery stats may or may not have been cleared",
            message,
        )
    }
}
