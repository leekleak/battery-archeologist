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
//
// Kotlin port of third_party/battery-historian/bugreportutils/bugreportutils_test.go,
// with extra cases for extraction, content detection and dumpstate parsing.

package com.leekleak.batteryarcheologist.core.bugreport

import com.leekleak.batteryarcheologist.core.historianutils.HistorianUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BugreportUtilsTest {
    @Test
    fun `converts timestamps with fractions to unix ms`() {
        assertEquals(
            1432842627636L,
            BugreportUtils.timeStampToMs("2015-05-28 19:50:27", "636636", ZoneOffset.UTC),
        )
        assertEquals(
            1432842627636L - 3600000L,
            BugreportUtils.timeStampToMs("2015-05-28 19:50:27", "636636", ZoneOffset.ofHours(1)),
        )

        val missingLocation = assertThrows(IllegalArgumentException::class.java) {
            BugreportUtils.timeStampToMs("2015-05-28 19:50:27", "636636", null)
        }
        assertEquals("missing location", missingLocation.message)

        assertEquals(
            1432842627000L,
            BugreportUtils.timeStampToMs("2015-05-28 19:50:27", "", ZoneOffset.UTC),
        )
        // Go's time.Parse accepts an embedded fraction but Unix() drops it; only the
        // separately supplied remainder contributes milliseconds.
        assertEquals(
            1432842627600L,
            BugreportUtils.timeStampToMs("2015-05-28 19:50:27.6", "6", ZoneOffset.UTC),
        )
        assertThrows(NumberFormatException::class.java) {
            BugreportUtils.timeStampToMs("2015-05-28 19:50:27", "abc", ZoneOffset.UTC)
        }
    }

    @Test
    fun `converts second fractions to milliseconds`() {
        assertEquals(123L, BugreportUtils.secFractionAsMs("123456"))
        assertEquals(0L, BugreportUtils.secFractionAsMs(""))
        assertEquals(600L, BugreportUtils.secFractionAsMs("6"))
    }

    @Test
    fun `extracts time zones`() {
        val london = lines(
            "========================================================",
            "== dumpstate: 2015-07-07 18:07:00",
            "========================================================",
            "",
            "Build: LYZ28H",
            "...",
            "[persist.sys.localevar]: []",
            "[persist.sys.media.use-awesome]: [true]",
            "[persist.sys.profiler_ms]: [0]",
            "[persist.sys.timezone]: [Europe/London]",
        )
        assertEquals("Europe/London", BugreportUtils.timeZone(london).id)

        val losAngeles = lines(
            "========================================================",
            "== dumpstate: 2015-07-31 09:20:54",
            "========================================================",
            "",
            "Build: shamu-userdebug M MRA16G 2097933 dev-keys",
            "..",
            "[persist.sys.qc.sub.rdump.on]: [0]",
            "[persist.sys.timezone]: [America/Los_Angeles]",
            "[persist.sys.usb.config]: [adb]",
            "[ril.baseband.config.version]: [SHAMU_TMO_CUST]",
        )
        assertEquals("America/Los_Angeles", BugreportUtils.timeZone(losAngeles).id)

        val invalid = lines(
            "========================================================",
            "== dumpstate: 2015-07-31 09:20:54",
            "========================================================",
            "[persist.sys.timezone]: [Invalid]",
        )
        val invalidZone = assertThrows(BugReportException::class.java) {
            BugreportUtils.timeZone(invalid)
        }
        assertTrue(invalidZone.message!!.contains("unknown time zone Invalid"))

        val missing = lines(
            "========================================================",
            "== dumpstate: 2015-07-31 09:20:54",
            "========================================================",
            "[persist.sys.qc.sub.rdump.on]: [0]",
            "[persist.sys.usb.config]: [adb]",
        )
        assertEquals("UTC", BugreportUtils.timeZone(missing).id)
    }

    @Test
    fun `parses meta info with all entries`() {
        val input = lines(
            "Build: LRX22C",
            "Build fingerprint: 'google/hammerhead/hammerhead:5.0.1/LRX22C/1602158:user/release-keys'",
            "Bootloader: HHZ12d",
            "Radio: msm",
            "Network: T-Mobile",
            "...",
            "[ro.build.id]: [LRX22C]",
            "[ro.build.version.sdk]: [21]",
            // Space intentionally added to make sure it doesn't affect extraction.
            " [ro.product.model]: [Nexus 5]",
            "...",
            "Client:",
            "  DeviceID: 123456789012345678",
            "DUMP OF SERVICE sensorservice:",
            "Sensor List:",
            // The spaces are actually found in bug reports.
            """AK8963 Magnetometer Uncalibrated| AKM       | version=1 |android.sensor.magnetic_field_uncalibrated| 0x0000000b | "" | type=14 | continuous | minRate=1.00Hz | maxRate=60.00Hz | FifoMax=1500 events | non-wakeUp | last=<>""",
            """AMD            | QTI       | version=1 |                    | 0x00000015 | "" | type=33171006 | on-change | maxDelay=0us |minDelay=0us |no batching | non-wakeUp | last=<>""",
            // Same name, different types.
            """APDS-9930/QPDS-T930 Proximity & Light| Avago     | version=2 |android.sensor.proximity| 0x00000024 | "" | type=8 | on-change | maxDelay=0us |minDelay=0us |no batching | wakeUp | last=<> """,
            """APDS-9930/QPDS-T930 Proximity & Light| Avago     | version=2 |android.sensor.light| 0x00000001 | "" | type=5 | on-change | maxDelay=0us |minDelay=0us |FifoMax=240 events | non-wakeUp | last 10 events = < 1)   8.2,  0.0,  0.0,159312324052923 16:15:19 2)   8.7,  0.0,  0.0,159312374406927 16:15:19 3)   9.0,  0.0,  0.0,159312424760931 16:15:19 4)   9.2,  0.0,  0.0,159315748125189 16:15:22 5)   9.7,  0.0,  0.0,159315798479193 16:15:22 6)  10.2,  0.0,  0.0,159315848833197 16:15:22 7)  10.7,  0.0,  0.0,159315899187201 16:15:22 8)  11.0,  0.0,  0.0,159315949541205 16:15:22 9)  11.2,  0.0,  0.0,159316050249212 16:15:22 10)  11.7,  0.0,  0.0,159316100603216 16:15:22 >""",
            // L format.
            """Accelerometer Sensor| HTC Group Ltd.| android.sensor.accelerometer| 0x00000002 | "" | type=1 | continuous | minRate=5.00Hz | maxRate=100.00Hz | FifoMax=1220 events | non-wakeUp | last=<  0.2, -0.0,  9.7, 2340745539166063>""",
            """Gyroscope Sensor (WAKE_UP)| HTC Group Ltd.| android.sensor.gyroscope| 0x0000000c | "" | type=4 | continuous | minRate=5.00Hz | maxRate=100.00Hz | FifoMax=1220 events | wakeUp | last=<  0.0,  0.0,  0.0, 0>""",
            // Format as of NRD42C. Sensor 0 doesn't have 0x.
            "0000000000) CrosEC Compass            | Google          | ver: 1 | type: android.sensor.magnetic_field(2) | perm: n/a",
            "0x00000101) CrosEC Significant Motion | Google          | ver: 1 | type: android.sensor.significant_motion(17) | perm: n/a",
        )

        val want = MetaInfo(
            deviceId = "123456789012345678",
            sdkVersion = 21,
            buildFingerprint = "google/hammerhead/hammerhead:5.0.1/LRX22C/1602158:user/release-keys",
            modelName = "Nexus 5",
            sensors = mapOf(
                -10000 to SensorInfo(name = "GPS", number = -10000),
                0 to SensorInfo(
                    name = "CrosEC Compass",
                    type = "android.sensor.magnetic_field",
                    version = 1,
                    number = 0,
                ),
                1 to SensorInfo(
                    name = "APDS-9930/QPDS-T930 Proximity & Light",
                    type = "android.sensor.light",
                    version = 2,
                    number = 1,
                ),
                2 to SensorInfo(
                    name = "Accelerometer Sensor",
                    type = "android.sensor.accelerometer",
                    number = 2,
                ),
                11 to SensorInfo(
                    name = "AK8963 Magnetometer Uncalibrated",
                    type = "android.sensor.magnetic_field_uncalibrated",
                    version = 1,
                    number = 11,
                ),
                12 to SensorInfo(
                    name = "Gyroscope Sensor (WAKE_UP)",
                    type = "android.sensor.gyroscope",
                    number = 12,
                ),
                21 to SensorInfo(name = "AMD", version = 1, number = 21),
                36 to SensorInfo(
                    name = "APDS-9930/QPDS-T930 Proximity & Light",
                    type = "android.sensor.proximity",
                    version = 2,
                    number = 36,
                ),
                257 to SensorInfo(
                    name = "CrosEC Significant Motion",
                    type = "android.sensor.significant_motion",
                    version = 1,
                    number = 257,
                ),
            ),
        )

        assertEquals(want, BugreportUtils.parseMetaInfo(input))
    }

    @Test
    fun `parses meta info without device id`() {
        val input = lines(
            "Build: LRX22C",
            "Build fingerprint: 'google/hammerhead/hammerhead:5.0.1/LRX22C/1602158:user/release-keys'",
            "Bootloader: HHZ12d",
            "Radio: msm",
            "Network: T-Mobile",
            "...",
            "[ro.build.id]: [LRX22C]",
            "[ro.build.version.sdk]: [21]",
            "[ro.product.model]: [Nexus 6]",
            "...",
            "Client:",
        )

        val want = MetaInfo(
            deviceId = "not available",
            sdkVersion = 21,
            buildFingerprint = "google/hammerhead/hammerhead:5.0.1/LRX22C/1602158:user/release-keys",
            modelName = "Nexus 6",
            sensors = mapOf(-10000 to SensorInfo(name = "GPS", number = -10000)),
        )

        assertEquals(want, BugreportUtils.parseMetaInfo(input))
    }

    @Test
    fun `uses only the first build fingerprint`() {
        val input = lines(
            "Build: shamu-userdebug 6.0 MRA58E 2219288 dev-keys",
            "Build fingerprint: 'google/shamu/shamu:6.0/MRA58E/2219288:userdebug/dev-keys'",
            "Bootloader: moto-apq8084-71.15",
            "Radio: msm",
            "Network: (unknown)",
            // There can be multiple instances of the following 3 lines in bug reports,
            // and in some cases, the build fingerprint will not be the correct one.
            "----- pid 10754 at 2015-08-17 01:11:07 -----",
            "Cmd line: random.package.name",
            "Build fingerprint: 'google/shamu/shamu:6.0/MRA42/2155602:userdebug/dev-keys'",
            "...",
            "[ro.build.id]: [MRA58E]",
            "[ro.build.version.sdk]: [23]",
            "[ro.product.model]: [Nexus 6]",
            "...",
            "Client:",
            "  DeviceID: 123456789012345678",
        )

        val want = MetaInfo(
            deviceId = "123456789012345678",
            sdkVersion = 23,
            buildFingerprint = "google/shamu/shamu:6.0/MRA58E/2219288:userdebug/dev-keys",
            modelName = "Nexus 6",
            sensors = mapOf(-10000 to SensorInfo(name = "GPS", number = -10000)),
        )

        assertEquals(want, BugreportUtils.parseMetaInfo(input))
    }

    @Test
    fun `fails meta info without sdk version`() {
        val error = assertThrows(BugReportException::class.java) {
            BugreportUtils.parseMetaInfo(
                lines(
                    "Build fingerprint: 'google/hammerhead/hammerhead:5.0.1/LRX22C/1602158:user/release-keys'",
                    "------ SYSTEM LOG ------",
                ),
            )
        }

        assertEquals("unable to find device SDK version", error.message)
    }

    @Test
    fun `extracts PID mappings`() {
        val input = lines(
            "  PID mappings:",
            "    PID #659: ProcessRecord{9b4f852 659:com.motorola.targetnotif/u0a124}",
            "    PID #1422: ProcessRecord{96225d2 1422:com.google.android.apps.shopping.express/u0a183}",
            "    PID #1805: ProcessRecord{e2a1678 1805:com.facebook.katana/u0a157}",
        )

        val result = BugreportUtils.extractPIDMappings(input)

        assertEquals(
            mapOf(
                "659" to listOf(AppInfo("com.motorola.targetnotif", "10124")),
                "1422" to listOf(AppInfo("com.google.android.apps.shopping.express", "10183")),
                "1805" to listOf(AppInfo("com.facebook.katana", "10157")),
            ),
            result.mappings,
        )
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `keeps duplicated PID mappings`() {
        val input = lines(
            "  PID mappings:",
            "    PID #659: ProcessRecord{9b4f852 659:com.motorola.targetnotif/u0a124}",
            "    PID #659: ProcessRecord{96225d2 659:com.google.android.apps.shopping.express/u0a183}",
        )

        val result = BugreportUtils.extractPIDMappings(input)

        assertEquals(
            mapOf(
                "659" to listOf(
                    AppInfo("com.motorola.targetnotif", "10124"),
                    AppInfo("com.google.android.apps.shopping.express", "10183"),
                ),
            ),
            result.mappings,
        )
    }

    @Test
    fun `warns about invalid PID mapping uids`() {
        val input = lines(
            "  PID mappings:",
            "    PID #659: ProcessRecord{9b4f852 659:com.motorola.targetnotif/invaliduid}",
        )

        val result = BugreportUtils.extractPIDMappings(input)

        assertEquals(
            mapOf("659" to listOf(AppInfo("com.motorola.targetnotif"))),
            result.mappings,
        )
        assertEquals(listOf("invalid uid: invaliduid"), result.warnings)
    }

    @Test
    fun `normalizes abbreviated and shared uids`() {
        val input = lines(
            "PID #1: ProcessRecord{abc 1:com.example.app/10001}",
            "PID #2: ProcessRecord{abc 2:com.example.isolated/u0i5}",
            "PID #3: ProcessRecord{abc 3:com.example.system/u0s1234}",
            "PID #4: ProcessRecord{abc 4:com.example.shared/50000}",
        )

        val result = BugreportUtils.extractPIDMappings(input)

        assertEquals("10001", result.mappings["1"]!!.single().uid)
        assertEquals("99005", result.mappings["2"]!!.single().uid)
        assertEquals("1234", result.mappings["3"]!!.single().uid)
        assertEquals("10000", result.mappings["4"]!!.single().uid)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `identifies bug reports`() {
        val report = bugReportText()

        assertTrue(BugreportUtils.isBugReport(report))
        assertTrue(BugreportUtils.isBugReport(report.toByteArray()))

        assertFalse(BugreportUtils.isBugReport("not a bug report"))
        assertFalse(
            BugreportUtils.isBugReport(
                "== dumpstate: 2015-07-07 18:07:00\n------ SYSTEM LOG ------",
            ),
        )
    }

    @Test
    fun `extracts batterystats checkin section`() {
        val input = lines(
            "------ CHECKIN BATTERYSTATS (dumpsys batterystats --checkin) ------",
            "9,0,i,vers,36,305,K,L",
            "  9,0,l,bt,0,1000,2000,3000,4000,1700000000000,500,600  ",
            "------ SYSTEM LOG ------",
            "01-01 00:00:00.000  1000  1000 I SystemServer: boot",
        )

        assertEquals(
            "9,0,i,vers,36,305,K,L\n" +
                "9,0,l,bt,0,1000,2000,3000,4000,1700000000000,500,600",
            BugreportUtils.extractBatterystatsCheckin(input),
        )
    }

    @Test
    fun `extracts bug report from plain text`() {
        val report = bugReportText()

        val extracted = BugreportUtils.extractBugReport("bugreport.txt", report.toByteArray())

        assertEquals("bugreport.txt", extracted.fileName)
        assertEquals(report, extracted.contents)
    }

    @Test
    fun `extracts bug report from zip`() {
        val report = bugReportText()
        val archive = zipOf(mapOf("bugreport.txt" to report.toByteArray()))

        val files = BugreportUtils.contents("bugreport.zip", archive)
        assertEquals(setOf("bugreport.zip~bugreport.txt"), files.keys)

        val extracted = BugreportUtils.extractBugReport("bugreport.zip", archive)
        assertEquals("bugreport.zip~bugreport.txt", extracted.fileName)
        assertEquals(report, extracted.contents)
    }

    @Test
    fun `accepts gzipped bug reports`() {
        val report = bugReportText()

        val files = BugreportUtils.contents(
            "bugreport.txt.gz",
            HistorianUtils.gzipCompress(report.toByteArray()),
        )

        assertEquals(setOf("bugreport.txt.gz"), files.keys)
        assertArrayEquals(report.toByteArray(), files.getValue("bugreport.txt.gz"))
    }

    @Test
    fun `rejects unknown file formats`() {
        val error = assertThrows(BugReportException::class.java) {
            BugreportUtils.contents("image.bin", byteArrayOf(0x00, 0x01, 0x02, 0x03))
        }

        assertTrue(error.message!!.contains("incorrect file format detected"))
    }

    @Test
    fun `fails when archive has no bug report`() {
        val archive = zipOf(mapOf("notes.txt" to "hello".toByteArray()))

        val error = assertThrows(BugReportException::class.java) {
            BugreportUtils.extractBugReport("bugreport.zip", archive)
        }

        assertEquals("bugreport.zip did not contain a valid bug report", error.message)
    }

    @Test
    fun `parses dumpstate time in the report timezone`() {
        val input = lines(
            "== dumpstate: 2015-07-07 18:07:00",
            "[persist.sys.timezone]: [Europe/London]",
            "------ SYSTEM LOG ------",
        )

        assertEquals(
            ZonedDateTime.of(2015, 7, 7, 18, 7, 0, 0, ZoneId.of("Europe/London")),
            BugreportUtils.dumpState(input),
        )
    }

    @Test
    fun `fails dumpstate parsing without a dumpstate line`() {
        val error = assertThrows(BugReportException::class.java) {
            BugreportUtils.dumpState("[persist.sys.timezone]: [Europe/London]")
        }

        assertEquals("could not find dumpstate information in bugreport", error.message)
    }

    private fun bugReportText(): String = lines(
        "========================================================",
        "== dumpstate: 2015-07-07 18:07:00",
        "========================================================",
        "",
        "Build: LRX22C",
        "Build fingerprint: 'google/hammerhead/hammerhead:5.0.1/LRX22C/1602158:user/release-keys'",
        "[ro.build.version.sdk]: [21]",
        "[ro.product.model]: [Nexus 5]",
        "[persist.sys.timezone]: [Europe/London]",
        "------ SYSTEM LOG ------",
        "01-01 00:00:00.000  1000  1000 I SystemServer: boot",
    )

    private fun lines(vararg input: String): String = input.joinToString("\n")

    private fun zipOf(files: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, data) in files) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
