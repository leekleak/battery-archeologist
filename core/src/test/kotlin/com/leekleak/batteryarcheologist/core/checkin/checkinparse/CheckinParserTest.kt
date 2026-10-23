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
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats
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

    private fun app(stats: BatteryStats, uid: Int): BatteryStats.App =
        stats.app.first { it.uid.v() == uid }

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
        assertEquals(36, stats.report_version.v())
        assertEquals(BatteryStats.AggregationType.SINCE_CHARGED, stats.aggregation_type)
        assertEquals(1_700_000_000_000_000L, stats.start_time_usec.v())
        assertEquals(100f, stats.system!!.battery!!.battery_realtime_msec.v(), 0f)
        assertEquals(10f, app(stats, 1000).cpu!!.user_time_ms.v(), 0f)
        assertEquals(20f, app(stats, 1000).cpu!!.system_time_ms.v(), 0f)
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
        val wakelock = app(stats, 1000).wakelock.single { it.name == "lock" }
        assertEquals(5f, wakelock.full_time_msec.v(), 0f)
        assertEquals(2f, wakelock.full_count.v(), 0f)
        assertEquals(3L, wakelock.full_current_duration_msec.v())
        assertEquals(4L, wakelock.full_max_duration_msec.v())
        assertEquals(5L, wakelock.full_total_duration_msec.v())
        assertEquals(7f, wakelock.partial_time_msec.v(), 0f)
        assertEquals(8f, wakelock.partial_count.v(), 0f)
        assertEquals(9L, wakelock.partial_current_duration_msec.v())
        assertEquals(10L, wakelock.partial_max_duration_msec.v())
        assertEquals(11L, wakelock.partial_total_duration_msec.v())
        assertEquals(12f, wakelock.window_time_msec.v(), 0f)
        assertEquals(13f, wakelock.window_count.v(), 0f)
        assertEquals(14L, wakelock.window_current_duration_msec.v())
        assertEquals(15L, wakelock.window_max_duration_msec.v())
        assertEquals(16L, wakelock.window_total_duration_msec.v())
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
        val stateTime = app(stats, 1000).state_time!!
        assertEquals(10f, stateTime.foreground_time_msec.v(), 0f)
        assertEquals(20f, stateTime.active_time_msec.v(), 0f)
        assertEquals(30f, stateTime.cached_time_msec.v(), 0f)
        assertEquals(0L, stateTime.top_time_msec.v())
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
        val stateTime = app(stats, 1000).state_time!!
        assertEquals(10L, stateTime.top_time_msec.v())
        assertEquals(20L, stateTime.foreground_service_time_msec.v())
        assertEquals(30L, stateTime.top_sleeping_time_msec.v())
        assertEquals(40f, stateTime.foreground_time_msec.v(), 0f)
        assertEquals(50L, stateTime.background_time_msec.v())
        assertEquals(60f, stateTime.cached_time_msec.v(), 0f)
        assertTrue(result.warnings.none { it.startsWith("st has") })
    }

    @Test
    fun `checkin uid repairs package entries uploaded with uid zero`() {
        val raw = """
            9,0,i,vers,36,216,BUILD,BUILD
            9,0,i,uid,10123,com.example.app
        """.trimIndent()
        val packages = listOf(PackageInfoModel(name = "com.example.app", uid = 0))

        val result = CheckinParser.parse(raw, packages)

        assertTrue(result.errors.toString(), result.errors.isEmpty())
        val stats = requireStats(result.stats)
        val parsed = stats.app.single()
        assertEquals(10123, parsed.uid.v())
        assertEquals("com.example.app", parsed.child.single().name.v())
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
        val system = requireNotNull(stats.system)
        val reportBuild = requireNotNull(stats.build)

        // Report metadata.
        assertEquals(36, stats.report_version.v())
        assertEquals(BatteryStats.AggregationType.SINCE_CHARGED, stats.aggregation_type)
        assertEquals(1_792_753_721_991_000L, stats.start_time_usec.v())
        assertEquals(0L, stats.end_time_usec.v())
        assertEquals("BE4B.251210.005", reportBuild.fingerprint)
        assertEquals(1234, stats.gms_version.v())
        assertEquals(70, stats.app.size)

        // System battery / discharge / misc.
        with(system.battery!!) {
            assertEquals(0f, start_count.v(), 0f)
            assertEquals(500_973f, battery_realtime_msec.v(), 0f)
            assertEquals(500_973f, battery_uptime_msec.v(), 0f)
            assertEquals(500_973f, total_realtime_msec.v(), 0f)
            assertEquals(500_973f, total_uptime_msec.v(), 0f)
            assertEquals(1_792_753_721_991L, start_clock_time_msec.v())
            assertEquals(0f, screen_off_realtime_msec.v(), 0f)
            assertEquals(3000L, estimated_battery_capacity_mah.v())
        }
        with(system.battery_discharge!!) {
            assertEquals(49f, lower_bound.v(), 0f)
            assertEquals(50f, upper_bound.v(), 0f)
            assertEquals(50f, screen_on.v(), 0f)
            assertEquals(0f, screen_off.v(), 0f)
        }
        with(system.misc!!) {
            assertEquals(500_973f, screen_on_time_msec.v(), 0f)
            assertEquals(20_399f, mobile_active_time_msec.v(), 0f)
            assertEquals(500_973f, interactive_time_msec.v(), 0f)
            assertEquals(2f, mobile_active_count.v(), 0f)
            assertEquals(8108f, mobile_active_unknown_time.v(), 0f)
        }
        with(system.power_use_summary!!) {
            assertEquals(3000f, battery_capacity_mah.v(), 0f)
            assertEquals(1200f, computed_power_mah.v(), 0f)
            assertEquals(1200f, min_drained_power_mah.v(), 0f)
            assertEquals(1200f, max_drained_power_mah.v(), 0f)
        }
        assertEquals(67, system.kernel_wakelock.size)
        with(system.kernel_wakelock.first {
            it.name == "PowerManagerService.WakeLocks"
        }) {
            assertEquals(0f, time_msec.v(), 0f)
            assertEquals(0f, count.v(), 0f)
            assertEquals(-1L, max_duration_msec.v())
        }
        with(system.global_network!!) {
            assertEquals(192f, mobile_bytes_rx.v(), 0f)
            assertEquals(503_560f, wifi_bytes_rx.v(), 0f)
            assertEquals(276_546f, wifi_bytes_tx.v(), 0f)
            assertEquals(2f, mobile_packets_rx.v(), 0f)
            assertEquals(633f, wifi_packets_rx.v(), 0f)
            assertEquals(481f, wifi_packets_tx.v(), 0f)
        }
        assertEquals(500_973f, system.global_wifi!!.wifi_on_time_msec.v(), 0f)
        with(system.global_modem_controller!!) {
            assertEquals(154L, idle_time_msec.v())
            assertEquals(99L, rx_time_msec.v())
            assertEquals(6, tx.size)
            assertEquals(listOf(0L, 11L, 33L, 66L, 88L, 99L), tx.map { it.time_msec.v() })
            assertEquals((0 until 6).toList(), tx.map { it.level.v() })
        }

        // 11 recognized pwi names plus the synthesized APP entry.
        assertEquals(12, system.power_use_item.size)
        assertEquals(
            listOf("APP", "BLUETOOTH", "CELL", "DEFAULT", "DEFAULT", "DEFAULT", "DEFAULT",
                "FLASHLIGHT", "IDLE", "PHONE", "SCREEN", "WIFI"),
            system.power_use_item.map { it.name?.name ?: "" }.sorted(),
        )
        assertEquals(0.04068f, system.power_use_item.first {
            it.name == BatteryStats.System.PowerUseItem.Name.APP
        }.computed_power_mah.v(), 1e-6f)
        assertEquals(0.0209f, system.power_use_item.first {
            it.name == BatteryStats.System.PowerUseItem.Name.SCREEN
        }.computed_power_mah.v(), 1e-9f)

        // Time/count sections. The last six data-connection enum numbers are newer than this
        // proto; they still get entries (values retained) but the generated enum has no
        // constant for them, so the name is left unset.
        assertEquals(23, system.data_connection.size)
        assertEquals(500_973f, system.data_connection[20].time_msec.v(), 0f)
        assertEquals(5, system.signal_strength.size)
        assertEquals(120_000f, system.signal_strength.first {
            it.name == BatteryStats.System.SignalStrength.Name.GOOD
        }.time_msec.v(), 0f)
        assertEquals(4f, system.signal_strength.first {
            it.name == BatteryStats.System.SignalStrength.Name.GOOD
        }.count.v(), 0f)
        assertEquals(500_973f, system.wifi_signal_strength.first {
            it.name == BatteryStats.System.WifiSignalStrength.Name.GREAT
        }.time_msec.v(), 0f)
        assertEquals(500_973f, system.wifi_state.first {
            it.name == BatteryStats.System.WifiState.Name.ON_CONNECTED_STA
        }.time_msec.v(), 0f)
        assertEquals(500_973f, system.wifi_supplicant_state.first {
            it.name == BatteryStats.System.WifiSupplicantState.Name.COMPLETED
        }.time_msec.v(), 0f)
        assertEquals(5, system.screen_brightness.size)
        assertEquals(500_973f, system.screen_brightness.single {
            it.name == BatteryStats.System.ScreenBrightness.Name.MEDIUM
        }.time_msec.v(), 0f)

        // The system-level rows are also attributed to UID 0, like upstream.
        val root = app(stats, 0)
        assertEquals("ROOT", root.name)
        assertEquals(0.00164f, root.power_use_item!!.computed_power_mah.v(), 1e-9f)
        assertEquals(63f, root.cpu!!.user_time_ms.v(), 0f)
        assertEquals(1014f, root.cpu!!.system_time_ms.v(), 0f)
        assertEquals(14, root.process.size)
        assertEquals(256f, root.network!!.wifi_bytes_tx.v(), 0f)
        assertEquals(4f, root.network!!.wifi_packets_tx.v(), 0f)

        // Apps and their common sections.
        val systemUi = app(stats, 10185)
        assertEquals("SYSTEM_UI", systemUi.name)
        assertEquals(500_973L, systemUi.state_time!!.top_sleeping_time_msec.v())
        assertEquals(193f, systemUi.cpu!!.user_time_ms.v(), 0f)
        assertEquals(57f, systemUi.cpu!!.system_time_ms.v(), 0f)

        val gms = app(stats, 10152)
        assertEquals("GOOGLE_SERVICES", gms.name)
        assertEquals(listOf("com.google.android.gms"), gms.child.map { it.name })
        assertEquals(1234, gms.child.single().version_code.v())
        assertEquals("1.2.3", gms.child.single().version_name)
        assertEquals(0.00702f, gms.power_use_item!!.computed_power_mah.v(), 1e-9f)
        assertEquals(564f, gms.cpu!!.user_time_ms.v(), 0f)
        assertEquals(135f, gms.cpu!!.system_time_ms.v(), 0f)
        assertEquals(500_973L, gms.state_time!!.foreground_service_time_msec.v())
        assertTrue(gms.wakelock.any { it.name == "GCM_WORK" })
        assertTrue(gms.wakelock.any { it.name == "GCM_HB_ALARM" })
        assertEquals(3, gms.sensor.size)
        assertEquals(545f, gms.sensor.first { it.number.v() == 0 }.total_time_msec.v(), 0f)
        assertEquals(1089L, gms.sensor.first { it.number.v() == 0 }.actual_time_msec.v())
        with(gms.network!!) {
            assertEquals(503_224f, wifi_bytes_rx.v(), 0f)
            assertEquals(276_290f, wifi_bytes_tx.v(), 0f)
            assertEquals(629f, wifi_packets_rx.v(), 0f)
            assertEquals(477f, wifi_packets_tx.v(), 0f)
            assertEquals(4, wifi_wakeup_count.v())
        }
        assertTrue(gms.wakeup_alarm.any { it.name.orEmpty().contains("HEARTBEAT_ALARM") })
        assertTrue(gms.process.any { it.name == "com.google.android.gms.unstable" })

        val androidSystem = app(stats, 1000)
        assertEquals("ANDROID_SYSTEM", androidSystem.name)
        assertEquals(11, androidSystem.child.size)
        assertEquals(0.00842f, androidSystem.power_use_item!!.computed_power_mah.v(), 1e-9f)
        assertEquals(1860f, androidSystem.cpu!!.user_time_ms.v(), 0f)
        assertEquals(3530f, androidSystem.cpu!!.system_time_ms.v(), 0f)
        with(androidSystem.network!!) {
            assertEquals(192f, mobile_bytes_rx.v(), 0f)
            assertEquals(336f, wifi_bytes_rx.v(), 0f)
            assertEquals(2f, mobile_packets_rx.v(), 0f)
            assertEquals(4f, wifi_packets_rx.v(), 0f)
            assertEquals(10_159f, mobile_active_time_msec.v(), 0f)
            assertEquals(2f, mobile_active_count.v(), 0f)
        }
        with(androidSystem.modem_controller!!) {
            assertEquals(9L, rx_time_msec.v())
            assertEquals(2, tx.size)
            assertEquals(listOf(0L, 0L), tx.map { it.time_msec.v() })
        }
        assertEquals(500_429f, androidSystem.sensor.single { it.number.v() == 0 }.total_time_msec.v(), 0f)
        assertEquals(500_973L, androidSystem.sensor.single { it.number.v() == 0 }.actual_time_msec.v())
        assertEquals(
            "android/com.android.server.notification.NotificationHistoryJobService",
            androidSystem.scheduled_job.single().name.v(),
        )
        assertEquals(1f, androidSystem.scheduled_job.single().total_time_msec.v(), 0f)

        val messaging = app(stats, 10141)
        assertTrue(messaging.wakelock.any {
            it.name ==
                "*job*r/XXX@com.google.android.apps.messaging/androidx.work.impl.background.systemjob.SystemJobService"
        })
        assertEquals(
            "XXX@com.google.android.apps.messaging/androidx.work.impl.background.systemjob.SystemJobService",
            messaging.scheduled_job.single().name.v(),
        )
        assertEquals(791f, messaging.scheduled_job.single().total_time_msec.v(), 0f)
        assertEquals(500_973L, messaging.state_time!!.foreground_service_time_msec.v())

        val dialer = app(stats, 10157)
        assertEquals(7f, dialer.state_time!!.foreground_time_msec.v(), 0f)
        assertEquals(
            "XXX@com.google.android.dialer/androidx.work.impl.background.systemjob.SystemJobService",
            dialer.scheduled_job.single().name.v(),
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
            radio.child.map { it.name.orEmpty() }.sorted(),
        )
        assertTrue(radio.wakelock.any { it.name == "*telephony-radio*" })
        assertEquals(500_973L, radio.state_time!!.top_sleeping_time_msec.v())

        val composite = app(stats, 1073)
        assertEquals(
            "com.google.android.cellbroadcastservice|com.google.android.networkstack|" +
                "com.google.android.networkstack.tethering",
            composite.name,
        )
        assertEquals(3, composite.child.size)

        val statsd = app(stats, 1066)
        assertEquals("process:statsd", statsd.name)
        assertEquals(6f, statsd.cpu!!.user_time_ms.v(), 0f)
        assertEquals(15f, statsd.cpu!!.system_time_ms.v(), 0f)

        val unnamed = app(stats, 1092)
        assertEquals("", unnamed.name)

        val appWifi = app(stats, 1010)
        assertEquals("WIFI", appWifi.name)
        assertEquals(1, appWifi.wifi_controller!!.tx.size)
        assertEquals(0, appWifi.wifi_controller!!.tx[0].level.v())

        // The four fields the oracle summary highlights for the target app.
        val target = app(stats, 10227)
        assertEquals("com.leekleak.batteryarcheologist", target.name)
        assertEquals(0.0209f, target.power_use_item!!.computed_power_mah.v(), 1e-9f)
        assertEquals(500_973L, target.state_time!!.top_time_msec.v())
        assertEquals(500_973f, target.foreground!!.total_time_msec.v(), 0f)
        assertEquals(24f, target.cpu!!.user_time_ms.v(), 0f)

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

    private fun Float?.v(): Float = this ?: 0f
    private fun Long?.v(): Long = this ?: 0L
    private fun Int?.v(): Int = this ?: 0
    private fun String?.v(): String = this ?: ""
}
