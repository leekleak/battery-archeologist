/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Port of TestBatteryLevelSummariesToCSV from
 * third_party/battery-historian/parseutils/parseutils_test.go.
 */

package com.leekleak.batteryarcheologist.core.history.parseutils

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryLevelCsvTest {
    @Test
    fun `writes dimensions and one row per level drop`() {
        val summary = ActivitySummary(HistorySummaryFormat.BATTERY_LEVEL).apply {
            reason = "LEVEL"
            startTimeMs = 1422997326657
            endTimeMs = 1422997348702
            initialBatteryLevel = 100
            finalBatteryLevel = 99

            pluggedInSummary = Dist(1, 2, 0)
            screenOnSummary = Dist(3, 4, 0)
            mobileRadioOnSummary = Dist(5, 6, 0)
            wifiOnSummary = Dist(7, 8, 0)
            cpuRunningSummary = Dist(9, 10, 0)

            gpsOnSummary = Dist(11, 12, 0)
            sensorOnSummary = Dist(13, 14, 0)
            wifiScanSummary = Dist(15, 16, 0)
            wifiFullLockSummary = Dist(17, 18, 0)
            wifiRadioSummary = Dist(19, 20, 0)
            wifiRunningSummary = Dist(21, 22, 0)
            wifiMulticastOnSummary = Dist(23, 24, 0)

            audioOnSummary = Dist(25, 26, 0)
            cameraOnSummary = Dist(27, 28, 0)
            videoOnSummary = Dist(29, 30, 0)
            lowPowerModeOnSummary = Dist(31, 32, 0)
            flashlightOnSummary = Dist(33, 34, 0)
            chargingOnSummary = Dist(35, 36, 0)

            phoneCallSummary = Dist(37, 38, 0)
            phoneScanSummary = Dist(39, 40, 0)
            bleScanSummary = Dist(41, 42, 0)

            totalSyncSummary = Dist(43, 44, 0)
        }

        val expectedDimensionLine = listOf(
            "StartTime",
            "EndTime",
            "Duration",
            "Reason",
            "InitialBatteryLevel",
            "FinalBatteryLevel",
            "LevelDropPerHour",
            "PluggedIn.num",
            "PluggedIn.dur",
            "ScreenOn.num",
            "ScreenOn.dur",
            "MobileRadioOn.num",
            "MobileRadioOn.dur",
            "WifiOn.num",
            "WifiOn.dur",
            "CPURunning.num",
            "CPURunning.dur",
            "GpsOn.num",
            "GpsOn.dur",
            "SensorOn.num",
            "SensorOn.dur",
            "WifiScan.num",
            "WifiScan.dur",
            "WifiFullLock.num",
            "WifiFullLock.dur",
            "WifiRadio.num",
            "WifiRadio.dur",
            "WifiRunning.num",
            "WifiRunning.dur",
            "WifiMulticastOn.num",
            "WifiMulticastOn.dur",
            "AudioOn.num",
            "AudioOn.dur",
            "CameraOn.num",
            "CameraOn.dur",
            "VideoOn.num",
            "VideoOn.dur",
            "LowPowerModeOn.num",
            "LowPowerModeOn.dur",
            "FlashlightOn.num",
            "FlashlightOn.dur",
            "ChargingOn.num",
            "ChargingOn.dur",
            "PhoneCall.num",
            "PhoneCall.dur",
            "PhoneScan.num",
            "PhoneScan.dur",
            "BLEScan.num",
            "BLEScan.dur",
            "TotalSync.num",
            "TotalSync.dur",
        ).joinToString(",") + "\n"

        val expectedValueLine = listOf(
            "1422997326657",
            "1422997348702",
            "22045",
            "LEVEL",
            "100",
            "99",
            "163.302336",
            (1..44).joinToString(","),
        ).joinToString(",") + "\n"

        val withDimensions = StringBuilder()
        BatteryLevelSummariesToCSV(
            withDimensions,
            listOf(summary),
            printDimensions = true,
        )
        assertEquals(expectedDimensionLine + expectedValueLine, withDimensions.toString())

        val withoutDimensions = StringBuilder()
        BatteryLevelSummariesToCSV(
            withoutDimensions,
            listOf(summary),
            printDimensions = false,
        )
        assertEquals(expectedValueLine, withoutDimensions.toString())
    }
}
