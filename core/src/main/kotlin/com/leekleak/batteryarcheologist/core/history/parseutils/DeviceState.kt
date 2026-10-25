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
 * Kotlin port of the timestamped state types (`tsInt`, `tsBool`, `tsString`)
 * and `DeviceState` from third_party/battery-historian/parseutils/parseutils.go.
 */

package com.leekleak.batteryarcheologist.core.history.parseutils

import com.leekleak.batteryarcheologist.core.history.csv.EntryState
import com.leekleak.batteryarcheologist.core.history.csv.Event
import com.leekleak.batteryarcheologist.core.history.csv.Key
import com.leekleak.batteryarcheologist.core.history.csv.State

internal const val TS_STRING_DEFAULT = "default"
internal const val UNKNOWN_SCREEN_ON_REASON = "unknown screen on reason"

/**
 * An integer state with an initial timestamp.
 *
 * Port of Go `tsInt`. Assigning always emits two CSV entries so the previous
 * interval is closed and the new one opened.
 */
internal data class TsInt(
    var startMs: Long = 0L,
    var intValue: Int = 0,
) : EntryState {
    override val startTimeMs: Long get() = startMs
    override val type: String get() = "int"
    override val value: String get() = intValue.toString()

    override fun key(desc: String): Key = Key(desc, "")

    fun assign(
        curTimeMs: Long,
        value: String,
        @Suppress("UNUSED_PARAMETER") summaryActive: Boolean,
        desc: String,
        csvState: State,
    ): String? {
        val parsed = value.toIntOrNull() ?: return "parsing int error for \"$desc\""
        csvState.addEntry(desc, this, curTimeMs)
        intValue = parsed
        startMs = curTimeMs
        csvState.addEntry(desc, this, curTimeMs)
        return null
    }

    fun initStart(curTimeMs: Long) {
        if (startMs != 0L) {
            startMs = curTimeMs
        }
    }
}

/** A boolean state with an initial timestamp. Port of Go `tsBool`. */
internal data class TsBool(
    var startMs: Long = 0L,
    var isOn: Boolean = false,
    /** Extra data associated with this boolean, for example a screen-on reason. */
    var data: String = "",
) : EntryState {
    override val startTimeMs: Long get() = startMs
    override val type: String get() = "bool"
    override val value: String get() = if (isOn) "true" else "false"

    override fun key(desc: String): Key = Key(desc, "")

    /**
     * Counts on negative transitions only, as some booleans merely indicate a
     * state.
     */
    fun assign(
        curTimeMs: Long,
        summaryActive: Boolean,
        summaryStartTimeMs: Long,
        summary: Dist,
        transition: String,
        desc: String,
        csvState: State,
    ): String? {
        val isOnTransition = when (transition) {
            "+" -> true
            "-" -> {
                if (!isOn) {
                    if (summary.num != 0) {
                        return "two negative transitions for \"$desc\":\"$transition\""
                    }
                    // First negative transition for an event that was in
                    // progress at the start of the summary.
                    isOn = true
                    // Set the start time so the CSV entry stores the right value.
                    startMs = summaryStartTimeMs
                    csvState.addEntryWithOpt(desc, this, startMs, data)
                }
                false
            }

            else -> return "unknown transition for \"$desc\":\"$transition\""
        }

        if (startMs == 0L) {
            startMs = summaryStartTimeMs
        }
        // On -> Off
        if (!isOnTransition && isOn) {
            if (summaryActive) {
                val durationMs = curTimeMs - startMs
                if (durationMs > 0) {
                    summary.addDuration(durationMs)
                }
            }
            isOn = false
            csvState.addEntryWithOpt(desc, this, curTimeMs, data)
        }

        // Off -> On
        if (isOnTransition && !isOn) {
            isOn = true
            csvState.addEntryWithOpt(desc, this, curTimeMs, data)
        }
        // Note the time the new state starts.
        startMs = curTimeMs
        return null
    }

    fun initStart(curTimeMs: Long) {
        if (startMs != 0L) {
            startMs = curTimeMs
        }
    }

    fun updateSummary(
        curTimeMs: Long,
        summaryActive: Boolean,
        summaryStartTimeMs: Long,
        summary: Dist,
    ) {
        if (startMs == 0L) {
            startMs = summaryStartTimeMs
        }
        if (isOn) {
            if (summaryActive) {
                summary.addDuration(curTimeMs - startMs)
            }
            startMs = curTimeMs
        }
    }
}

/** A string state with an initial timestamp. Port of Go `tsString`. */
internal data class TsString(
    var startMs: Long = 0L,
    override var value: String = "",
) : EntryState {
    override val startTimeMs: Long get() = startMs
    override val type: String get() = "string"

    override fun key(desc: String): Key = Key(desc, "")

    fun assign(
        curTimeMs: Long,
        summaryActive: Boolean,
        summaryStartTimeMs: Long,
        summary: MutableMap<String, Dist>,
        newValue: String,
        desc: String,
        csvState: State,
    ): String? {
        if (startMs == 0L) {
            startMs = summaryStartTimeMs
        }
        if (value != newValue) {
            if (value != TS_STRING_DEFAULT) {
                csvState.addEntry(desc, this, curTimeMs)
            }
            if (summaryActive && value.isNotEmpty()) {
                val dist = summary.getOrDefault(value, Dist())
                val durationMs = curTimeMs - startMs
                if (durationMs > 0) {
                    dist.addDuration(durationMs)
                    summary[value] = dist
                }
            }
            // Note the time the new state starts.
            startMs = curTimeMs
            value = newValue
            csvState.addEntry(desc, this, curTimeMs)
        }
        return null
    }

    fun initStart(curTimeMs: Long) {
        if (startMs != 0L) {
            startMs = curTimeMs
        }
    }

    fun updateSummary(
        curTimeMs: Long,
        summaryActive: Boolean,
        summaryStartTimeMs: Long,
        summary: MutableMap<String, Dist>,
    ) {
        if (startMs == 0L) {
            startMs = summaryStartTimeMs
        }
        if (value.isEmpty()) {
            value = TS_STRING_DEFAULT
        }
        if (summaryActive) {
            val dist = summary.getOrDefault(value, Dist())
            dist.addDuration(curTimeMs - startMs)
            summary[value] = dist
        }
        startMs = curTimeMs
    }
}

/**
 * The instantaneous device state reconstructed from history events.
 *
 * Port of Go `DeviceState`. Every field that has initialization code in
 * upstream `initStartTimeForAllStates` is initialized here too.
 */
internal class DeviceState {
    var currentTimeMs: Long = 0L
    var lastWakeupTimeMs: Long = 0L
    var lastWakeupDurationMs: Long = 0L
    var isDpstEvent: Boolean = false
    var dpstTokenIndex: Int = 0
    var lastBatteryLevel: TsInt = TsInt()

    var temperature: TsInt = TsInt()
    var voltage: TsInt = TsInt()
    var batteryLevel: TsInt = TsInt()
    var brightness: TsInt = TsInt()
    var coulombCharge: TsInt = TsInt()

    var phoneState: TsString = TsString()
    var dataConnection: TsString = TsString()
    var plugType: TsString = TsString()
    var chargingStatus: TsString = TsString()
    var health: TsString = TsString()
    var wifiSuppl: TsString = TsString()
    var phoneSignalStrength: TsString = TsString()
    var wifiSignalStrength: TsString = TsString()
    var userRunning: TsString = TsString()
    var userForeground: TsString = TsString()
    var idleMode: TsString = TsString()

    var cpuRunning: TsBool = TsBool()
    var sensorOn: TsBool = TsBool()
    var gpsOn: TsBool = TsBool()
    var wifiFullLock: TsBool = TsBool()
    var wifiScan: TsBool = TsBool()
    var wifiMulticastOn: TsBool = TsBool()
    var mobileRadioOn: TsBool = TsBool()
    var wifiOn: TsBool = TsBool()
    var wifiRadio: TsBool = TsBool()
    var wifiRunning: TsBool = TsBool()
    var phoneScanning: TsBool = TsBool()
    var bleScanning: TsBool = TsBool()
    var screenOn: TsBool = TsBool(data = UNKNOWN_SCREEN_ON_REASON)
    var screenDoze: TsBool = TsBool()
    var usbDataLink: TsBool = TsBool()
    var cellularHighTxPower: TsBool = TsBool()
    var gpsSignalQuality: TsString = TsString()
    var nrState: TsString = TsString()
    var plugged: TsBool = TsBool()
    var phoneInCall: TsBool = TsBool()
    var wakeLockHeld: TsBool = TsBool()
    var flashlightOn: TsBool = TsBool()
    var chargingOn: TsBool = TsBool()
    var cameraOn: TsBool = TsBool()
    var videoOn: TsBool = TsBool()
    var audioOn: TsBool = TsBool()
    var lowPowerModeOn: TsBool = TsBool()
    var bluetoothOn: TsBool = TsBool()

    var wakeLockHolder: ServiceUID = ServiceUID()
    var wakeupReason: ServiceUID = ServiceUID()

    val syncIntervals: MutableList<Event> = ArrayList()

    val activeProcessMap: MutableMap<String, ServiceUID> = LinkedHashMap()
    val appSyncingMap: MutableMap<String, ServiceUID> = LinkedHashMap()
    val foregroundProcessMap: MutableMap<String, ServiceUID> = LinkedHashMap()
    val topApplicationMap: MutableMap<String, ServiceUID> = LinkedHashMap()
    val connectivityMap: MutableMap<String, ServiceUID> = LinkedHashMap()
    val longWakelockMap: MutableMap<String, ServiceUID> = LinkedHashMap()
    val scheduledJobMap: MutableMap<String, ServiceUID> = LinkedHashMap()
    val tmpWhiteListMap: MutableMap<String, ServiceUID> = LinkedHashMap()
    val wakeLockMap: MutableMap<String, ServiceUID> = LinkedHashMap()
    val alarmMap: MutableMap<String, ServiceUID> = LinkedHashMap()

    var dpstStats: DPST = DPST()
    var dcpuStats: DCPU = DCPU()

    /**
     * The low power state summary is printed as an aggregate since boot, so
     * this tracks the cumulative values in order to split it per discharge.
     */
    val cumulativePowerState: MutableMap<String, PowerState> = LinkedHashMap()
    val initialPowerState: MutableMap<String, PowerState> = LinkedHashMap()

    /**
     * Re-bases all active states when the device transitions from charging to
     * discharging. State is retained; only accounting start times move to now.
     *
     * Port of Go `initStartTimeForAllStates`.
     */
    fun initStartTimeForAllStates() {
        temperature.initStart(currentTimeMs)
        voltage.initStart(currentTimeMs)
        batteryLevel.initStart(currentTimeMs)
        brightness.initStart(currentTimeMs)
        coulombCharge.initStart(currentTimeMs)
        phoneSignalStrength.initStart(currentTimeMs)
        phoneState.initStart(currentTimeMs)
        dataConnection.initStart(currentTimeMs)
        plugType.initStart(currentTimeMs)
        chargingStatus.initStart(currentTimeMs)
        health.initStart(currentTimeMs)
        cpuRunning.initStart(currentTimeMs)
        sensorOn.initStart(currentTimeMs)
        gpsOn.initStart(currentTimeMs)
        wifiFullLock.initStart(currentTimeMs)
        wifiScan.initStart(currentTimeMs)
        wifiMulticastOn.initStart(currentTimeMs)
        mobileRadioOn.initStart(currentTimeMs)
        wifiOn.initStart(currentTimeMs)
        wifiRadio.initStart(currentTimeMs)
        wifiRunning.initStart(currentTimeMs)
        phoneScanning.initStart(currentTimeMs)
        bleScanning.initStart(currentTimeMs)
        screenOn.initStart(currentTimeMs)
        screenDoze.initStart(currentTimeMs)
        usbDataLink.initStart(currentTimeMs)
        cellularHighTxPower.initStart(currentTimeMs)
        gpsSignalQuality.initStart(currentTimeMs)
        nrState.initStart(currentTimeMs)
        plugged.initStart(currentTimeMs)
        phoneInCall.initStart(currentTimeMs)
        wakeLockHeld.initStart(currentTimeMs)
        wakeLockHolder.initStart(currentTimeMs)
        wakeupReason.initStart(currentTimeMs)
        bluetoothOn.initStart(currentTimeMs)
        videoOn.initStart(currentTimeMs)
        audioOn.initStart(currentTimeMs)
        cameraOn.initStart(currentTimeMs)
        lowPowerModeOn.initStart(currentTimeMs)
        idleMode.initStart(currentTimeMs)
        flashlightOn.initStart(currentTimeMs)
        chargingOn.initStart(currentTimeMs)
        wifiSuppl.initStart(currentTimeMs)
        wifiSignalStrength.initStart(currentTimeMs)
        dcpuStats.initStart(currentTimeMs)
        dpstStats.initStart(currentTimeMs)

        for (suid in activeProcessMap.values) suid.initStart(currentTimeMs)
        for (suid in appSyncingMap.values) suid.initStart(currentTimeMs)
        for (suid in foregroundProcessMap.values) suid.initStart(currentTimeMs)
        for (suid in topApplicationMap.values) suid.initStart(currentTimeMs)
        for (suid in connectivityMap.values) suid.initStart(currentTimeMs)
        for (suid in longWakelockMap.values) suid.initStart(currentTimeMs)
        for (suid in wakeLockMap.values) suid.initStart(currentTimeMs)
        for (suid in scheduledJobMap.values) suid.initStart(currentTimeMs)
        for (suid in tmpWhiteListMap.values) suid.initStart(currentTimeMs)
        for (suid in alarmMap.values) suid.initStart(currentTimeMs)
    }

    /**
     * Returns the current top app, or `null` when there is none.
     *
     * The second component is an error message for the impossible
     * multi-top-app case, mirroring Go's `(app, error)` return.
     */
    fun topApp(): Pair<ServiceUID?, String?> = when (topApplicationMap.size) {
        0 -> null to null
        1 -> topApplicationMap.values.first() to null
        else -> null to
            "too many apps simultaneously on top: (|TopApplicationMap| = ${topApplicationMap.size})"
    }
}
