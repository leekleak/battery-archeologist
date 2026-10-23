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

// Kotlin port of the section parsers in
// third_party/battery-historian/checkinparse/checkin_parse.go.
//
// Version gates follow the notes in AOSP frameworks/base/core/java/android/os/BatteryStats.java:
//   * report version 14 added the modern `m` layout and removed the legacy misc fields;
//   * report version 17 changed controller activity, `st`, and global bluetooth layouts;
//   * report version 18 added battery capacity to `bt`;
//   * report version 19 added current/max durations to `wl`;
//   * report version 21 added total durations to `wl`;
//   * current AOSP (report version 36 in this fixture) adds a fourth wakelock group
//     lettered `bp` and a seventh `st` value. There are no matching proto fields, so both
//     are reported as "new fields" warnings, like upstream.
//
// Fields that newer devices print but this 2017-era proto cannot represent are deliberately
// left unsupported and reported through warnings rather than dropped silently:
//   * pwi names cpu/camera/audio/video/sensors/gnss/memory/ambi;
//   * the wl `bp` group, the seventh st value, and extra bt/dc fields;
//   * data connection enum values 17+. The entries and values are retained, but the
//     generated enum has no constant for them, so the name is left unset;
//   * sections awl, ctf, fgs, gcf, jbc, rpm and wmct.

package com.leekleak.batteryarcheologist.core.checkin.checkinparse

import com.leekleak.batteryarcheologist.core.checkin.sliceparse.SliceParseException
import com.leekleak.batteryarcheologist.core.checkin.sliceparse.SliceReader
import com.leekleak.batteryarcheologist.core.historianutils.HistorianUtils
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App as BatteryApp
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Apk as BatteryAppApk
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Apk.Service as BatteryAppService
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Audio as BatteryAppAudio
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.BluetoothMisc as BatteryAppBluetoothMisc
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Camera as BatteryAppCamera
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Child as BatteryAppChild
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Cpu as BatteryAppCpu
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Flashlight as BatteryAppFlashlight
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Foreground as BatteryAppForeground
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Network as BatteryAppNetwork
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.PowerUseItem as BatteryAppPowerUseItem
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Process as BatteryAppProcess
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.ScheduledJob as BatteryAppScheduledJob
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Sensor as BatteryAppSensor
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.StateTime as BatteryAppStateTime
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Sync as BatteryAppSync
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.UserActivity as BatteryAppUserActivity
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Vibrator as BatteryAppVibrator
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Video as BatteryAppVideo
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.WakeupAlarm as BatteryAppWakeupAlarm
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Wakelock as BatteryAppWakelock
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Wifi as BatteryAppWifi
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.ControllerActivity as BatteryController
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.ControllerActivity.TxLevel as BatteryTxLevel
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.System as BatterySystem

/**
 * Highest report version with an explicit field gate in this port (Android 16 / API 36).
 * Versions above this still parse with the v36 layout and add an extra warning.
 */
internal const val MAX_SUPPORTED_REPORT_VERSION = 36

/** Section names used by `dumpsys batterystats --checkin`. */
internal object SectionNames {
    const val INFO = "i"
    const val SINCE_CHARGED = "l"
    const val CURRENT = "c"
    const val UNPLUGGED = "u"

    const val VERSION = "vers"
    const val UID = "uid"
    const val WAKEUP_ALARM = "wua"
    const val APK = "apk"
    const val PROCESS = "pr"
    const val CPU = "cpu"
    const val SENSOR = "sr"
    const val VIBRATOR = "vib"
    const val FOREGROUND = "fg"
    const val STATE_TIME = "st"
    const val WAKELOCK = "wl"
    const val SYNC = "sy"
    const val JOB = "jb"
    const val KERNEL_WAKELOCK = "kwl"
    const val WAKEUP_REASON = "wr"
    const val NETWORK = "nt"
    const val USER_ACTIVITY = "ua"
    const val BATTERY = "bt"
    const val BATTERY_DISCHARGE = "dc"
    const val BATTERY_LEVEL = "lv"
    const val GLOBAL_WIFI = "gwfl"
    const val GLOBAL_WIFI_CONTROLLER = "gwfcd"
    const val WIFI_CONTROLLER = "wfcd"
    const val WIFI = "wfl"
    const val BLUETOOTH_CONTROLLER = "ble"
    const val BLUETOOTH_MISC = "blem"
    const val GLOBAL_BLUETOOTH_CONTROLLER = "gble"
    const val MISC = "m"
    const val MODEM_CONTROLLER = "mcd"
    const val GLOBAL_MODEM_CONTROLLER = "gmcd"
    const val GLOBAL_NETWORK = "gn"
    const val SCREEN_BRIGHTNESS = "br"
    const val SIGNAL_STRENGTH_TIME = "sgt"
    const val SIGNAL_SCANNING_TIME = "sst"
    const val SIGNAL_STRENGTH_COUNT = "sgc"
    const val DATA_CONNECTION_TIME = "dct"
    const val DATA_CONNECTION_COUNT = "dcc"
    const val WIFI_STATE_TIME = "wst"
    const val WIFI_STATE_COUNT = "wsc"
    const val WIFI_SUPPLICANT_STATE_TIME = "wsst"
    const val WIFI_SUPPLICANT_STATE_COUNT = "wssc"
    const val WIFI_SIGNAL_STRENGTH_TIME = "wsgt"
    const val WIFI_SIGNAL_STRENGTH_COUNT = "wsgc"
    const val BLUETOOTH_STATE_TIME = "bst"
    const val BLUETOOTH_STATE_COUNT = "bsc"
    const val POWER_USE_SUMMARY = "pws"
    const val POWER_USE_ITEM = "pwi"
    const val DISCHARGE_STEP = "dsd"
    const val CHARGE_STEP = "csd"
    const val DISCHARGE_TIME_REMAINING = "dtr"
    const val CHARGE_TIME_REMAINING = "ctr"
    const val FLASHLIGHT = "fla"
    const val CAMERA = "cam"
    const val VIDEO = "vid"
    const val AUDIO = "aud"
}

/** Maps the short names printed in `pwi` rows to proto enum values. */
private val powerUseItemNameMap: Map<String, BatterySystem.PowerUseItem.Name> = mapOf(
    "idle" to BatterySystem.PowerUseItem.Name.IDLE,
    "cell" to BatterySystem.PowerUseItem.Name.CELL,
    "phone" to BatterySystem.PowerUseItem.Name.PHONE,
    "wifi" to BatterySystem.PowerUseItem.Name.WIFI,
    "blue" to BatterySystem.PowerUseItem.Name.BLUETOOTH,
    "scrn" to BatterySystem.PowerUseItem.Name.SCREEN,
    "uid" to BatterySystem.PowerUseItem.Name.APP,
    "user" to BatterySystem.PowerUseItem.Name.USER,
    "unacc" to BatterySystem.PowerUseItem.Name.UNACCOUNTED,
    "over" to BatterySystem.PowerUseItem.Name.OVERCOUNTED,
    "???" to BatterySystem.PowerUseItem.Name.DEFAULT,
    "flashlight" to BatterySystem.PowerUseItem.Name.FLASHLIGHT,
)

/** Data collected while parsing a `csd` or `dsd` row. */
private data class StepData(
    val timeMsec: Float,
    val level: Float,
    val displayState: BatterySystem.DisplayState.State,
    val powerSaveMode: BatterySystem.PowerSaveMode.Mode,
    val idleMode: BatterySystem.IdleMode.Mode,
)

/**
 * Dispatches one checkin row to its section parser.
 *
 * @return whether the section was recognized. Unrecognized sections are warnings in the
 *   caller, never fatal errors.
 */
internal fun parseSection(
    state: ParserState,
    reportVersion: Int,
    rawUid: Int,
    section: String,
    record: List<String>,
    app: BatteryApp.Builder,
): SectionParseResult {
    val result: SectionParseResult = when (section) {
        SectionNames.APK -> result(parseChildApk(state, record, app, rawUid))
        SectionNames.AUDIO -> result(parseAppSingleton(section, record, app, AppSingleton.AUDIO))
        SectionNames.BATTERY -> result(systemBattery(record, state.system))
        SectionNames.BATTERY_DISCHARGE -> result(systemBatteryDischarge(record, state.system))
        SectionNames.BATTERY_LEVEL -> result(systemBatteryLevel(record, state.system))
        SectionNames.BLUETOOTH_CONTROLLER -> result(
            parseAppController(section, record, app, AppController.BLUETOOTH),
        )
        SectionNames.BLUETOOTH_MISC -> result(
            parseAppSingleton(section, record, app, AppSingleton.BLUETOOTH_MISC),
        )
        SectionNames.CAMERA -> result(parseAppSingleton(section, record, app, AppSingleton.CAMERA))
        SectionNames.CHARGE_STEP -> {
            val (data, warning) = parseStepData(record)
            val step = BatterySystem.ChargeStep.Builder()
            step.time_msec = data.timeMsec
            step.level = data.level
            step.display_state = data.displayState
            step.power_save_mode = data.powerSaveMode
            step.idle_mode = data.idleMode
            state.system.charge_step = state.system.charge_step + step.build()
            result(warning)
        }
        SectionNames.CHARGE_TIME_REMAINING -> {
            if (state.system.charge_time_remaining != null) {
                state.counter.count("error-charge-time-remaining-exist", 1)
                throw CheckinParseException("charge time remaining field already exists")
            }
            val b = BatterySystem.ChargeTimeRemaining.Builder()
            val warning = parseLine(
                section,
                record,
                b,
                BATTERY_SYSTEM_CHARGE_TIME_REMAINING_SPEC,
            )
            state.system.charge_time_remaining = b.build()
            result(warning)
        }
        SectionNames.CPU -> result(parseAppSingleton(section, record, app, AppSingleton.CPU))
        SectionNames.DISCHARGE_STEP -> {
            val (data, warning) = parseStepData(record)
            val step = BatterySystem.DischargeStep.Builder()
            step.time_msec = data.timeMsec
            step.level = data.level
            step.display_state = data.displayState
            step.power_save_mode = data.powerSaveMode
            step.idle_mode = data.idleMode
            state.system.discharge_step = state.system.discharge_step + step.build()
            result(warning)
        }
        SectionNames.DISCHARGE_TIME_REMAINING -> {
            if (state.system.discharge_time_remaining != null) {
                state.counter.count("error-discharge-time-remaining-exist", 1)
                throw CheckinParseException("discharge time remaining field already exists")
            }
            val b = BatterySystem.DischargeTimeRemaining.Builder()
            val warning = parseLine(
                section,
                record,
                b,
                BATTERY_SYSTEM_DISCHARGE_TIME_REMAINING_SPEC,
            )
            state.system.discharge_time_remaining = b.build()
            result(warning)
        }
        SectionNames.FLASHLIGHT -> result(
            parseAppSingleton(section, record, app, AppSingleton.FLASHLIGHT),
        )
        SectionNames.FOREGROUND -> result(
            parseAppSingleton(section, record, app, AppSingleton.FOREGROUND),
        )
        SectionNames.GLOBAL_BLUETOOTH_CONTROLLER -> {
            if (reportVersion < 17) {
                val b = BatterySystem.GlobalBluetooth.Builder()
                val warning = parseLine(
                    section,
                    record,
                    b,
                    BATTERY_SYSTEM_GLOBAL_BLUETOOTH_SPEC,
                )
                state.system.global_bluetooth = b.build()
                result(warning)
            } else {
                state.system.global_bluetooth_controller =
                    parseControllerData(section, record).build()
                result(null)
            }
        }
        SectionNames.GLOBAL_MODEM_CONTROLLER -> {
            state.system.global_modem_controller = parseControllerData(section, record).build()
            result(null)
        }
        SectionNames.GLOBAL_NETWORK -> result(globalNetwork(record, state.system, state.counter))
        SectionNames.GLOBAL_WIFI_CONTROLLER -> {
            state.system.global_wifi_controller = parseControllerData(section, record).build()
            result(null)
        }
        SectionNames.GLOBAL_WIFI -> result(globalWifi(record, state.system))
        SectionNames.JOB -> result(parseAppScheduledJob(record, app))
        SectionNames.KERNEL_WAKELOCK -> result(systemKernelWakelock(record, state.system))
        SectionNames.MISC -> result(systemMisc(reportVersion, record, state.system, state.counter))
        SectionNames.MODEM_CONTROLLER -> result(
            parseAppController(section, record, app, AppController.MODEM),
        )
        SectionNames.NETWORK -> result(parseAppNetwork(record, app))
        SectionNames.POWER_USE_ITEM -> result(parseAppSystemPowerUseItem(state, record, app))
        SectionNames.POWER_USE_SUMMARY -> result(
            systemPowerUseSummary(record, state.system, state.counter),
        )
        SectionNames.PROCESS -> result(parseAppProcess(record, app))
        SectionNames.SCREEN_BRIGHTNESS -> result(parseSystemScreenBrightness(record, state.system))
        SectionNames.SENSOR -> result(parseAppSensor(record, app))
        SectionNames.SIGNAL_SCANNING_TIME -> result(
            systemSignalScanningTime(record, state.system, state.counter),
        )
        SectionNames.STATE_TIME -> result(parseAppStateTime(reportVersion, record, app))
        SectionNames.SYNC -> result(parseAppSync(record, app))
        SectionNames.USER_ACTIVITY -> {
            if (rawUid % 100_000 != 0) {
                result(parseAppUserActivity(record, app))
            } else {
                result(null)
            }
        }
        SectionNames.VIBRATOR -> result(
            parseAppSingleton(section, record, app, AppSingleton.VIBRATOR),
        )
        SectionNames.VIDEO -> result(parseAppSingleton(section, record, app, AppSingleton.VIDEO))
        SectionNames.WAKELOCK -> result(parseAppWakelock(reportVersion, record, app))
        SectionNames.WAKEUP_ALARM -> result(parseAppWakeupAlarm(record, app))
        SectionNames.WAKEUP_REASON -> result(systemWakeupReason(record, state.system))
        SectionNames.WIFI_CONTROLLER -> result(
            parseAppController(section, record, app, AppController.WIFI),
        )
        SectionNames.WIFI -> result(parseAppWifi(record, app))
        else -> {
            if (isTimeCountSection(section)) {
                result(parseSystemTimeCountPair(section, record, state.system, state.counter))
            } else {
                SectionParseResult(parsed = false)
            }
        }
    }
    return result
}

private fun result(warning: String?): SectionParseResult = SectionParseResult(true, warning)

private fun isTimeCountSection(section: String): Boolean = timeCountKind(section) != null

// ---------------------------------------------------------------------------------------
// Generic app sections
// ---------------------------------------------------------------------------------------

private enum class AppSingleton {
    AUDIO,
    BLUETOOTH_MISC,
    CAMERA,
    CPU,
    FLASHLIGHT,
    FOREGROUND,
    VIBRATOR,
    VIDEO,
}

private fun parseAppSingleton(
    section: String,
    record: List<String>,
    app: BatteryApp.Builder,
    kind: AppSingleton,
): String? = when (kind) {
    AppSingleton.AUDIO -> {
        val target = app.audio?.newBuilder() ?: BatteryAppAudio.Builder()
        val warning = parseAndAccumulate(section, record, target, BATTERY_APP_AUDIO_SPEC)
        app.audio = target.build()
        warning
    }
    AppSingleton.BLUETOOTH_MISC -> {
        val target = app.bluetooth_misc?.newBuilder() ?: BatteryAppBluetoothMisc.Builder()
        val warning = parseAndAccumulate(section, record, target, BATTERY_APP_BLUETOOTH_MISC_SPEC)
        app.bluetooth_misc = target.build()
        warning
    }
    AppSingleton.CAMERA -> {
        val target = app.camera?.newBuilder() ?: BatteryAppCamera.Builder()
        val warning = parseAndAccumulate(section, record, target, BATTERY_APP_CAMERA_SPEC)
        app.camera = target.build()
        warning
    }
    AppSingleton.CPU -> {
        val target = app.cpu?.newBuilder() ?: BatteryAppCpu.Builder()
        val warning = parseAndAccumulate(section, record, target, BATTERY_APP_CPU_SPEC)
        app.cpu = target.build()
        warning
    }
    AppSingleton.FLASHLIGHT -> {
        val target = app.flashlight?.newBuilder() ?: BatteryAppFlashlight.Builder()
        val warning = parseAndAccumulate(section, record, target, BATTERY_APP_FLASHLIGHT_SPEC)
        app.flashlight = target.build()
        warning
    }
    AppSingleton.FOREGROUND -> {
        val target = app.foreground?.newBuilder() ?: BatteryAppForeground.Builder()
        val warning = parseAndAccumulate(section, record, target, BATTERY_APP_FOREGROUND_SPEC)
        app.foreground = target.build()
        warning
    }
    AppSingleton.VIBRATOR -> {
        val target = app.vibrator?.newBuilder() ?: BatteryAppVibrator.Builder()
        val warning = parseAndAccumulate(section, record, target, BATTERY_APP_VIBRATOR_SPEC)
        app.vibrator = target.build()
        warning
    }
    AppSingleton.VIDEO -> {
        val target = app.video?.newBuilder() ?: BatteryAppVideo.Builder()
        val warning = parseAndAccumulate(section, record, target, BATTERY_APP_VIDEO_SPEC)
        app.video = target.build()
        warning
    }
}

private enum class AppController {
    BLUETOOTH,
    MODEM,
    WIFI,
}

private fun parseAppController(
    section: String,
    record: List<String>,
    app: BatteryApp.Builder,
    kind: AppController,
): String? {
    val existing = when (kind) {
        AppController.BLUETOOTH -> app.bluetooth_controller
        AppController.MODEM -> app.modem_controller
        AppController.WIFI -> app.wifi_controller
    }
    val target = existing?.newBuilder() ?: BatteryController.Builder()
    parseAndAccumulateControllerData(section, record, target)
    when (kind) {
        AppController.BLUETOOTH -> app.bluetooth_controller = target.build()
        AppController.MODEM -> app.modem_controller = target.build()
        AppController.WIFI -> app.wifi_controller = target.build()
    }
    return null
}

// ---------------------------------------------------------------------------------------
// App sections
// ---------------------------------------------------------------------------------------

/**
 * Parses `apk`: wakeup alarms, apk name, service name, started time, start count, launch count.
 */
private fun parseChildApk(
    state: ParserState,
    record: List<String>,
    app: BatteryApp.Builder,
    rawUid: Int,
): String? {
    val reader = SliceReader(record)
    val wakeups = reader.readFloat32()
    val name = reader.readString()

    var childIndex = -1
    for (i in app.child.indices) {
        if (app.child[i].name == name) {
            childIndex = i
            break
        }
    }
    val child = if (childIndex >= 0) {
        app.child[childIndex].newBuilder()
    } else {
        BatteryAppChild.Builder().also { it.name = name }
    }

    val apk = child.apk?.newBuilder() ?: BatteryAppApk.Builder()
    if (state.apkSeen.add(ApkId(rawUid, name))) {
        apk.wakeups = (apk.wakeups ?: 0f) + wakeups
    }

    val service = BatteryAppService.Builder()
    val warning = parseLine(SectionNames.APK, reader.remaining, service, BATTERY_APP_SERVICE_SPEC)

    var merged = false
    for (i in apk.service.indices) {
        if (apk.service[i].name == service.name) {
            val target = apk.service[i].newBuilder()
            accumulate(target, service, BATTERY_APP_SERVICE_SPEC, skipFields = setOf("name"))
            apk.service = apk.service.toMutableList().also { it[i] = target.build() }
            merged = true
            break
        }
    }
    if (!merged) {
        apk.service = apk.service + service.build()
    }

    child.apk = apk.build()
    if (childIndex >= 0) {
        app.child = app.child.toMutableList().also { it[childIndex] = child.build() }
    } else {
        app.child = app.child + child.build()
    }
    return warning
}

/**
 * Parses `pr`: process name, user time, system time, foreground time, starts, ANRs, crashes.
 * Rows for the same process name are accumulated.
 */
private fun parseAppProcess(record: List<String>, app: BatteryApp.Builder): String? {
    val parsed = BatteryAppProcess.Builder()
    val warning = parseLine(SectionNames.PROCESS, record, parsed, BATTERY_APP_PROCESS_SPEC)
    for (i in app.process.indices) {
        if (app.process[i].name == parsed.name) {
            val target = app.process[i].newBuilder()
            accumulate(target, parsed, BATTERY_APP_PROCESS_SPEC, skipFields = setOf("name"))
            app.process = app.process.toMutableList().also { it[i] = target.build() }
            return warning
        }
    }
    app.process = app.process + parsed.build()
    return warning
}

/**
 * Parses `sr`: sensor number, apportioned time, count, background count, actual time,
 * background actual time. Rows for the same sensor are accumulated.
 */
private fun parseAppSensor(record: List<String>, app: BatteryApp.Builder): String? {
    val parsed = BatteryAppSensor.Builder()
    val warning = parseLine(SectionNames.SENSOR, record, parsed, BATTERY_APP_SENSOR_SPEC)
    for (i in app.sensor.indices) {
        if (app.sensor[i].number == parsed.number) {
            val target = app.sensor[i].newBuilder()
            accumulate(target, parsed, BATTERY_APP_SENSOR_SPEC, skipFields = setOf("number"))
            app.sensor = app.sensor.toMutableList().also { it[i] = target.build() }
            return warning
        }
    }
    app.sensor = app.sensor + parsed.build()
    return warning
}

/**
 * Parses `jb`: job name, total time, count, background time, background count.
 */
private fun parseAppScheduledJob(record: List<String>, app: BatteryApp.Builder): String? {
    val parsed = BatteryAppScheduledJob.Builder()
    val warning = parseLine(SectionNames.JOB, record, parsed, BATTERY_APP_SCHEDULED_JOB_SPEC)
    parsed.name = HistorianUtils.scrubPII(parsed.name.orEmpty())
    for (i in app.scheduled_job.indices) {
        if (app.scheduled_job[i].name == parsed.name) {
            val target = app.scheduled_job[i].newBuilder()
            accumulate(target, parsed, BATTERY_APP_SCHEDULED_JOB_SPEC, skipFields = setOf("name"))
            app.scheduled_job = app.scheduled_job.toMutableList().also { it[i] = target.build() }
            return warning
        }
    }
    app.scheduled_job = app.scheduled_job + parsed.build()
    return warning
}

/**
 * Parses `sy`: sync name, total time, count, background time, background count.
 */
private fun parseAppSync(record: List<String>, app: BatteryApp.Builder): String? {
    val parsed = BatteryAppSync.Builder()
    val warning = parseLine(SectionNames.SYNC, record, parsed, BATTERY_APP_SYNC_SPEC)
    parsed.name = HistorianUtils.scrubPII(parsed.name.orEmpty())
    for (i in app.sync.indices) {
        if (app.sync[i].name == parsed.name) {
            val target = app.sync[i].newBuilder()
            accumulate(target, parsed, BATTERY_APP_SYNC_SPEC, skipFields = setOf("name"))
            app.sync = app.sync.toMutableList().also { it[i] = target.build() }
            return warning
        }
    }
    app.sync = app.sync + parsed.build()
    return warning
}

/**
 * Parses `wua`: wakeup alarm name and count. Rows with the same name are accumulated.
 */
private fun parseAppWakeupAlarm(record: List<String>, app: BatteryApp.Builder): String? {
    val parsed = BatteryAppWakeupAlarm.Builder()
    val warning = parseLine(SectionNames.WAKEUP_ALARM, record, parsed, BATTERY_APP_WAKEUP_ALARM_SPEC)
    parsed.name = HistorianUtils.scrubPII(parsed.name.orEmpty())
    for (i in app.wakeup_alarm.indices) {
        if (app.wakeup_alarm[i].name == parsed.name) {
            val target = app.wakeup_alarm[i].newBuilder()
            accumulate(target, parsed, BATTERY_APP_WAKEUP_ALARM_SPEC, skipFields = setOf("name"))
            app.wakeup_alarm = app.wakeup_alarm.toMutableList().also { it[i] = target.build() }
            return warning
        }
    }
    app.wakeup_alarm = app.wakeup_alarm + parsed.build()
    return warning
}

/**
 * Parses `ua`: activity counts in order OTHER, BUTTON, TOUCH, ACCESSIBILITY.
 *
 * Newer builds print additional activity types. The count is retained; the name is left
 * unset when the generated enum has no constant for that index.
 */
private fun parseAppUserActivity(record: List<String>, app: BatteryApp.Builder): String? {
    val warning = if (record.size > 4) {
        "user activity now has ${record.size - 4} additional fields"
    } else {
        null
    }
    for ((i, rawCount) in record.withIndex()) {
        val count = parseFloat32Value(rawCount, SectionNames.USER_ACTIVITY)
        if (i < app.user_activity.size) {
            val b = app.user_activity[i].newBuilder()
            b.count = (b.count ?: 0f) + count
            app.user_activity = app.user_activity.toMutableList().also { it[i] = b.build() }
            continue
        }
        val b = BatteryAppUserActivity.Builder()
        // Unknown activity types from newer builds cannot be represented by the generated
        // enum; keep the count and leave the name unset.
        BatteryAppUserActivity.Name.fromValue(i)?.let { b.name = it }
        b.count = count
        app.user_activity = app.user_activity + b.build()
    }
    return warning
}

/**
 * Parses `st`.
 *
 * Report version <= 16: foreground time, active time, cached time.
 * Report version >= 17: top, foreground service, top sleeping, foreground, background,
 * cached. AOSP later appended a seventh value; it has no proto field here and is counted
 * in the warning.
 */
private fun parseAppStateTime(
    reportVersion: Int,
    record: List<String>,
    app: BatteryApp.Builder,
): String? {
    if (reportVersion < 17) {
        val target = app.state_time?.newBuilder() ?: BatteryAppStateTime.Builder()
        val warning = parseAndAccumulate(
            SectionNames.STATE_TIME,
            record,
            target,
            BATTERY_APP_STATE_TIME_SPEC,
        )
        app.state_time = target.build()
        return warning
    }

    val reader = SliceReader(record)
    val top = reader.readInt64()
    val foregroundService = reader.readInt64()
    val topSleeping = reader.readInt64()
    val foreground = reader.readFloat32()
    val background = reader.readInt64()
    val cached = reader.readFloat32()
    val warning = if (reader.hasRemaining) {
        "${SectionNames.STATE_TIME} has ${reader.remaining.size} new fields"
    } else {
        null
    }

    val stateTime = app.state_time?.newBuilder() ?: BatteryAppStateTime.Builder()
    stateTime.top_time_msec = (stateTime.top_time_msec ?: 0L) + top
    stateTime.foreground_service_time_msec =
        (stateTime.foreground_service_time_msec ?: 0L) + foregroundService
    stateTime.top_sleeping_time_msec = (stateTime.top_sleeping_time_msec ?: 0L) + topSleeping
    stateTime.foreground_time_msec = (stateTime.foreground_time_msec ?: 0f) + foreground
    stateTime.background_time_msec = (stateTime.background_time_msec ?: 0L) + background
    stateTime.cached_time_msec = (stateTime.cached_time_msec ?: 0f) + cached
    app.state_time = stateTime.build()
    return warning
}

/**
 * Parses `wl`.
 *
 * The display format has changed several times:
 *   v<=18: name, (time, "f", count) x3;
 *   v19-20: name, (time, "f", count, current, max) x3;
 *   v21-35: name, (time, "f", count, current, max, total) x3;
 *   v36+: name, (time, letter, count, current, max, total) x4, where the fourth group is
 *          "bp" and has no field in this proto.
 */
private fun parseAppWakelock(
    reportVersion: Int,
    record: List<String>,
    app: BatteryApp.Builder,
): String? {
    val reader = SliceReader(record)
    val name = HistorianUtils.scrubPII(reader.readString())

    var fullTime = 0f
    var fullCount = 0f
    var fullCurrent = 0L
    var fullMax = 0L
    var fullTotal = 0L
    var partialTime = 0f
    var partialCount = 0f
    var partialCurrent = 0L
    var partialMax = 0L
    var partialTotal = 0L
    var windowTime = 0f
    var windowCount = 0f
    var windowCurrent = 0L
    var windowMax = 0L
    var windowTotal = 0L

    val warning: String?
    when {
        reportVersion <= 18 -> {
            fullTime = reader.readFloat32()
            reader.skip() // "f"
            fullCount = reader.readFloat32()
            partialTime = reader.readFloat32()
            reader.skip() // "p"
            partialCount = reader.readFloat32()
            windowTime = reader.readFloat32()
            reader.skip() // "w"
            windowCount = reader.readFloat32()
            warning = remainingWarning(reader)
        }

        reportVersion <= 20 -> {
            fullTime = reader.readFloat32()
            reader.skip()
            fullCount = reader.readFloat32()
            fullCurrent = reader.readInt64()
            fullMax = reader.readInt64()
            partialTime = reader.readFloat32()
            reader.skip()
            partialCount = reader.readFloat32()
            partialCurrent = reader.readInt64()
            partialMax = reader.readInt64()
            windowTime = reader.readFloat32()
            reader.skip()
            windowCount = reader.readFloat32()
            windowCurrent = reader.readInt64()
            windowMax = reader.readInt64()
            warning = remainingWarning(reader)
        }

        reportVersion < MAX_SUPPORTED_REPORT_VERSION -> {
            fullTime = reader.readFloat32()
            reader.skip()
            fullCount = reader.readFloat32()
            fullCurrent = reader.readInt64()
            fullMax = reader.readInt64()
            fullTotal = reader.readInt64()
            partialTime = reader.readFloat32()
            reader.skip()
            partialCount = reader.readFloat32()
            partialCurrent = reader.readInt64()
            partialMax = reader.readInt64()
            partialTotal = reader.readInt64()
            windowTime = reader.readFloat32()
            reader.skip()
            windowCount = reader.readFloat32()
            windowCurrent = reader.readInt64()
            windowMax = reader.readInt64()
            windowTotal = reader.readInt64()
            warning = remainingWarning(reader)
        }

        else -> {
            // Report version 36+: map groups by the printed type letter instead of position,
            // because AOSP inserted the "bp" group between p and w.
            var unsupportedFields = 0
            while (reader.hasRemaining) {
                if (reader.remaining.size < 6) {
                    unsupportedFields += reader.remaining.size
                    reader.skip(reader.remaining.size)
                    break
                }
                val time = reader.readFloat32()
                val letter = reader.readString()
                val count = reader.readFloat32()
                val current = reader.readInt64()
                val max = reader.readInt64()
                val total = reader.readInt64()
                when (letter) {
                    "f" -> {
                        fullTime = time
                        fullCount = count
                        fullCurrent = current
                        fullMax = max
                        fullTotal = total
                    }
                    "p" -> {
                        partialTime = time
                        partialCount = count
                        partialCurrent = current
                        partialMax = max
                        partialTotal = total
                    }
                    "w" -> {
                        windowTime = time
                        windowCount = count
                        windowCurrent = current
                        windowMax = max
                        windowTotal = total
                    }
                    else -> unsupportedFields += 6 // "bp" has no proto field.
                }
            }
            warning = if (unsupportedFields > 0) {
                "${SectionNames.WAKELOCK} has $unsupportedFields new fields"
            } else {
                null
            }
        }
    }

    for (i in app.wakelock.indices) {
        val existing = app.wakelock[i]
        if (existing.name == name) {
            val b = existing.newBuilder()
            b.full_time_msec = (b.full_time_msec ?: 0f) + fullTime
            b.full_count = (b.full_count ?: 0f) + fullCount
            b.full_current_duration_msec =
                maxOf(b.full_current_duration_msec ?: 0L, fullCurrent)
            b.full_max_duration_msec = maxOf(b.full_max_duration_msec ?: 0L, fullMax)
            if (fullTotal != -1L) {
                b.full_total_duration_msec = (b.full_total_duration_msec ?: 0L) + fullTotal
            }
            b.partial_time_msec = (b.partial_time_msec ?: 0f) + partialTime
            b.partial_count = (b.partial_count ?: 0f) + partialCount
            b.partial_current_duration_msec =
                maxOf(b.partial_current_duration_msec ?: 0L, partialCurrent)
            b.partial_max_duration_msec =
                maxOf(b.partial_max_duration_msec ?: 0L, partialMax)
            if (partialTotal != -1L) {
                b.partial_total_duration_msec = (b.partial_total_duration_msec ?: 0L) + partialTotal
            }
            b.window_time_msec = (b.window_time_msec ?: 0f) + windowTime
            b.window_count = (b.window_count ?: 0f) + windowCount
            b.window_current_duration_msec =
                maxOf(b.window_current_duration_msec ?: 0L, windowCurrent)
            b.window_max_duration_msec = maxOf(b.window_max_duration_msec ?: 0L, windowMax)
            if (windowTotal != -1L) {
                b.window_total_duration_msec = (b.window_total_duration_msec ?: 0L) + windowTotal
            }
            app.wakelock = app.wakelock.toMutableList().also { it[i] = b.build() }
            return warning
        }
    }

    val b = BatteryAppWakelock.Builder()
    b.name = name
    b.full_time_msec = fullTime
    b.full_count = fullCount
    b.full_current_duration_msec = fullCurrent
    b.full_max_duration_msec = fullMax
    b.full_total_duration_msec = fullTotal
    b.partial_time_msec = partialTime
    b.partial_count = partialCount
    b.partial_current_duration_msec = partialCurrent
    b.partial_max_duration_msec = partialMax
    b.partial_total_duration_msec = partialTotal
    b.window_time_msec = windowTime
    b.window_count = windowCount
    b.window_current_duration_msec = windowCurrent
    b.window_max_duration_msec = windowMax
    b.window_total_duration_msec = windowTotal
    app.wakelock = app.wakelock + b.build()
    return warning
}

private fun remainingWarning(reader: SliceReader): String? {
    val remaining = reader.remaining.size
    return if (remaining > 0) "${SectionNames.WAKELOCK} has $remaining new fields" else null
}

/**
 * Parses `nt`. `mobile_active_time` is printed in microseconds and stored in milliseconds.
 */
private fun parseAppNetwork(record: List<String>, app: BatteryApp.Builder): String? {
    val parsed = BatteryAppNetwork.Builder()
    val warning = parseLine(SectionNames.NETWORK, record, parsed, BATTERY_APP_NETWORK_SPEC)
    parsed.mobile_active_time_msec = (parsed.mobile_active_time_msec ?: 0f) / 1e3f
    val existing = app.network
    if (existing == null) {
        app.network = parsed.build()
    } else {
        val target = existing.newBuilder()
        accumulate(target, parsed, BATTERY_APP_NETWORK_SPEC)
        app.network = target.build()
    }
    return warning
}

/**
 * Parses `wfl`. The first three values are microseconds and are stored as milliseconds.
 */
private fun parseAppWifi(record: List<String>, app: BatteryApp.Builder): String? {
    if (record.size < 3) {
        throw CheckinParseException("${SectionNames.WIFI} line didn't contain enough fields")
    }
    val parsed = BatteryAppWifi.Builder()
    val warning = parseLine(SectionNames.WIFI, record, parsed, BATTERY_APP_WIFI_SPEC)
    parsed.full_wifi_lock_time_msec = (parsed.full_wifi_lock_time_msec ?: 0f) / 1e3f
    parsed.scan_time_msec = (parsed.scan_time_msec ?: 0f) / 1e3f
    parsed.running_time_msec = (parsed.running_time_msec ?: 0f) / 1e3f
    val existing = app.wifi
    if (existing == null) {
        app.wifi = parsed.build()
    } else {
        val target = existing.newBuilder()
        accumulate(target, parsed, BATTERY_APP_WIFI_SPEC)
        app.wifi = target.build()
    }
    return warning
}

/**
 * Parses `pwi`: power drain type and power in mAh.
 *
 * `uid` rows are per-app power and are accumulated in the app proto. Named system items
 * are stored in the system proto. Unknown names are warnings, not errors.
 */
private fun parseAppSystemPowerUseItem(
    state: ParserState,
    record: List<String>,
    app: BatteryApp.Builder,
): String? {
    val reader = SliceReader(record)
    val rawName = reader.readString()
    val computedPowerMah = reader.readFloat32()
    if (rawName == "uid") {
        state.allAppComputedPowerMah += computedPowerMah
        val target = app.power_use_item?.newBuilder() ?: BatteryAppPowerUseItem.Builder()
        target.computed_power_mah = (target.computed_power_mah ?: 0f) + computedPowerMah
        app.power_use_item = target.build()
    } else {
        val name = powerUseItemNameMap[rawName]
        if (name == null) {
            state.counter.count("unknown-app-system-power-use-item-$rawName", 1)
            return "Unknown powerUseItem name $rawName"
        }
        val item = BatterySystem.PowerUseItem.Builder()
        item.name = name
        item.computed_power_mah = computedPowerMah
        state.system.power_use_item = state.system.power_use_item + item.build()
    }
    return null
}

// ---------------------------------------------------------------------------------------
// System sections
// ---------------------------------------------------------------------------------------

/**
 * Parses `bt`. `N/A` start counts are normalized to 0, matching the upstream pipeline.
 */
private fun systemBattery(record: List<String>, system: BatterySystem.Builder): String? {
    if (system.battery != null) {
        throw CheckinParseException("system battery field already exists")
    }
    val normalized = if (record.firstOrNull() == "N/A") listOf("0") + record.drop(1) else record
    val parsed = BatterySystem.Battery.Builder()
    val warning = parseLine(SectionNames.BATTERY, normalized, parsed, BATTERY_SYSTEM_BATTERY_SPEC)
    system.battery = parsed.build()
    return warning
}

/** Parses `dc`: lower/upper bound discharged points, screen-on/off, and optional mAh. */
private fun systemBatteryDischarge(record: List<String>, system: BatterySystem.Builder): String? {
    if (system.battery_discharge != null) {
        throw CheckinParseException("battery discharge field already exists")
    }
    val parsed = BatterySystem.BatteryDischarge.Builder()
    val warning = parseLine(
        SectionNames.BATTERY_DISCHARGE,
        record,
        parsed,
        BATTERY_SYSTEM_BATTERY_DISCHARGE_SPEC,
    )
    system.battery_discharge = parsed.build()
    return warning
}

/** Parses `lv`: start and current battery level. */
private fun systemBatteryLevel(record: List<String>, system: BatterySystem.Builder): String? {
    if (system.battery_level != null) {
        throw CheckinParseException("battery level field already exists")
    }
    val parsed = BatterySystem.BatteryLevel.Builder()
    val warning = parseLine(
        SectionNames.BATTERY_LEVEL,
        record,
        parsed,
        BATTERY_SYSTEM_BATTERY_LEVEL_SPEC,
    )
    system.battery_level = parsed.build()
    return warning
}

/**
 * Parses `m`. The proto includes a screen-off field that is not printed and a block of
 * legacy fields removed in report version 14, so dummy values are inserted before the
 * generic line parser is used.
 */
private fun systemMisc(
    reportVersion: Int,
    record: List<String>,
    system: BatterySystem.Builder,
    counter: com.leekleak.batteryarcheologist.core.checkin.checkinutil.Counter,
): String? {
    if (system.misc != null) {
        counter.count("error-parse-system-misc-exist", 1)
        throw CheckinParseException("misc field already exists")
    }
    val adjusted = ArrayList(record)
    if (adjusted.isEmpty()) throw CheckinParseException("m field didn't contain enough fields")
    // screen_off_time_msec is calculated later; insert a placeholder in its proto position.
    adjusted.add(1, "0")
    val parsed = BatterySystem.Misc.Builder()
    val warning = if (reportVersion >= 14) {
        // Legacy wifi/bluetooth/network fields were removed from the line in v14.
        repeat(7) { adjusted.add(3, "0") }
        parseLine(SectionNames.MISC, adjusted, parsed, BATTERY_SYSTEM_MISC_SPEC)
    } else {
        // Legacy input event count is printed in the middle of the older layout.
        val withoutLegacyInput = adjusted.filterIndexed { index, _ -> index != 12 }
        parseLine(SectionNames.MISC, withoutLegacyInput, parsed, BATTERY_SYSTEM_MISC_SPEC)
    }
    if (reportVersion < 9) {
        // Older devices printed these durations in microseconds.
        parsed.full_wakelock_time_msec = (parsed.full_wakelock_time_msec ?: 0f) / 1e3f
        parsed.partial_wakelock_time_msec = (parsed.partial_wakelock_time_msec ?: 0f) / 1e3f
        parsed.mobile_active_time_msec = (parsed.mobile_active_time_msec ?: 0f) / 1e3f
        parsed.mobile_active_adjusted_time_msec =
            (parsed.mobile_active_adjusted_time_msec ?: 0f) / 1e3f
    }
    system.misc = parsed.build()
    return warning
}

/** Parses `pws`: capacity, computed power, and drained-power bounds. */
private fun systemPowerUseSummary(
    record: List<String>,
    system: BatterySystem.Builder,
    counter: com.leekleak.batteryarcheologist.core.checkin.checkinutil.Counter,
): String? {
    if (system.power_use_summary != null) {
        counter.count("error-parse-system-power-use-summary-exist", 1)
        throw CheckinParseException("power use summary field already exists")
    }
    val parsed = BatterySystem.PowerUseSummary.Builder()
    val warning = parseLine(
        SectionNames.POWER_USE_SUMMARY,
        record,
        parsed,
        BATTERY_SYSTEM_POWER_USE_SUMMARY_SPEC,
    )
    system.power_use_summary = parsed.build()
    return warning
}

/** Parses `gn`: global mobile/wifi traffic and packet counters. */
private fun globalNetwork(
    record: List<String>,
    system: BatterySystem.Builder,
    counter: com.leekleak.batteryarcheologist.core.checkin.checkinutil.Counter,
): String? {
    if (system.global_network != null) {
        counter.count("error-parse-system-global-network-exist", 1)
        throw CheckinParseException("global network field already exists")
    }
    val parsed = BatterySystem.GlobalNetwork.Builder()
    val warning = parseLine(
        SectionNames.GLOBAL_NETWORK,
        record,
        parsed,
        BATTERY_SYSTEM_GLOBAL_NETWORK_SPEC,
    )
    system.global_network = parsed.build()
    return warning
}

/** Parses `gwfl`: global wifi on/running times and legacy controller fields. */
private fun globalWifi(record: List<String>, system: BatterySystem.Builder): String? {
    val parsed = BatterySystem.GlobalWifi.Builder()
    val warning = parseLine(
        SectionNames.GLOBAL_WIFI,
        record,
        parsed,
        BATTERY_SYSTEM_GLOBAL_WIFI_SPEC,
    )
    system.global_wifi = parsed.build()
    return warning
}

/** Parses `kwl`: kernel wakelock name, time, count, current and max duration. */
private fun systemKernelWakelock(record: List<String>, system: BatterySystem.Builder): String? {
    val parsed = BatterySystem.KernelWakelock.Builder()
    val warning = parseLine(
        SectionNames.KERNEL_WAKELOCK,
        record,
        parsed,
        BATTERY_SYSTEM_KERNEL_WAKELOCK_SPEC,
    )
    for (i in system.kernel_wakelock.indices) {
        val existing = system.kernel_wakelock[i]
        if (existing.name == parsed.name) {
            val b = existing.newBuilder()
            b.time_msec = (b.time_msec ?: 0f) + (parsed.time_msec ?: 0f)
            b.count = (b.count ?: 0f) + (parsed.count ?: 0f)
            b.current_duration_msec =
                maxOf(b.current_duration_msec ?: 0L, parsed.current_duration_msec ?: 0L)
            b.max_duration_msec =
                maxOf(b.max_duration_msec ?: 0L, parsed.max_duration_msec ?: 0L)
            system.kernel_wakelock =
                system.kernel_wakelock.toMutableList().also { it[i] = b.build() }
            return warning
        }
    }
    system.kernel_wakelock = system.kernel_wakelock + parsed.build()
    return warning
}

/** Parses `wr`: wakeup reason name, time and (v11+) count. */
private fun systemWakeupReason(record: List<String>, system: BatterySystem.Builder): String? {
    val parsed = BatterySystem.WakeupReason.Builder()
    val warning = parseLine(
        SectionNames.WAKEUP_REASON,
        record,
        parsed,
        BATTERY_SYSTEM_WAKEUP_REASON_SPEC,
    )
    system.wakeup_reason = system.wakeup_reason + parsed.build()
    return warning
}

/** Parses `sst`: time spent trying to acquire a signal. */
private fun systemSignalScanningTime(
    record: List<String>,
    system: BatterySystem.Builder,
    counter: com.leekleak.batteryarcheologist.core.checkin.checkinutil.Counter,
): String? {
    if (system.signal_scanning_time != null) {
        counter.count("error-parse-system-signal-scanning-time-exist", 1)
        throw CheckinParseException("signal scanning time field already exists")
    }
    val parsed = BatterySystem.SignalScanningTime.Builder()
    val warning = parseLine(
        SectionNames.SIGNAL_SCANNING_TIME,
        record,
        parsed,
        BATTERY_SYSTEM_SIGNAL_SCANNING_TIME_SPEC,
    )
    system.signal_scanning_time = parsed.build()
    return warning
}

/** Parses `br`: duration spent at each of the five screen brightness levels. */
private fun parseSystemScreenBrightness(
    record: List<String>,
    system: BatterySystem.Builder,
): String? {
    if (system.screen_brightness.isNotEmpty()) {
        throw CheckinParseException("screen brightness field already exists")
    }
    val expected = BatterySystem.ScreenBrightness.Name.entries.size
    if (record.size != expected) {
        throw CheckinParseException(
            "wrong number of screen brightness fields: got ${record.size}, want $expected",
        )
    }
    for ((index, rawValue) in record.withIndex()) {
        val b = BatterySystem.ScreenBrightness.Builder()
        BatterySystem.ScreenBrightness.Name.fromValue(index)?.let { b.name = it }
        b.time_msec = parseFloat32Value(rawValue, SectionNames.SCREEN_BRIGHTNESS)
        system.screen_brightness = system.screen_brightness + b.build()
    }
    return null
}

/** Categorizes the paired time/count sections. */
private enum class TimeCountKind(val expectedLen: Int) {
    SIGNAL_STRENGTH(5),
    DATA_CONNECTION(17),
    WIFI_STATE(8),
    BLUETOOTH_STATE(4),
    WIFI_SUPPLICANT_STATE(13),
    WIFI_SIGNAL_STRENGTH(5),
}

private fun timeCountKind(section: String): TimeCountKind? = when (section) {
    SectionNames.SIGNAL_STRENGTH_TIME,
    SectionNames.SIGNAL_STRENGTH_COUNT,
    -> TimeCountKind.SIGNAL_STRENGTH

    SectionNames.DATA_CONNECTION_TIME,
    SectionNames.DATA_CONNECTION_COUNT,
    -> TimeCountKind.DATA_CONNECTION

    SectionNames.WIFI_STATE_TIME,
    SectionNames.WIFI_STATE_COUNT,
    -> TimeCountKind.WIFI_STATE

    SectionNames.BLUETOOTH_STATE_TIME,
    SectionNames.BLUETOOTH_STATE_COUNT,
    -> TimeCountKind.BLUETOOTH_STATE

    SectionNames.WIFI_SUPPLICANT_STATE_TIME,
    SectionNames.WIFI_SUPPLICANT_STATE_COUNT,
    -> TimeCountKind.WIFI_SUPPLICANT_STATE

    SectionNames.WIFI_SIGNAL_STRENGTH_TIME,
    SectionNames.WIFI_SIGNAL_STRENGTH_COUNT,
    -> TimeCountKind.WIFI_SIGNAL_STRENGTH

    else -> null
}

/**
 * Parses the sections that are printed as a time array and a separate count array
 * (signal strength, data connection, wifi/bluetooth state, ...).
 *
 * Values beyond the enum's defined range are still appended; their enum name is left unset,
 * matching the upstream parser, which keeps every record value.
 */
private fun parseSystemTimeCountPair(
    section: String,
    record: List<String>,
    system: BatterySystem.Builder,
    counter: com.leekleak.batteryarcheologist.core.checkin.checkinutil.Counter,
): String? {
    val kind = timeCountKind(section)
        ?: throw CheckinParseException("parseSystemTimeCountPair encountered unknown section: $section")
    val fieldName = if (section.endsWith("t")) "time_msec" else "count"
    var warning: String? = null
    if (record.size < kind.expectedLen) {
        counter.count("error-system-$section-too-few-records", 1)
        throw CheckinParseException("$section doesn't contain enough records")
    } else if (record.size > kind.expectedLen) {
        counter.count("error-system-$section-too-many-records", 1)
        warning = "$section has ${record.size - kind.expectedLen} additional field(s) that are not captured."
    }

    val currentLen = kind.entryCount(system)
    if (currentLen == 0) {
        for ((index, rawValue) in record.withIndex()) {
            val value = parseFloat32Value(rawValue, section)
            kind.addEntry(system, index, fieldName, value)
        }
    } else if (currentLen == record.size) {
        for ((index, rawValue) in record.withIndex()) {
            val value = parseFloat32Value(rawValue, section)
            kind.setEntry(system, index, fieldName, value, section, counter)
        }
    } else {
        throw CheckinParseException("inconsistent number of fields in $section")
    }
    return warning
}

private fun TimeCountKind.entryCount(system: BatterySystem.Builder): Int = when (this) {
    TimeCountKind.SIGNAL_STRENGTH -> system.signal_strength.size
    TimeCountKind.DATA_CONNECTION -> system.data_connection.size
    TimeCountKind.WIFI_STATE -> system.wifi_state.size
    TimeCountKind.BLUETOOTH_STATE -> system.bluetooth_state.size
    TimeCountKind.WIFI_SUPPLICANT_STATE -> system.wifi_supplicant_state.size
    TimeCountKind.WIFI_SIGNAL_STRENGTH -> system.wifi_signal_strength.size
}

private fun TimeCountKind.addEntry(
    system: BatterySystem.Builder,
    index: Int,
    fieldName: String,
    value: Float,
) {
    when (this) {
        TimeCountKind.SIGNAL_STRENGTH -> {
            val b = BatterySystem.SignalStrength.Builder()
            BatterySystem.SignalStrength.Name.fromValue(index)?.let { b.name = it }
            if (fieldName == "time_msec") b.time_msec = value else b.count = value
            system.signal_strength = system.signal_strength + b.build()
        }
        TimeCountKind.DATA_CONNECTION -> {
            val b = BatterySystem.DataConnection.Builder()
            BatterySystem.DataConnection.Name.fromValue(index)?.let { b.name = it }
            if (fieldName == "time_msec") b.time_msec = value else b.count = value
            system.data_connection = system.data_connection + b.build()
        }
        TimeCountKind.WIFI_STATE -> {
            val b = BatterySystem.WifiState.Builder()
            BatterySystem.WifiState.Name.fromValue(index)?.let { b.name = it }
            if (fieldName == "time_msec") b.time_msec = value else b.count = value
            system.wifi_state = system.wifi_state + b.build()
        }
        TimeCountKind.BLUETOOTH_STATE -> {
            val b = BatterySystem.BluetoothState.Builder()
            BatterySystem.BluetoothState.Name.fromValue(index)?.let { b.name = it }
            if (fieldName == "time_msec") b.time_msec = value else b.count = value
            system.bluetooth_state = system.bluetooth_state + b.build()
        }
        TimeCountKind.WIFI_SUPPLICANT_STATE -> {
            val b = BatterySystem.WifiSupplicantState.Builder()
            BatterySystem.WifiSupplicantState.Name.fromValue(index)?.let { b.name = it }
            if (fieldName == "time_msec") b.time_msec = value else b.count = value
            system.wifi_supplicant_state = system.wifi_supplicant_state + b.build()
        }
        TimeCountKind.WIFI_SIGNAL_STRENGTH -> {
            val b = BatterySystem.WifiSignalStrength.Builder()
            BatterySystem.WifiSignalStrength.Name.fromValue(index)?.let { b.name = it }
            if (fieldName == "time_msec") b.time_msec = value else b.count = value
            system.wifi_signal_strength = system.wifi_signal_strength + b.build()
        }
    }
}

private fun TimeCountKind.setEntry(
    system: BatterySystem.Builder,
    index: Int,
    fieldName: String,
    value: Float,
    section: String,
    counter: com.leekleak.batteryarcheologist.core.checkin.checkinutil.Counter,
) {
    when (this) {
        TimeCountKind.SIGNAL_STRENGTH -> {
            val b = system.signal_strength[index].newBuilder()
            if (fieldName == "time_msec") {
                if (b.time_msec != null) throwExisting(section, counter)
                b.time_msec = value
            } else {
                if (b.count != null) throwExisting(section, counter)
                b.count = value
            }
            system.signal_strength =
                system.signal_strength.toMutableList().also { it[index] = b.build() }
        }
        TimeCountKind.DATA_CONNECTION -> {
            val b = system.data_connection[index].newBuilder()
            if (fieldName == "time_msec") {
                if (b.time_msec != null) throwExisting(section, counter)
                b.time_msec = value
            } else {
                if (b.count != null) throwExisting(section, counter)
                b.count = value
            }
            system.data_connection =
                system.data_connection.toMutableList().also { it[index] = b.build() }
        }
        TimeCountKind.WIFI_STATE -> {
            val b = system.wifi_state[index].newBuilder()
            if (fieldName == "time_msec") {
                if (b.time_msec != null) throwExisting(section, counter)
                b.time_msec = value
            } else {
                if (b.count != null) throwExisting(section, counter)
                b.count = value
            }
            system.wifi_state =
                system.wifi_state.toMutableList().also { it[index] = b.build() }
        }
        TimeCountKind.BLUETOOTH_STATE -> {
            val b = system.bluetooth_state[index].newBuilder()
            if (fieldName == "time_msec") {
                if (b.time_msec != null) throwExisting(section, counter)
                b.time_msec = value
            } else {
                if (b.count != null) throwExisting(section, counter)
                b.count = value
            }
            system.bluetooth_state =
                system.bluetooth_state.toMutableList().also { it[index] = b.build() }
        }
        TimeCountKind.WIFI_SUPPLICANT_STATE -> {
            val b = system.wifi_supplicant_state[index].newBuilder()
            if (fieldName == "time_msec") {
                if (b.time_msec != null) throwExisting(section, counter)
                b.time_msec = value
            } else {
                if (b.count != null) throwExisting(section, counter)
                b.count = value
            }
            system.wifi_supplicant_state =
                system.wifi_supplicant_state.toMutableList().also { it[index] = b.build() }
        }
        TimeCountKind.WIFI_SIGNAL_STRENGTH -> {
            val b = system.wifi_signal_strength[index].newBuilder()
            if (fieldName == "time_msec") {
                if (b.time_msec != null) throwExisting(section, counter)
                b.time_msec = value
            } else {
                if (b.count != null) throwExisting(section, counter)
                b.count = value
            }
            system.wifi_signal_strength =
                system.wifi_signal_strength.toMutableList().also { it[index] = b.build() }
        }
    }
}

private fun throwExisting(
    section: String,
    counter: com.leekleak.batteryarcheologist.core.checkin.checkinutil.Counter,
): Nothing {
    counter.count("error-system-$section-exist", 1)
    throw CheckinParseException("$section section already exists")
}

// ---------------------------------------------------------------------------------------
// Controller data and step data
// ---------------------------------------------------------------------------------------

/**
 * Parses controller activity: idle time, rx time, power, followed by one transmit time
 * per level.
 */
private fun parseControllerData(
    section: String,
    record: List<String>,
): BatteryController.Builder {
    val reader = SliceReader(record)
    val idle = reader.readInt64()
    val rx = reader.readInt64()
    val power = reader.readInt64()
    if (!reader.hasRemaining) {
        throw CheckinParseException(
            "$section didn't contain any transmit level data: \"${record.joinToString(",")}\"",
        )
    }
    val builder = BatteryController.Builder()
    builder.idle_time_msec = idle
    builder.rx_time_msec = rx
    builder.power_mah = power
    var level = 0
    while (reader.hasRemaining) {
        val value = reader.readString()
        val time = SliceReader.parseInt64(value)
        val tx = BatteryTxLevel.Builder()
        tx.level = level++
        tx.time_msec = time
        builder.tx = builder.tx + tx.build()
    }
    return builder
}

/** Parses controller activity and adds it to an existing controller proto. */
private fun parseAndAccumulateControllerData(
    section: String,
    record: List<String>,
    target: BatteryController.Builder,
) {
    val parsed = parseControllerData(section, record).build()
    val targetTx = target.tx
    if (targetTx.isNotEmpty() && targetTx.size != parsed.tx.size) {
        throw CheckinParseException(
            "$section had different number of transmit levels: ${targetTx.size} vs ${parsed.tx.size}",
        )
    }
    target.idle_time_msec = (target.idle_time_msec ?: 0L) + (parsed.idle_time_msec ?: 0L)
    target.rx_time_msec = (target.rx_time_msec ?: 0L) + (parsed.rx_time_msec ?: 0L)
    target.power_mah = (target.power_mah ?: 0L) + (parsed.power_mah ?: 0L)
    if (targetTx.isEmpty()) {
        target.tx = parsed.tx
    } else {
        val byLevel = parsed.tx.associate { (it.level ?: 0) to (it.time_msec ?: 0L) }
        target.tx = targetTx.map { tx ->
            val level = tx.level ?: 0
            val b = tx.newBuilder()
            b.time_msec = (b.time_msec ?: 0L) + (byLevel[level] ?: 0L)
            b.build()
        }
    }
}

/**
 * Parses `csd`/`dsd`: elapsed time, battery level, display state, power-save mode and
 * idle mode.
 */
private fun parseStepData(record: List<String>): Pair<StepData, String?> {
    var warning: String? = null
    var timeMsec = 0f
    var level = 0f
    if (record.isNotEmpty()) {
        timeMsec = parseFloat32Value(record[0], "step time")
    }
    if (record.size > 1) {
        if (record[1] == "?") throw CheckinParseException("discovered ? for display state")
        level = parseFloat32Value(record[1], "step level")
    }

    var displayState = BatterySystem.DisplayState.State.MIXED
    if (record.size > 2) {
        displayState = when (record[2]) {
            "s+" -> BatterySystem.DisplayState.State.ON
            "s-" -> BatterySystem.DisplayState.State.OFF
            "sd" -> BatterySystem.DisplayState.State.DOZE
            "sds" -> BatterySystem.DisplayState.State.DOZE_SUSPEND
            "" -> BatterySystem.DisplayState.State.MIXED
            "?" -> throw CheckinParseException("discovered ? when parsing display state")
            else -> {
                warning = "unknown display state: ${record[2]}"
                BatterySystem.DisplayState.State.MIXED
            }
        }
    }

    var powerSaveMode = BatterySystem.PowerSaveMode.Mode.MIXED
    var idleMode = BatterySystem.IdleMode.Mode.NO_DATA
    if (record.size > 3) {
        when (record[3]) {
            "p+" -> powerSaveMode = BatterySystem.PowerSaveMode.Mode.ON
            "p-" -> powerSaveMode = BatterySystem.PowerSaveMode.Mode.OFF
            "" -> powerSaveMode = BatterySystem.PowerSaveMode.Mode.MIXED
            // i+ and i- were printed in the fourth slot by an old device bug.
            "i+" -> idleMode = BatterySystem.IdleMode.Mode.ON
            "i-" -> idleMode = BatterySystem.IdleMode.Mode.OFF
            else -> warning = "unknown power save mode: ${record[3]}"
        }
    }
    if (record.size > 4) {
        when (record[4]) {
            "i+" -> idleMode = BatterySystem.IdleMode.Mode.ON
            "i-" -> idleMode = BatterySystem.IdleMode.Mode.OFF
            "" -> idleMode = BatterySystem.IdleMode.Mode.MIXED
            else -> warning = "unknown idle mode: ${record[4]}"
        }
    }

    return Pair(
        StepData(timeMsec, level, displayState, powerSaveMode, idleMode),
        warning,
    )
}

// ---------------------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------------------

private fun parseFloat32Value(raw: String, section: String): Float =
    raw.toFloatOrNull() ?: throw SliceParseException("cannot parse float32 from '$raw' in $section")
