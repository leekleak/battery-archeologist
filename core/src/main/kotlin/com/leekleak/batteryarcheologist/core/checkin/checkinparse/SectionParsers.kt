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
//   * data connection enum values 17+. The entries and values are retained, but generated
//     Java proto2 getters collapse the unknown enum name to NONE;
//   * sections awl, ctf, fgs, gcf, jbc, rpm and wmct.

package com.leekleak.batteryarcheologist.core.checkin.checkinparse

import com.google.protobuf.Message
import com.leekleak.batteryarcheologist.core.checkin.sliceparse.SliceParseException
import com.leekleak.batteryarcheologist.core.checkin.sliceparse.SliceReader
import com.leekleak.batteryarcheologist.core.historianutils.HistorianUtils
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.App as BatteryApp
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.App.Apk as BatteryAppApk
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.App.Apk.Service as BatteryAppService
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.App.Child as BatteryAppChild
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.App.Network as BatteryAppNetwork
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.App.PowerUseItem as BatteryAppPowerUseItem
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.App.Process as BatteryAppProcess
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.App.ScheduledJob as BatteryAppScheduledJob
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.App.Sensor as BatteryAppSensor
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.App.StateTime as BatteryAppStateTime
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.App.Sync as BatteryAppSync
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.App.UserActivity as BatteryAppUserActivity
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.App.WakeupAlarm as BatteryAppWakeupAlarm
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.App.Wakelock as BatteryAppWakelock
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.App.Wifi as BatteryAppWifi
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.ControllerActivity as BatteryController
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.ControllerActivity.TxLevel as BatteryTxLevel
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.System as BatterySystem

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
            state.system.addChargeStep(
                BatterySystem.ChargeStep.newBuilder()
                    .setTimeMsec(data.timeMsec)
                    .setLevel(data.level)
                    .setDisplayState(data.displayState)
                    .setPowerSaveMode(data.powerSaveMode)
                    .setIdleMode(data.idleMode),
            )
            result(warning)
        }
        SectionNames.CHARGE_TIME_REMAINING -> {
            if (state.system.hasChargeTimeRemaining()) {
                state.counter.count("error-charge-time-remaining-exist", 1)
                throw CheckinParseException("charge time remaining field already exists")
            }
            val b = BatterySystem.ChargeTimeRemaining.newBuilder()
            val warning = parseLine(section, record, b)
            state.system.setChargeTimeRemaining(b)
            result(warning)
        }
        SectionNames.CPU -> result(parseAppSingleton(section, record, app, AppSingleton.CPU))
        SectionNames.DISCHARGE_STEP -> {
            val (data, warning) = parseStepData(record)
            state.system.addDischargeStep(
                BatterySystem.DischargeStep.newBuilder()
                    .setTimeMsec(data.timeMsec)
                    .setLevel(data.level)
                    .setDisplayState(data.displayState)
                    .setPowerSaveMode(data.powerSaveMode)
                    .setIdleMode(data.idleMode),
            )
            result(warning)
        }
        SectionNames.DISCHARGE_TIME_REMAINING -> {
            if (state.system.hasDischargeTimeRemaining()) {
                state.counter.count("error-discharge-time-remaining-exist", 1)
                throw CheckinParseException("discharge time remaining field already exists")
            }
            val b = BatterySystem.DischargeTimeRemaining.newBuilder()
            val warning = parseLine(section, record, b)
            state.system.setDischargeTimeRemaining(b)
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
                val b = BatterySystem.GlobalBluetooth.newBuilder()
                val warning = parseLine(section, record, b)
                state.system.setGlobalBluetooth(b)
                result(warning)
            } else {
                state.system.setGlobalBluetoothController(parseControllerData(section, record))
                result(null)
            }
        }
        SectionNames.GLOBAL_MODEM_CONTROLLER -> {
            state.system.setGlobalModemController(parseControllerData(section, record))
            result(null)
        }
        SectionNames.GLOBAL_NETWORK -> result(globalNetwork(record, state.system, state.counter))
        SectionNames.GLOBAL_WIFI_CONTROLLER -> {
            state.system.setGlobalWifiController(parseControllerData(section, record))
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
): String? {
    val target: Message.Builder = when (kind) {
        AppSingleton.AUDIO ->
            if (app.hasAudio()) app.audio.toBuilder() else BatteryApp.Audio.newBuilder()
        AppSingleton.BLUETOOTH_MISC ->
            if (app.hasBluetoothMisc()) app.bluetoothMisc.toBuilder()
            else BatteryApp.BluetoothMisc.newBuilder()
        AppSingleton.CAMERA ->
            if (app.hasCamera()) app.camera.toBuilder() else BatteryApp.Camera.newBuilder()
        AppSingleton.CPU ->
            if (app.hasCpu()) app.cpu.toBuilder() else BatteryApp.Cpu.newBuilder()
        AppSingleton.FLASHLIGHT ->
            if (app.hasFlashlight()) app.flashlight.toBuilder()
            else BatteryApp.Flashlight.newBuilder()
        AppSingleton.FOREGROUND ->
            if (app.hasForeground()) app.foreground.toBuilder()
            else BatteryApp.Foreground.newBuilder()
        AppSingleton.VIBRATOR ->
            if (app.hasVibrator()) app.vibrator.toBuilder() else BatteryApp.Vibrator.newBuilder()
        AppSingleton.VIDEO ->
            if (app.hasVideo()) app.video.toBuilder() else BatteryApp.Video.newBuilder()
    }
    val warning = parseAndAccumulate(section, record, target)
    when (kind) {
        AppSingleton.AUDIO -> app.setAudio(target as BatteryApp.Audio.Builder)
        AppSingleton.BLUETOOTH_MISC ->
            app.setBluetoothMisc(target as BatteryApp.BluetoothMisc.Builder)
        AppSingleton.CAMERA -> app.setCamera(target as BatteryApp.Camera.Builder)
        AppSingleton.CPU -> app.setCpu(target as BatteryApp.Cpu.Builder)
        AppSingleton.FLASHLIGHT -> app.setFlashlight(target as BatteryApp.Flashlight.Builder)
        AppSingleton.FOREGROUND -> app.setForeground(target as BatteryApp.Foreground.Builder)
        AppSingleton.VIBRATOR -> app.setVibrator(target as BatteryApp.Vibrator.Builder)
        AppSingleton.VIDEO -> app.setVideo(target as BatteryApp.Video.Builder)
    }
    return warning
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
        AppController.BLUETOOTH -> if (app.hasBluetoothController()) app.bluetoothController else null
        AppController.MODEM -> if (app.hasModemController()) app.modemController else null
        AppController.WIFI -> if (app.hasWifiController()) app.wifiController else null
    }
    if (existing == null) {
        val parsed = parseControllerData(section, record)
        when (kind) {
            AppController.BLUETOOTH -> app.setBluetoothController(parsed)
            AppController.MODEM -> app.setModemController(parsed)
            AppController.WIFI -> app.setWifiController(parsed)
        }
    } else {
        val target = existing.toBuilder()
        parseAndAccumulateControllerData(section, record, target)
        when (kind) {
            AppController.BLUETOOTH -> app.setBluetoothController(target)
            AppController.MODEM -> app.setModemController(target)
            AppController.WIFI -> app.setWifiController(target)
        }
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
    for (i in 0 until app.childCount) {
        if (app.getChild(i).name == name) {
            childIndex = i
            break
        }
    }
    val child = if (childIndex >= 0) {
        app.getChild(childIndex).toBuilder()
    } else {
        BatteryAppChild.newBuilder().setName(name)
    }

    val apk = if (child.hasApk()) child.apk.toBuilder() else BatteryAppApk.newBuilder()
    if (state.apkSeen.add(ApkId(rawUid, name))) {
        apk.setWakeups(apk.wakeups + wakeups)
    }

    val service = BatteryAppService.newBuilder()
    val warning = parseLine(SectionNames.APK, reader.remaining, service)

    var merged = false
    for (i in 0 until apk.serviceCount) {
        if (apk.getService(i).name == service.name) {
            val target = apk.getService(i).toBuilder()
            accumulate(target, service.build(), skipFields = setOf("name"))
            apk.setService(i, target)
            merged = true
            break
        }
    }
    if (!merged) {
        apk.addService(service)
    }

    child.setApk(apk)
    if (childIndex >= 0) {
        app.setChild(childIndex, child)
    } else {
        app.addChild(child)
    }
    return warning
}

/**
 * Parses `pr`: process name, user time, system time, foreground time, starts, ANRs, crashes.
 * Rows for the same process name are accumulated.
 */
private fun parseAppProcess(record: List<String>, app: BatteryApp.Builder): String? {
    val parsed = BatteryAppProcess.newBuilder()
    val warning = parseLine(SectionNames.PROCESS, record, parsed)
    for (i in 0 until app.processCount) {
        if (app.getProcess(i).name == parsed.name) {
            val target = app.getProcess(i).toBuilder()
            accumulate(target, parsed.build(), skipFields = setOf("name"))
            app.setProcess(i, target)
            return warning
        }
    }
    app.addProcess(parsed)
    return warning
}

/**
 * Parses `sr`: sensor number, apportioned time, count, background count, actual time,
 * background actual time. Rows for the same sensor are accumulated.
 */
private fun parseAppSensor(record: List<String>, app: BatteryApp.Builder): String? {
    val parsed = BatteryAppSensor.newBuilder()
    val warning = parseLine(SectionNames.SENSOR, record, parsed)
    for (i in 0 until app.sensorCount) {
        if (app.getSensor(i).number == parsed.number) {
            val target = app.getSensor(i).toBuilder()
            accumulate(target, parsed.build(), skipFields = setOf("number"))
            app.setSensor(i, target)
            return warning
        }
    }
    app.addSensor(parsed)
    return warning
}

/**
 * Parses `jb`: job name, total time, count, background time, background count.
 */
private fun parseAppScheduledJob(record: List<String>, app: BatteryApp.Builder): String? {
    val parsed = BatteryAppScheduledJob.newBuilder()
    val warning = parseLine(SectionNames.JOB, record, parsed)
    parsed.setName(HistorianUtils.scrubPII(parsed.name))
    for (i in 0 until app.scheduledJobCount) {
        if (app.getScheduledJob(i).name == parsed.name) {
            val target = app.getScheduledJob(i).toBuilder()
            accumulate(target, parsed.build(), skipFields = setOf("name"))
            app.setScheduledJob(i, target)
            return warning
        }
    }
    app.addScheduledJob(parsed)
    return warning
}

/**
 * Parses `sy`: sync name, total time, count, background time, background count.
 */
private fun parseAppSync(record: List<String>, app: BatteryApp.Builder): String? {
    val parsed = BatteryAppSync.newBuilder()
    val warning = parseLine(SectionNames.SYNC, record, parsed)
    parsed.setName(HistorianUtils.scrubPII(parsed.name))
    for (i in 0 until app.syncCount) {
        if (app.getSync(i).name == parsed.name) {
            val target = app.getSync(i).toBuilder()
            accumulate(target, parsed.build(), skipFields = setOf("name"))
            app.setSync(i, target)
            return warning
        }
    }
    app.addSync(parsed)
    return warning
}

/**
 * Parses `wua`: wakeup alarm name and count. Rows with the same name are accumulated.
 */
private fun parseAppWakeupAlarm(record: List<String>, app: BatteryApp.Builder): String? {
    val parsed = BatteryAppWakeupAlarm.newBuilder()
    val warning = parseLine(SectionNames.WAKEUP_ALARM, record, parsed)
    parsed.setName(HistorianUtils.scrubPII(parsed.name))
    for (i in 0 until app.wakeupAlarmCount) {
        if (app.getWakeupAlarm(i).name == parsed.name) {
            val target = app.getWakeupAlarm(i).toBuilder()
            accumulate(target, parsed.build(), skipFields = setOf("name"))
            app.setWakeupAlarm(i, target)
            return warning
        }
    }
    app.addWakeupAlarm(parsed)
    return warning
}

/**
 * Parses `ua`: activity counts in order OTHER, BUTTON, TOUCH, ACCESSIBILITY.
 *
 * Newer builds print additional activity types. They are preserved as unknown enum
 * numbers so no data is lost on round-trip.
 */
private fun parseAppUserActivity(record: List<String>, app: BatteryApp.Builder): String? {
    val warning = if (record.size > 4) {
        "user activity now has ${record.size - 4} additional fields"
    } else {
        null
    }
    for ((i, rawCount) in record.withIndex()) {
        val count = parseFloat32Value(rawCount, SectionNames.USER_ACTIVITY)
        if (i < app.userActivityCount) {
            val b = app.getUserActivity(i).toBuilder()
            b.setCount(b.count + count)
            app.setUserActivity(i, b)
            continue
        }
        val b = BatteryAppUserActivity.newBuilder()
        // Unknown activity types from newer builds cannot be represented by the generated
        // enum getter; keep the count and leave the name at its default.
        BatteryAppUserActivity.Name.forNumber(i)?.let { b.setName(it) }
        b.setCount(count)
        app.addUserActivity(b)
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
        val target = if (app.hasStateTime()) {
            app.stateTime.toBuilder()
        } else {
            BatteryAppStateTime.newBuilder()
        }
        val warning = parseAndAccumulate(SectionNames.STATE_TIME, record, target)
        app.setStateTime(target)
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

    val stateTime = if (app.hasStateTime()) app.stateTime.toBuilder() else BatteryAppStateTime.newBuilder()
    stateTime.setTopTimeMsec(stateTime.topTimeMsec + top)
    stateTime.setForegroundServiceTimeMsec(stateTime.foregroundServiceTimeMsec + foregroundService)
    stateTime.setTopSleepingTimeMsec(stateTime.topSleepingTimeMsec + topSleeping)
    stateTime.setForegroundTimeMsec(stateTime.foregroundTimeMsec + foreground)
    stateTime.setBackgroundTimeMsec(stateTime.backgroundTimeMsec + background)
    stateTime.setCachedTimeMsec(stateTime.cachedTimeMsec + cached)
    app.setStateTime(stateTime)
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

    for (i in 0 until app.wakelockCount) {
        val existing = app.getWakelock(i)
        if (existing.name == name) {
            val b = existing.toBuilder()
            b.setFullTimeMsec(b.fullTimeMsec + fullTime)
            b.setFullCount(b.fullCount + fullCount)
            b.setFullCurrentDurationMsec(maxOf(b.fullCurrentDurationMsec, fullCurrent))
            b.setFullMaxDurationMsec(maxOf(b.fullMaxDurationMsec, fullMax))
            if (fullTotal != -1L) {
                b.setFullTotalDurationMsec(b.fullTotalDurationMsec + fullTotal)
            }
            b.setPartialTimeMsec(b.partialTimeMsec + partialTime)
            b.setPartialCount(b.partialCount + partialCount)
            b.setPartialCurrentDurationMsec(maxOf(b.partialCurrentDurationMsec, partialCurrent))
            b.setPartialMaxDurationMsec(maxOf(b.partialMaxDurationMsec, partialMax))
            if (partialTotal != -1L) {
                b.setPartialTotalDurationMsec(b.partialTotalDurationMsec + partialTotal)
            }
            b.setWindowTimeMsec(b.windowTimeMsec + windowTime)
            b.setWindowCount(b.windowCount + windowCount)
            b.setWindowCurrentDurationMsec(maxOf(b.windowCurrentDurationMsec, windowCurrent))
            b.setWindowMaxDurationMsec(maxOf(b.windowMaxDurationMsec, windowMax))
            if (windowTotal != -1L) {
                b.setWindowTotalDurationMsec(b.windowTotalDurationMsec + windowTotal)
            }
            app.setWakelock(i, b)
            return warning
        }
    }

    app.addWakelock(
        BatteryAppWakelock.newBuilder()
            .setName(name)
            .setFullTimeMsec(fullTime)
            .setFullCount(fullCount)
            .setFullCurrentDurationMsec(fullCurrent)
            .setFullMaxDurationMsec(fullMax)
            .setFullTotalDurationMsec(fullTotal)
            .setPartialTimeMsec(partialTime)
            .setPartialCount(partialCount)
            .setPartialCurrentDurationMsec(partialCurrent)
            .setPartialMaxDurationMsec(partialMax)
            .setPartialTotalDurationMsec(partialTotal)
            .setWindowTimeMsec(windowTime)
            .setWindowCount(windowCount)
            .setWindowCurrentDurationMsec(windowCurrent)
            .setWindowMaxDurationMsec(windowMax)
            .setWindowTotalDurationMsec(windowTotal),
    )
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
    val parsed = BatteryAppNetwork.newBuilder()
    val warning = parseLine(SectionNames.NETWORK, record, parsed)
    parsed.setMobileActiveTimeMsec(parsed.mobileActiveTimeMsec / 1e3f)
    if (!app.hasNetwork()) {
        app.setNetwork(parsed)
    } else {
        val target = app.network.toBuilder()
        accumulate(target, parsed.build())
        app.setNetwork(target)
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
    val parsed = BatteryAppWifi.newBuilder()
    val warning = parseLine(SectionNames.WIFI, record, parsed)
    parsed.setFullWifiLockTimeMsec(parsed.fullWifiLockTimeMsec / 1e3f)
    parsed.setScanTimeMsec(parsed.scanTimeMsec / 1e3f)
    parsed.setRunningTimeMsec(parsed.runningTimeMsec / 1e3f)
    if (!app.hasWifi()) {
        app.setWifi(parsed)
    } else {
        val target = app.wifi.toBuilder()
        accumulate(target, parsed.build())
        app.setWifi(target)
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
        val target = if (app.hasPowerUseItem()) app.powerUseItem.toBuilder() else BatteryAppPowerUseItem.newBuilder()
        target.setComputedPowerMah(target.computedPowerMah + computedPowerMah)
        app.setPowerUseItem(target)
    } else {
        val name = powerUseItemNameMap[rawName]
        if (name == null) {
            state.counter.count("unknown-app-system-power-use-item-$rawName", 1)
            return "Unknown powerUseItem name $rawName"
        }
        state.system.addPowerUseItem(
            BatterySystem.PowerUseItem.newBuilder()
                .setName(name)
                .setComputedPowerMah(computedPowerMah),
        )
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
    if (system.hasBattery()) {
        throw CheckinParseException("system battery field already exists")
    }
    val normalized = if (record.firstOrNull() == "N/A") listOf("0") + record.drop(1) else record
    val parsed = BatterySystem.Battery.newBuilder()
    val warning = parseLine(SectionNames.BATTERY, normalized, parsed)
    system.setBattery(parsed)
    return warning
}

/** Parses `dc`: lower/upper bound discharged points, screen-on/off, and optional mAh. */
private fun systemBatteryDischarge(record: List<String>, system: BatterySystem.Builder): String? {
    if (system.hasBatteryDischarge()) {
        throw CheckinParseException("battery discharge field already exists")
    }
    val parsed = BatterySystem.BatteryDischarge.newBuilder()
    val warning = parseLine(SectionNames.BATTERY_DISCHARGE, record, parsed)
    system.setBatteryDischarge(parsed)
    return warning
}

/** Parses `lv`: start and current battery level. */
private fun systemBatteryLevel(record: List<String>, system: BatterySystem.Builder): String? {
    if (system.hasBatteryLevel()) {
        throw CheckinParseException("battery level field already exists")
    }
    val parsed = BatterySystem.BatteryLevel.newBuilder()
    val warning = parseLine(SectionNames.BATTERY_LEVEL, record, parsed)
    system.setBatteryLevel(parsed)
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
    if (system.hasMisc()) {
        counter.count("error-parse-system-misc-exist", 1)
        throw CheckinParseException("misc field already exists")
    }
    val adjusted = ArrayList(record)
    if (adjusted.isEmpty()) throw CheckinParseException("m field didn't contain enough fields")
    // screen_off_time_msec is calculated later; insert a placeholder in its proto position.
    adjusted.add(1, "0")
    val parsed = BatterySystem.Misc.newBuilder()
    val warning = if (reportVersion >= 14) {
        // Legacy wifi/bluetooth/network fields were removed from the line in v14.
        repeat(7) { adjusted.add(3, "0") }
        parseLine(SectionNames.MISC, adjusted, parsed)
    } else {
        // Legacy input event count is printed in the middle of the older layout.
        val withoutLegacyInput = adjusted.filterIndexed { index, _ -> index != 12 }
        parseLine(SectionNames.MISC, withoutLegacyInput, parsed)
    }
    if (reportVersion < 9) {
        // Older devices printed these durations in microseconds.
        parsed.setFullWakelockTimeMsec(parsed.fullWakelockTimeMsec / 1e3f)
        parsed.setPartialWakelockTimeMsec(parsed.partialWakelockTimeMsec / 1e3f)
        parsed.setMobileActiveTimeMsec(parsed.mobileActiveTimeMsec / 1e3f)
        parsed.setMobileActiveAdjustedTimeMsec(parsed.mobileActiveAdjustedTimeMsec / 1e3f)
    }
    system.setMisc(parsed)
    return warning
}

/** Parses `pws`: capacity, computed power, and drained-power bounds. */
private fun systemPowerUseSummary(
    record: List<String>,
    system: BatterySystem.Builder,
    counter: com.leekleak.batteryarcheologist.core.checkin.checkinutil.Counter,
): String? {
    if (system.hasPowerUseSummary()) {
        counter.count("error-parse-system-power-use-summary-exist", 1)
        throw CheckinParseException("power use summary field already exists")
    }
    val parsed = BatterySystem.PowerUseSummary.newBuilder()
    val warning = parseLine(SectionNames.POWER_USE_SUMMARY, record, parsed)
    system.setPowerUseSummary(parsed)
    return warning
}

/** Parses `gn`: global mobile/wifi traffic and packet counters. */
private fun globalNetwork(
    record: List<String>,
    system: BatterySystem.Builder,
    counter: com.leekleak.batteryarcheologist.core.checkin.checkinutil.Counter,
): String? {
    if (system.hasGlobalNetwork()) {
        counter.count("error-parse-system-global-network-exist", 1)
        throw CheckinParseException("global network field already exists")
    }
    val parsed = BatterySystem.GlobalNetwork.newBuilder()
    val warning = parseLine(SectionNames.GLOBAL_NETWORK, record, parsed)
    system.setGlobalNetwork(parsed)
    return warning
}

/** Parses `gwfl`: global wifi on/running times and legacy controller fields. */
private fun globalWifi(record: List<String>, system: BatterySystem.Builder): String? {
    val parsed = BatterySystem.GlobalWifi.newBuilder()
    val warning = parseLine(SectionNames.GLOBAL_WIFI, record, parsed)
    system.setGlobalWifi(parsed)
    return warning
}

/** Parses `kwl`: kernel wakelock name, time, count, current and max duration. */
private fun systemKernelWakelock(record: List<String>, system: BatterySystem.Builder): String? {
    val parsed = BatterySystem.KernelWakelock.newBuilder()
    val warning = parseLine(SectionNames.KERNEL_WAKELOCK, record, parsed)
    for (i in 0 until system.kernelWakelockCount) {
        val existing = system.getKernelWakelock(i)
        if (existing.name == parsed.name) {
            val b = existing.toBuilder()
            b.setTimeMsec(b.timeMsec + parsed.timeMsec)
            b.setCount(b.count + parsed.count)
            b.setCurrentDurationMsec(maxOf(b.currentDurationMsec, parsed.currentDurationMsec))
            b.setMaxDurationMsec(maxOf(b.maxDurationMsec, parsed.maxDurationMsec))
            system.setKernelWakelock(i, b)
            return warning
        }
    }
    system.addKernelWakelock(parsed)
    return warning
}

/** Parses `wr`: wakeup reason name, time and (v11+) count. */
private fun systemWakeupReason(record: List<String>, system: BatterySystem.Builder): String? {
    val parsed = BatterySystem.WakeupReason.newBuilder()
    val warning = parseLine(SectionNames.WAKEUP_REASON, record, parsed)
    system.addWakeupReason(parsed)
    return warning
}

/** Parses `sst`: time spent trying to acquire a signal. */
private fun systemSignalScanningTime(
    record: List<String>,
    system: BatterySystem.Builder,
    counter: com.leekleak.batteryarcheologist.core.checkin.checkinutil.Counter,
): String? {
    if (system.hasSignalScanningTime()) {
        counter.count("error-parse-system-signal-scanning-time-exist", 1)
        throw CheckinParseException("signal scanning time field already exists")
    }
    val parsed = BatterySystem.SignalScanningTime.newBuilder()
    val warning = parseLine(SectionNames.SIGNAL_SCANNING_TIME, record, parsed)
    system.setSignalScanningTime(parsed)
    return warning
}

/** Parses `br`: duration spent at each of the five screen brightness levels. */
private fun parseSystemScreenBrightness(
    record: List<String>,
    system: BatterySystem.Builder,
): String? {
    if (system.screenBrightnessCount > 0) {
        throw CheckinParseException("screen brightness field already exists")
    }
    val expected = BatterySystem.ScreenBrightness.Name.values().size
    if (record.size != expected) {
        throw CheckinParseException(
            "wrong number of screen brightness fields: got ${record.size}, want $expected",
        )
    }
    for ((index, rawValue) in record.withIndex()) {
        system.addScreenBrightness(
            BatterySystem.ScreenBrightness.newBuilder()
                .setName(BatterySystem.ScreenBrightness.Name.forNumber(index))
                .setTimeMsec(parseFloat32Value(rawValue, SectionNames.SCREEN_BRIGHTNESS)),
        )
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
 * Values beyond the enum's defined range are kept as unknown enum numbers, matching the
 * upstream parser, which appends every record value.
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
            kind.addEntry(system, kind.newEntry(index, fieldName, value))
        }
    } else if (currentLen == record.size) {
        for ((index, rawValue) in record.withIndex()) {
            val value = parseFloat32Value(rawValue, section)
            val entry = kind.getEntry(system, index).toBuilder()
            val field = entry.descriptorForType.findFieldByName(fieldName)
                ?: throw CheckinParseException("$section message has no $fieldName field")
            if (entry.hasField(field)) {
                counter.count("error-system-$section-exist", 1)
                throw CheckinParseException("$section section already exists")
            }
            entry.setField(field, value)
            kind.setEntry(system, index, entry.build())
        }
    } else {
        throw CheckinParseException("inconsistent number of fields in $section")
    }
    return warning
}

private fun TimeCountKind.entryCount(system: BatterySystem.Builder): Int = when (this) {
    TimeCountKind.SIGNAL_STRENGTH -> system.signalStrengthCount
    TimeCountKind.DATA_CONNECTION -> system.dataConnectionCount
    TimeCountKind.WIFI_STATE -> system.wifiStateCount
    TimeCountKind.BLUETOOTH_STATE -> system.bluetoothStateCount
    TimeCountKind.WIFI_SUPPLICANT_STATE -> system.wifiSupplicantStateCount
    TimeCountKind.WIFI_SIGNAL_STRENGTH -> system.wifiSignalStrengthCount
}

private fun TimeCountKind.newEntry(index: Int, fieldName: String, value: Float): Message {
    // Generated Java proto2 enums only accept constants declared in the proto; devices can
    // print enum numbers from newer Android releases. Those unknown numbers cannot be
    // represented by the generated getter (it falls back to the first constant), so the
    // name is left unset for them while the time/count value is still retained.
    return when (this) {
        TimeCountKind.SIGNAL_STRENGTH -> {
            val b = BatterySystem.SignalStrength.newBuilder()
            BatterySystem.SignalStrength.Name.forNumber(index)?.let { b.setName(it) }
            if (fieldName == "time_msec") b.setTimeMsec(value) else b.setCount(value)
            b.build()
        }
        TimeCountKind.DATA_CONNECTION -> {
            val b = BatterySystem.DataConnection.newBuilder()
            BatterySystem.DataConnection.Name.forNumber(index)?.let { b.setName(it) }
            if (fieldName == "time_msec") b.setTimeMsec(value) else b.setCount(value)
            b.build()
        }
        TimeCountKind.WIFI_STATE -> {
            val b = BatterySystem.WifiState.newBuilder()
            BatterySystem.WifiState.Name.forNumber(index)?.let { b.setName(it) }
            if (fieldName == "time_msec") b.setTimeMsec(value) else b.setCount(value)
            b.build()
        }
        TimeCountKind.BLUETOOTH_STATE -> {
            val b = BatterySystem.BluetoothState.newBuilder()
            BatterySystem.BluetoothState.Name.forNumber(index)?.let { b.setName(it) }
            if (fieldName == "time_msec") b.setTimeMsec(value) else b.setCount(value)
            b.build()
        }
        TimeCountKind.WIFI_SUPPLICANT_STATE -> {
            val b = BatterySystem.WifiSupplicantState.newBuilder()
            BatterySystem.WifiSupplicantState.Name.forNumber(index)?.let { b.setName(it) }
            if (fieldName == "time_msec") b.setTimeMsec(value) else b.setCount(value)
            b.build()
        }
        TimeCountKind.WIFI_SIGNAL_STRENGTH -> {
            val b = BatterySystem.WifiSignalStrength.newBuilder()
            BatterySystem.WifiSignalStrength.Name.forNumber(index)?.let { b.setName(it) }
            if (fieldName == "time_msec") b.setTimeMsec(value) else b.setCount(value)
            b.build()
        }
    }
}

private fun TimeCountKind.addEntry(system: BatterySystem.Builder, message: Message) {
    when (this) {
        TimeCountKind.SIGNAL_STRENGTH ->
            system.addSignalStrength(message as BatterySystem.SignalStrength)
        TimeCountKind.DATA_CONNECTION ->
            system.addDataConnection(message as BatterySystem.DataConnection)
        TimeCountKind.WIFI_STATE -> system.addWifiState(message as BatterySystem.WifiState)
        TimeCountKind.BLUETOOTH_STATE ->
            system.addBluetoothState(message as BatterySystem.BluetoothState)
        TimeCountKind.WIFI_SUPPLICANT_STATE ->
            system.addWifiSupplicantState(message as BatterySystem.WifiSupplicantState)
        TimeCountKind.WIFI_SIGNAL_STRENGTH ->
            system.addWifiSignalStrength(message as BatterySystem.WifiSignalStrength)
    }
}

private fun TimeCountKind.getEntry(system: BatterySystem.Builder, index: Int): Message = when (this) {
    TimeCountKind.SIGNAL_STRENGTH -> system.getSignalStrength(index)
    TimeCountKind.DATA_CONNECTION -> system.getDataConnection(index)
    TimeCountKind.WIFI_STATE -> system.getWifiState(index)
    TimeCountKind.BLUETOOTH_STATE -> system.getBluetoothState(index)
    TimeCountKind.WIFI_SUPPLICANT_STATE -> system.getWifiSupplicantState(index)
    TimeCountKind.WIFI_SIGNAL_STRENGTH -> system.getWifiSignalStrength(index)
}

private fun TimeCountKind.setEntry(
    system: BatterySystem.Builder,
    index: Int,
    message: Message,
) {
    when (this) {
        TimeCountKind.SIGNAL_STRENGTH ->
            system.setSignalStrength(index, message as BatterySystem.SignalStrength)
        TimeCountKind.DATA_CONNECTION ->
            system.setDataConnection(index, message as BatterySystem.DataConnection)
        TimeCountKind.WIFI_STATE -> system.setWifiState(index, message as BatterySystem.WifiState)
        TimeCountKind.BLUETOOTH_STATE ->
            system.setBluetoothState(index, message as BatterySystem.BluetoothState)
        TimeCountKind.WIFI_SUPPLICANT_STATE ->
            system.setWifiSupplicantState(index, message as BatterySystem.WifiSupplicantState)
        TimeCountKind.WIFI_SIGNAL_STRENGTH ->
            system.setWifiSignalStrength(index, message as BatterySystem.WifiSignalStrength)
    }
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
    val builder = BatteryController.newBuilder()
        .setIdleTimeMsec(idle)
        .setRxTimeMsec(rx)
        .setPowerMah(power)
    var level = 0
    while (reader.hasRemaining) {
        val value = reader.readString()
        val time = SliceReader.parseInt64(value)
        builder.addTx(BatteryTxLevel.newBuilder().setLevel(level++).setTimeMsec(time))
    }
    return builder
}

/** Parses controller activity and adds it to an existing controller proto. */
private fun parseAndAccumulateControllerData(
    section: String,
    record: List<String>,
    target: BatteryController.Builder,
) {
    val parsed = parseControllerData(section, record)
    if (target.txCount != parsed.txCount) {
        throw CheckinParseException(
            "$section had different number of transmit levels: ${target.txCount} vs ${parsed.txCount}",
        )
    }
    target.setIdleTimeMsec(target.idleTimeMsec + parsed.idleTimeMsec)
    target.setRxTimeMsec(target.rxTimeMsec + parsed.rxTimeMsec)
    target.setPowerMah(target.powerMah + parsed.powerMah)
    val byLevel = parsed.txList.associate { it.level to it.timeMsec }
    for (i in 0 until target.txCount) {
        val b = target.getTx(i).toBuilder()
        b.setTimeMsec(b.timeMsec + (byLevel[b.level] ?: 0L))
        target.setTx(i, b)
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
