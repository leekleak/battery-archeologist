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

// Tests for the Kotlin checkin parser. The api36 fixture is the golden capture used by
// the Go oracle harness; selected fields below mirror
// tools/fixtures/api36/expected/checkin-summary.json.

package com.leekleak.batteryarcheologist.core.checkin.checkinparse

import com.leekleak.batteryarcheologist.core.model.PackageInfoModel
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckinParserTest {
    private fun fixture(name: String): String {
        val candidates = listOf(
            File("tools/fixtures/api36/$name"),
            File("../tools/fixtures/api36/$name"),
            File("../../tools/fixtures/api36/$name"),
        )
        return candidates.first { it.isFile }.readText()
    }

    private fun app(stats: Batterystats.BatteryStats, uid: Int): Batterystats.BatteryStats.App =
        stats.appList.first { it.uid == uid }

    @Test
    fun `parses a minimal checkin and warns about unknown sections`() {
        val raw = """
            9,0,i,vers,36,216,BUILD,BUILD
            9,0,i,uid,1000,android
            9,0,l,bt,0,100,200,300,400,1700000000000,0,0,3000
            9,0,l,my-new-section,1,2
            9,1000,l,cpu,10,20,30
        """.trimIndent()

        val result = CheckinParser.parse(raw, emptyList())

        assertTrue(result.errors.toString(), result.errors.isEmpty())
        val stats = requireStats(result.stats)
        assertEquals(36, stats.reportVersion)
        assertEquals(Batterystats.BatteryStats.AggregationType.SINCE_CHARGED, stats.aggregationType)
        assertEquals(1_700_000_000_000_000L, stats.startTimeUsec)
        assertEquals(100f, stats.system.battery.batteryRealtimeMsec, 0f)
        assertEquals(10f, app(stats, 1000).cpu.userTimeMs, 0f)
        assertEquals(20f, app(stats, 1000).cpu.systemTimeMs, 0f)
        assertTrue(result.warnings.contains("unknown data category my-new-section"))
        assertTrue(result.warnings.contains("newer report version 36 found"))
    }

    @Test
    fun `report version 36 wakelock groups are mapped by type letter`() {
        val raw = """
            9,0,i,vers,36,216,BUILD,BUILD
            9,0,i,uid,1000,android
            9,1000,l,wl,lock,5,f,2,3,4,5,7,p,8,9,10,11,1,bp,2,3,4,5,12,w,13,14,15,16
        """.trimIndent()

        val result = CheckinParser.parse(raw, emptyList())

        assertTrue(result.errors.toString(), result.errors.isEmpty())
        val stats = requireStats(result.stats)
        val wakelock = app(stats, 1000).wakelockList.single { it.name == "lock" }
        assertEquals(5f, wakelock.fullTimeMsec, 0f)
        assertEquals(2f, wakelock.fullCount, 0f)
        assertEquals(3L, wakelock.fullCurrentDurationMsec)
        assertEquals(4L, wakelock.fullMaxDurationMsec)
        assertEquals(5L, wakelock.fullTotalDurationMsec)
        assertEquals(7f, wakelock.partialTimeMsec, 0f)
        assertEquals(8f, wakelock.partialCount, 0f)
        assertEquals(9L, wakelock.partialCurrentDurationMsec)
        assertEquals(10L, wakelock.partialMaxDurationMsec)
        assertEquals(11L, wakelock.partialTotalDurationMsec)
        assertEquals(12f, wakelock.windowTimeMsec, 0f)
        assertEquals(13f, wakelock.windowCount, 0f)
        assertEquals(14L, wakelock.windowCurrentDurationMsec)
        assertEquals(15L, wakelock.windowMaxDurationMsec)
        assertEquals(16L, wakelock.windowTotalDurationMsec)
        // The bp (background partial) group has no field in this proto.
        assertTrue(result.warnings.contains("wl has 6 new fields"))
    }

    @Test
    fun `legacy state time layout is used before report version 17`() {
        val raw = """
            9,0,i,vers,16,1,BUILD,BUILD
            9,0,i,uid,1000,android
            9,1000,l,st,10,20,30
        """.trimIndent()

        val result = CheckinParser.parse(raw, emptyList())

        assertTrue(result.errors.toString(), result.errors.isEmpty())
        val stats = requireStats(result.stats)
        val stateTime = app(stats, 1000).stateTime
        assertEquals(10f, stateTime.foregroundTimeMsec, 0f)
        assertEquals(20f, stateTime.activeTimeMsec, 0f)
        assertEquals(30f, stateTime.cachedTimeMsec, 0f)
        assertEquals(0L, stateTime.topTimeMsec)
    }

    @Test
    fun `state time layout from version 17 onwards reports new fields`() {
        val raw = """
            9,0,i,vers,17,1,BUILD,BUILD
            9,0,i,uid,1000,android
            9,1000,l,st,10,20,30,40,50,60
        """.trimIndent()

        val result = CheckinParser.parse(raw, emptyList())

        assertTrue(result.errors.toString(), result.errors.isEmpty())
        val stats = requireStats(result.stats)
        val stateTime = app(stats, 1000).stateTime
        assertEquals(10L, stateTime.topTimeMsec)
        assertEquals(20L, stateTime.foregroundServiceTimeMsec)
        assertEquals(30L, stateTime.topSleepingTimeMsec)
        assertEquals(40f, stateTime.foregroundTimeMsec, 0f)
        assertEquals(50L, stateTime.backgroundTimeMsec)
        assertEquals(60f, stateTime.cachedTimeMsec, 0f)
        assertTrue(result.warnings.none { it.startsWith("st has") })
    }

    @Test
    fun `malformed numeric section is a fatal error`() {
        val raw = """
            9,0,i,vers,36,216,BUILD,BUILD
            9,1000,l,cpu,not,a,number
        """.trimIndent()

        val result = CheckinParser.parse(raw, emptyList())

        assertNull(result.stats)
        assertTrue(result.errors.any { it.contains("error parsing cpu") })
    }

    @Test
    fun `parses the api36 golden fixture`() {
        val raw = fixture("checkin.txt")
        val packages = listOf(
            PackageInfoModel(
                name = "com.google.android.gms",
                uid = 10152,
                versionCode = 1234L,
                versionName = "1.2.3",
            ),
        )

        val result = CheckinParser.parse(raw, packages)

        assertTrue("parser errors: ${result.errors}", result.errors.isEmpty())
        val stats = requireStats(result.stats)

        // Report metadata.
        assertEquals(36, stats.reportVersion)
        assertEquals(Batterystats.BatteryStats.AggregationType.SINCE_CHARGED, stats.aggregationType)
        assertEquals(1_792_753_721_991_000L, stats.startTimeUsec)
        assertEquals(0L, stats.endTimeUsec)
        assertEquals("BE4B.251210.005", stats.build.fingerprint)
        assertEquals(1234, stats.gmsVersion)
        assertEquals(70, stats.appCount)

        // System battery / discharge / misc.
        with(stats.system.battery) {
            assertEquals(0f, startCount, 0f)
            assertEquals(500_973f, batteryRealtimeMsec, 0f)
            assertEquals(500_973f, batteryUptimeMsec, 0f)
            assertEquals(500_973f, totalRealtimeMsec, 0f)
            assertEquals(500_973f, totalUptimeMsec, 0f)
            assertEquals(1_792_753_721_991L, startClockTimeMsec)
            assertEquals(0f, screenOffRealtimeMsec, 0f)
            assertEquals(3000L, estimatedBatteryCapacityMah)
        }
        with(stats.system.batteryDischarge) {
            assertEquals(49f, lowerBound, 0f)
            assertEquals(50f, upperBound, 0f)
            assertEquals(50f, screenOn, 0f)
            assertEquals(0f, screenOff, 0f)
        }
        with(stats.system.misc) {
            assertEquals(500_973f, screenOnTimeMsec, 0f)
            assertEquals(20_399f, mobileActiveTimeMsec, 0f)
            assertEquals(500_973f, interactiveTimeMsec, 0f)
            assertEquals(2f, mobileActiveCount, 0f)
            assertEquals(8108f, mobileActiveUnknownTime, 0f)
        }
        with(stats.system.powerUseSummary) {
            assertEquals(3000f, batteryCapacityMah, 0f)
            assertEquals(1200f, computedPowerMah, 0f)
            assertEquals(1200f, minDrainedPowerMah, 0f)
            assertEquals(1200f, maxDrainedPowerMah, 0f)
        }
        assertEquals(67, stats.system.kernelWakelockCount)
        with(stats.system.kernelWakelockList.first {
            it.name == "PowerManagerService.WakeLocks"
        }) {
            assertEquals(0f, timeMsec, 0f)
            assertEquals(0f, count, 0f)
            assertEquals(-1L, maxDurationMsec)
        }
        with(stats.system.globalNetwork) {
            assertEquals(192f, mobileBytesRx, 0f)
            assertEquals(503_560f, wifiBytesRx, 0f)
            assertEquals(276_546f, wifiBytesTx, 0f)
            assertEquals(2f, mobilePacketsRx, 0f)
            assertEquals(633f, wifiPacketsRx, 0f)
            assertEquals(481f, wifiPacketsTx, 0f)
        }
        assertEquals(500_973f, stats.system.globalWifi.wifiOnTimeMsec, 0f)
        with(stats.system.globalModemController) {
            assertEquals(154L, idleTimeMsec)
            assertEquals(99L, rxTimeMsec)
            assertEquals(6, txCount)
            assertEquals(listOf(0L, 11L, 33L, 66L, 88L, 99L), txList.map { it.timeMsec })
            assertEquals((0 until 6).toList(), txList.map { it.level })
        }

        // 11 recognized pwi names plus the synthesized APP entry.
        assertEquals(12, stats.system.powerUseItemCount)
        assertEquals(
            listOf("APP", "BLUETOOTH", "CELL", "DEFAULT", "DEFAULT", "DEFAULT", "DEFAULT",
                "FLASHLIGHT", "IDLE", "PHONE", "SCREEN", "WIFI"),
            stats.system.powerUseItemList.map { it.name.name }.sorted(),
        )
        assertEquals(0.04068f, stats.system.powerUseItemList.first {
            it.name == Batterystats.BatteryStats.System.PowerUseItem.Name.APP
        }.computedPowerMah, 1e-6f)
        assertEquals(0.0209f, stats.system.powerUseItemList.first {
            it.name == Batterystats.BatteryStats.System.PowerUseItem.Name.SCREEN
        }.computedPowerMah, 1e-9f)

        // Time/count sections. The last six data-connection enum numbers are newer than this
        // proto; they still get entries (values retained) but generated Java proto2 getters
        // cannot expose the unknown enum name.
        assertEquals(23, stats.system.dataConnectionCount)
        assertEquals(500_973f, stats.system.getDataConnection(20).timeMsec, 0f)
        assertEquals(5, stats.system.signalStrengthCount)
        assertEquals(120_000f, stats.system.signalStrengthList.first {
            it.name == Batterystats.BatteryStats.System.SignalStrength.Name.GOOD
        }.timeMsec, 0f)
        assertEquals(4f, stats.system.signalStrengthList.first {
            it.name == Batterystats.BatteryStats.System.SignalStrength.Name.GOOD
        }.count, 0f)
        assertEquals(500_973f, stats.system.wifiSignalStrengthList.first {
            it.name == Batterystats.BatteryStats.System.WifiSignalStrength.Name.GREAT
        }.timeMsec, 0f)
        assertEquals(500_973f, stats.system.wifiStateList.first {
            it.name == Batterystats.BatteryStats.System.WifiState.Name.ON_CONNECTED_STA
        }.timeMsec, 0f)
        assertEquals(500_973f, stats.system.wifiSupplicantStateList.first {
            it.name == Batterystats.BatteryStats.System.WifiSupplicantState.Name.COMPLETED
        }.timeMsec, 0f)
        assertEquals(5, stats.system.screenBrightnessCount)
        assertEquals(500_973f, stats.system.screenBrightnessList.single {
            it.name == Batterystats.BatteryStats.System.ScreenBrightness.Name.MEDIUM
        }.timeMsec, 0f)

        // The system-level rows are also attributed to UID 0, like upstream.
        val root = app(stats, 0)
        assertEquals("ROOT", root.name)
        assertEquals(0.00164f, root.powerUseItem.computedPowerMah, 1e-9f)
        assertEquals(63f, root.cpu.userTimeMs, 0f)
        assertEquals(1014f, root.cpu.systemTimeMs, 0f)
        assertEquals(14, root.processCount)
        assertEquals(256f, root.network.wifiBytesTx, 0f)
        assertEquals(4f, root.network.wifiPacketsTx, 0f)

        // Apps and their common sections.
        val systemUi = app(stats, 10185)
        assertEquals("SYSTEM_UI", systemUi.name)
        assertEquals(500_973L, systemUi.stateTime.topSleepingTimeMsec)
        assertEquals(193f, systemUi.cpu.userTimeMs, 0f)
        assertEquals(57f, systemUi.cpu.systemTimeMs, 0f)

        val gms = app(stats, 10152)
        assertEquals("GOOGLE_SERVICES", gms.name)
        assertEquals(listOf("com.google.android.gms"), gms.childList.map { it.name })
        assertEquals(1234, gms.childList.single().versionCode)
        assertEquals("1.2.3", gms.childList.single().versionName)
        assertEquals(0.00702f, gms.powerUseItem.computedPowerMah, 1e-9f)
        assertEquals(564f, gms.cpu.userTimeMs, 0f)
        assertEquals(135f, gms.cpu.systemTimeMs, 0f)
        assertEquals(500_973L, gms.stateTime.foregroundServiceTimeMsec)
        assertTrue(gms.wakelockList.any { it.name == "GCM_WORK" })
        assertTrue(gms.wakelockList.any { it.name == "GCM_HB_ALARM" })
        assertEquals(3, gms.sensorCount)
        assertEquals(545f, gms.sensorList.first { it.number == 0 }.totalTimeMsec, 0f)
        assertEquals(1089L, gms.sensorList.first { it.number == 0 }.actualTimeMsec)
        with(gms.network) {
            assertEquals(503_224f, wifiBytesRx, 0f)
            assertEquals(276_290f, wifiBytesTx, 0f)
            assertEquals(629f, wifiPacketsRx, 0f)
            assertEquals(477f, wifiPacketsTx, 0f)
            assertEquals(4, wifiWakeupCount)
        }
        assertTrue(gms.wakeupAlarmList.any { it.name.contains("HEARTBEAT_ALARM") })
        assertTrue(gms.processList.any { it.name == "com.google.android.gms.unstable" })

        val androidSystem = app(stats, 1000)
        assertEquals("ANDROID_SYSTEM", androidSystem.name)
        assertEquals(11, androidSystem.childCount)
        assertEquals(0.00842f, androidSystem.powerUseItem.computedPowerMah, 1e-9f)
        assertEquals(1860f, androidSystem.cpu.userTimeMs, 0f)
        assertEquals(3530f, androidSystem.cpu.systemTimeMs, 0f)
        with(androidSystem.network) {
            assertEquals(192f, mobileBytesRx, 0f)
            assertEquals(336f, wifiBytesRx, 0f)
            assertEquals(2f, mobilePacketsRx, 0f)
            assertEquals(4f, wifiPacketsRx, 0f)
            assertEquals(10_159f, mobileActiveTimeMsec, 0f)
            assertEquals(2f, mobileActiveCount, 0f)
        }
        with(androidSystem.modemController) {
            assertEquals(9L, rxTimeMsec)
            assertEquals(2, txCount)
            assertEquals(listOf(0L, 0L), txList.map { it.timeMsec })
        }
        assertEquals(500_429f, androidSystem.sensorList.single { it.number == 0 }.totalTimeMsec, 0f)
        assertEquals(500_973L, androidSystem.sensorList.single { it.number == 0 }.actualTimeMsec)
        assertEquals(
            "android/com.android.server.notification.NotificationHistoryJobService",
            androidSystem.scheduledJobList.single().name,
        )
        assertEquals(1f, androidSystem.scheduledJobList.single().totalTimeMsec, 0f)

        val messaging = app(stats, 10141)
        assertTrue(messaging.wakelockList.any {
            it.name ==
                "*job*r/XXX@com.google.android.apps.messaging/androidx.work.impl.background.systemjob.SystemJobService"
        })
        assertEquals(
            "XXX@com.google.android.apps.messaging/androidx.work.impl.background.systemjob.SystemJobService",
            messaging.scheduledJobList.single().name,
        )
        assertEquals(791f, messaging.scheduledJobList.single().totalTimeMsec, 0f)
        assertEquals(500_973L, messaging.stateTime.foregroundServiceTimeMsec)

        val dialer = app(stats, 10157)
        assertEquals(7f, dialer.stateTime.foregroundTimeMsec, 0f)
        assertEquals(
            "XXX@com.google.android.dialer/androidx.work.impl.background.systemjob.SystemJobService",
            dialer.scheduledJobList.single().name,
        )

        val radio = app(stats, 1001)
        assertEquals("RADIO", radio.name)
        assertEquals(
            listOf(
                "com.android.mms.service",
                "com.android.ons",
                "com.android.phone",
                "com.android.providers.telephony",
                "com.android.stk",
            ),
            radio.childList.map { it.name }.sorted(),
        )
        assertTrue(radio.wakelockList.any { it.name == "*telephony-radio*" })
        assertEquals(500_973L, radio.stateTime.topSleepingTimeMsec)

        val composite = app(stats, 1073)
        assertEquals(
            "com.google.android.cellbroadcastservice|com.google.android.networkstack|" +
                "com.google.android.networkstack.tethering",
            composite.name,
        )
        assertEquals(3, composite.childCount)

        val statsd = app(stats, 1066)
        assertEquals("process:statsd", statsd.name)
        assertEquals(6f, statsd.cpu.userTimeMs, 0f)
        assertEquals(15f, statsd.cpu.systemTimeMs, 0f)

        val unnamed = app(stats, 1092)
        assertEquals("", unnamed.name)

        val appWifi = app(stats, 1010)
        assertEquals("WIFI", appWifi.name)
        assertEquals(1, appWifi.wifiController.txCount)
        assertEquals(0, appWifi.wifiController.getTx(0).level)

        // The four fields the oracle summary highlights for the target app.
        val target = app(stats, 10227)
        assertEquals("com.leekleak.batteryarcheologist", target.name)
        assertEquals(0.0209f, target.powerUseItem.computedPowerMah, 1e-9f)
        assertEquals(500_973L, target.stateTime.topTimeMsec)
        assertEquals(500_973f, target.foreground.totalTimeMsec, 0f)
        assertEquals(24f, target.cpu.userTimeMs, 0f)

        // Warning parity with the oracle manifest: 164 warnings, zero errors, same text.
        assertEquals("warnings: ${result.warnings}", 164, result.warnings.size)
        assertEquals(expectedWarnings().sorted(), result.warnings.sorted())
        listOf(
            "The underlying format for bt has 1 additional field(s) that are not captured.",
            "The underlying format for dc has 4 additional field(s) that are not captured.",
            "dcc has 6 additional field(s) that are not captured.",
            "dct has 6 additional field(s) that are not captured.",
            "newer report version 36 found",
            "st has 1 new fields",
            "wl has 6 new fields",
            "user activity now has 3 additional fields",
            "unknown data category awl",
            "unknown data category ctf",
            "unknown data category jbc",
            "unknown data category rpm",
            "unknown data category wmct",
            "Unknown powerUseItem name camera",
            "Unknown powerUseItem name cpu",
            "Unknown powerUseItem name memory",
            "found unexpected uid 1066 for section \"pwi\"",
            "found unexpected uid 1069 for section \"pwi\"",
            "found unexpected uid 1084 for section \"pwi\"",
            "found unexpected uid 1092 for section \"cpu\"",
        ).forEach { expected ->
            assertTrue("missing warning: $expected", result.warnings.contains(expected))
        }
    }

    private fun <T : Any> requireStats(value: T?): T {
        org.junit.Assert.assertNotNull(value)
        return value!!
    }

    /**
     * Reads the warnings array out of the oracle summary without pulling a JSON library into
     * :core. The fixture's warnings contain no `]`, so a simple string scan is sufficient.
     */
    private fun expectedWarnings(): List<String> {
        val text = fixture("expected/checkin-summary.json")
        val key = text.indexOf("\"warnings\":")
        check(key >= 0) { "warnings array not found in oracle summary" }
        val start = text.indexOf('[', key)
        val end = text.indexOf(']', start)
        check(start > key && end > start) { "unterminated warnings array in oracle summary" }
        val array = text.substring(start, end)
        return Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(array)
            .map { unescapeJson(it.groupValues[1]) }
            .toList()
    }

    private fun unescapeJson(value: String): String {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c != '\\' || i + 1 >= value.length) {
                out.append(c)
                i++
                continue
            }
            when (val escaped = value[i + 1]) {
                '"', '\\', '/' -> out.append(escaped)
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                'u' -> {
                    out.append(value.substring(i + 2, i + 6).toInt(16).toChar())
                    i += 4
                }
                else -> out.append(escaped)
            }
            i += 2
        }
        return out.toString()
    }
}
