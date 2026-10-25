package com.leekleak.batteryarcheologist.core.analytics

import com.leekleak.batteryarcheologist.core.checkin.checkinparse.CheckinParser
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppPowerStatsTest {
    @Test
    fun `computes device percentages from capacity`() {
        val stats = BatteryStats(
            system = BatteryStats.System(
                power_use_summary = BatteryStats.System.PowerUseSummary(
                    battery_capacity_mah = 3000f,
                ),
            ),
            app = listOf(
                BatteryStats.App(
                    name = "com.example.app",
                    uid = 10123,
                    power_use_item = BatteryStats.App.PowerUseItem(
                        computed_power_mah = 0.0209f,
                    ),
                ),
            ),
        )

        val report = AppPowerStats.calculate(stats)
        assertEquals(3000f, report.capacityMah, 0f)
        assertTrue(report.isAvailable)
        val app = report.apps.single()
        assertEquals(0.0209f, app.devicePowerMah, 1e-7f)
        assertEquals(0.0006967f, app.devicePowerPct, 1e-6f)
        assertEquals("0.001%", AppPowerStats.formatPercent(app.devicePowerPct))
    }

    @Test
    fun `falls back to estimated battery capacity`() {
        val stats = BatteryStats(
            system = BatteryStats.System(
                battery = BatteryStats.System.Battery(
                    estimated_battery_capacity_mah = 2500L,
                ),
            ),
            app = listOf(
                BatteryStats.App(
                    name = "app",
                    uid = 10001,
                    power_use_item = BatteryStats.App.PowerUseItem(
                        computed_power_mah = 25f,
                    ),
                ),
            ),
        )

        val report = AppPowerStats.calculate(stats)
        assertTrue(report.capacityEstimated)
        assertEquals(2500f, report.capacityMah, 0f)
        assertEquals("1%", AppPowerStats.formatPercent(report.apps.single().devicePowerPct))
    }

    @Test
    fun `formatPercent drops trailing zeros but keeps tiny values visible`() {
        assertEquals("0%", AppPowerStats.formatPercent(0f))
        assertEquals("1%", AppPowerStats.formatPercent(1f))
        assertEquals("1423%", AppPowerStats.formatPercent(1423f))
        assertEquals("0.614%", AppPowerStats.formatPercent(0.614f))
        assertEquals("0.02%", AppPowerStats.formatPercent(0.02f))
        assertEquals("<0.001%", AppPowerStats.formatPercent(0.0004f))
    }

    @Test
    fun `zero capacity produces unavailable predictions`() {
        val stats = BatteryStats(
            app = listOf(
                BatteryStats.App(
                    name = "app",
                    uid = 10001,
                    power_use_item = BatteryStats.App.PowerUseItem(
                        computed_power_mah = 1f,
                    ),
                ),
            ),
        )

        val report = AppPowerStats.calculate(stats)
        assertTrue(!report.isAvailable)
        assertEquals(0f, report.apps.single().devicePowerPct, 0f)
        assertEquals("0%", AppPowerStats.formatPercent(report.apps.single().devicePowerPct))
    }

    @Test
    fun `api36 fixture produces thousandth-percent estimates`() {
        val fixture = listOf(
            File("../tools/fixtures/api36/checkin.txt"),
            File("tools/fixtures/api36/checkin.txt"),
            File("../../tools/fixtures/api36/checkin.txt"),
        ).firstOrNull { it.isFile } ?: error("api36 fixture not found")

        val result = CheckinParser.parse(fixture.readText(), emptyList())
        val stats = result.stats ?: error("checkin parser returned no stats")
        val report = AppPowerStats.calculate(stats)

        assertEquals(3000f, report.capacityMah, 0f)
        assertTrue(report.apps.isNotEmpty())
        val top = report.apps.first()
        assertEquals("com.leekleak.batteryarcheologist", top.name)
        assertEquals("0.001%", AppPowerStats.formatPercent(top.devicePowerPct))
        assertTrue(top.devicePowerMah > 0f)
    }
}
