package com.leekleak.batteryarcheologist.core.analytics

import com.leekleak.batteryarcheologist.core.checkin.checkinparse.CheckinParser
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppPowerStatsTest {
    @Test
    fun `computes device and cpu percentages from capacity`() {
        val stats = Batterystats.BatteryStats.newBuilder()
            .setSystem(
                Batterystats.BatteryStats.System.newBuilder()
                    .setPowerUseSummary(
                        Batterystats.BatteryStats.System.PowerUseSummary.newBuilder()
                            .setBatteryCapacityMah(3000f),
                    ),
            )
            .addApp(
                Batterystats.BatteryStats.App.newBuilder()
                    .setName("com.example.app")
                    .setUid(10123)
                    .setPowerUseItem(
                        Batterystats.BatteryStats.App.PowerUseItem.newBuilder()
                            .setComputedPowerMah(0.0209f),
                    )
                    .setCpu(
                        Batterystats.BatteryStats.App.Cpu.newBuilder()
                            .setPowerMaMs(3_600_000f),
                    ),
            )
            .build()

        val report = AppPowerStats.calculate(stats)
        assertEquals(3000f, report.capacityMah, 0f)
        assertTrue(report.isAvailable)
        val app = report.apps.single()
        assertEquals(0.0209f, app.devicePowerMah, 1e-7f)
        assertEquals(0.0006967f, app.devicePowerPct, 1e-6f)
        assertEquals(1f, app.cpuPowerMah, 1e-6f)
        assertEquals(0.0333333f, app.cpuPowerPct, 1e-5f)
        assertEquals("0.001%", AppPowerStats.formatPercent(app.devicePowerPct))
        assertEquals("0.033%", AppPowerStats.formatPercent(app.cpuPowerPct))
    }

    @Test
    fun `falls back to estimated battery capacity`() {
        val stats = Batterystats.BatteryStats.newBuilder()
            .setSystem(
                Batterystats.BatteryStats.System.newBuilder()
                    .setBattery(
                        Batterystats.BatteryStats.System.Battery.newBuilder()
                            .setEstimatedBatteryCapacityMah(2500L),
                    ),
            )
            .addApp(
                Batterystats.BatteryStats.App.newBuilder()
                    .setName("app")
                    .setUid(10001)
                    .setPowerUseItem(
                        Batterystats.BatteryStats.App.PowerUseItem.newBuilder()
                            .setComputedPowerMah(25f),
                    ),
            )
            .build()

        val report = AppPowerStats.calculate(stats)
        assertTrue(report.capacityEstimated)
        assertEquals(2500f, report.capacityMah, 0f)
        assertEquals("1.000%", AppPowerStats.formatPercent(report.apps.single().devicePowerPct))
    }

    @Test
    fun `zero capacity produces unavailable predictions`() {
        val stats = Batterystats.BatteryStats.newBuilder()
            .addApp(
                Batterystats.BatteryStats.App.newBuilder()
                    .setName("app")
                    .setUid(10001)
                    .setPowerUseItem(
                        Batterystats.BatteryStats.App.PowerUseItem.newBuilder()
                            .setComputedPowerMah(1f),
                    ),
            )
            .build()

        val report = AppPowerStats.calculate(stats)
        assertTrue(!report.isAvailable)
        assertEquals(0f, report.apps.single().devicePowerPct, 0f)
        assertEquals("0.000%", AppPowerStats.formatPercent(report.apps.single().devicePowerPct))
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
