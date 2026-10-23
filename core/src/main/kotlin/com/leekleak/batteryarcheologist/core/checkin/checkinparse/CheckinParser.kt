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

// Kotlin port of checkinparse.ParseBatteryStats, CreateBatteryReport,
// parsePackageManager, processAppInfo, aggregateAppApk and the post-processing
// functions from third_party/battery-historian/checkinparse/checkin_parse.go.

package com.leekleak.batteryarcheologist.core.checkin.checkinparse

import com.leekleak.batteryarcheologist.core.checkin.checkinutil.BatteryReport
import com.leekleak.batteryarcheologist.core.checkin.checkinutil.CheckinUtil
import com.leekleak.batteryarcheologist.core.checkin.checkinutil.IntCounter
import com.leekleak.batteryarcheologist.core.checkin.sliceparse.SliceReader
import com.leekleak.batteryarcheologist.core.model.PackageInfoModel
import com.leekleak.batteryarcheologist.core.packageinfo.appId
import com.leekleak.batteryarcheologist.core.packageinfo.guessPackage
import com.leekleak.batteryarcheologist.core.packageinfo.isSandboxedProcess
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats as BatteryStatsProto
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App as BatteryApp
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Apk as BatteryAppApk
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Apk.Service as BatteryAppService
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Child as BatteryAppChild
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.App.Wifi as BatteryAppWifi
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.ControllerActivity as BatteryController
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.ControllerActivity.TxLevel as BatteryTxLevel
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats.System as BatterySystem
import com.leekleak.batteryarcheologist.proto.build.Build
import com.leekleak.batteryarcheologist.proto.usagestats.PackageInfo

/**
 * Parses `dumpsys batterystats --checkin` captures into a battery stats proto.
 *
 * The parser intentionally keeps the upstream warning behaviour: unknown sections, newer
 * fields, and unsupported newer power-use items become warnings. Only structural problems
 * (an unreadable row, a missing version, duplicate singletons) become errors.
 */
object CheckinParser {
    /**
     * Parses [raw] checkin text.
     *
     * @param raw the complete checkin capture. History rows are ignored here; they are
     *   consumed by the separate history parser.
     * @param packages package metadata collected from a bug report. May be empty when the
     *   checkin capture itself contains `uid` rows (which it normally does).
     */
    fun parse(raw: String, packages: List<PackageInfoModel>): CheckinResult =
        CheckinParserImpl(CheckinUtil.createBatteryReport(raw), packages).parse()
}

/** The last report version the upstream 2017 parser knew about. */
private const val MAX_UPSTREAM_REPORT_VERSION = 21

/** Oldest report version the upstream parser accepts. */
private const val MIN_PARSE_REPORT_VERSION = 11

private class CheckinParserImpl(
    private val report: BatteryReport,
    packages: List<PackageInfoModel>,
) {
    private val state = ParserState(report, packages)
    private var reportVersion = -1
    private var aggregationType = BatteryStatsProto.AggregationType.SINCE_CHARGED

    fun parse(): CheckinResult {
        if (!parsePackageManager()) {
            return CheckinResult(null, state.warnings.toList(), state.errors.toList())
        }

        try {
            parseRows()
        } catch (e: CheckinParseException) {
            state.errors += e.message ?: e.toString()
            return CheckinResult(null, state.warnings.toList(), state.errors.toList())
        } catch (e: Exception) {
            state.errors += e.message ?: e.toString()
            return CheckinResult(null, state.warnings.toList(), state.errors.toList())
        }

        val stats = buildStats()
        return CheckinResult(stats, state.warnings.toList(), state.errors.toList())
    }

    /**
     * Builds the UID -> app map from the caller-supplied package list and the checkin
     * `uid` rows. All packages sharing a UID become children of the same app proto.
     */
    private fun parsePackageManager(): Boolean {
        // Use maps to ensure we don't double count packages. `models` is what guessPackage
        // consumes; `protos` is the generated usagestats shape used for App.Child entries.
        val models = LinkedHashMap<String, PackageInfoModel>()
        val protos = LinkedHashMap<String, PackageInfo>()
        for (pkg in state.packages) {
            models[pkg.name] = pkg
            protos[pkg.name] = pkg.toProtoPackageInfo()
        }

        for (row in report.rawBatteryStats) {
            if (row.size <= 3 || row[3] != SectionNames.UID) continue
            try {
                // Expect rows like "9,0,i,uid,1000,com.android.settings".
                val reader = SliceReader(row.subList(4, row.size))
                val rawUid = reader.readInt32()
                val name = reader.readString()
                val uid = appId(rawUid)
                val existing = protos[name]
                if (existing == null) {
                    val builder = PackageInfo.Builder()
                    builder.pkg_name = name
                    builder.uid = uid
                    protos[name] = builder.build()
                    models[name] = PackageInfoModel(name = name, uid = uid)
                } else if (appId(existing.uid ?: 0) == 0) {
                    // Some uploaded package entries have a uid of 0; prefer the checkin uid.
                    protos[name] = existing.newBuilder().also { it.uid = uid }.build()
                    models[name]?.let { models[name] = it.copy(uid = uid) }
                }
            } catch (e: Exception) {
                state.errors += "error parsing PackageManager: ${e.message}"
                return false
            }
        }

        // The package list is now complete. Create one App proto per UID and attach every
        // package as a Child.
        for (pkg in protos.values) {
            val uid = appId(pkg.uid ?: 0)
            if (uid == 0) {
                // Packages uploaded with an unknown uid cannot be attributed.
                continue
            }
            val app = state.apps.getOrPut(uid) {
                BatteryApp.Builder().also { it.uid = uid }
            }
            app.child = app.child + childFrom(pkg)
        }
        state.resolvedPackages = models
        state.resolvedUsagePackages = protos
        return true
    }

    /** Reads all aggregated (info/SINCE_CHARGED) checkin rows. */
    private fun parseRows() {
        val packageList = state.resolvedPackages.values.toList()

        for (row in report.rawBatteryStats) {
            // The first element is '9', a legacy field that used to carry the report version.
            val reader = SliceReader(row.subList(1, row.size))
            val rawUid = reader.readInt32()
            val rawAggregationType = reader.readString()
            val section = reader.readString()

            if (section == SectionNames.UID) {
                // uid rows were parsed by parsePackageManager().
                continue
            }

            if (section == SectionNames.VERSION) {
                if (reportVersion != -1) {
                    state.errors += "multiple ${SectionNames.VERSION} lines encountered"
                }
                try {
                    // e.g. "9,0,i,vers,36,216,BE4B.251210.005,BE4B.251210.005"; the extra
                    // build fields are not needed by the stats parser.
                    reportVersion = SliceReader(reader.remaining).readInt32()
                } catch (e: Exception) {
                    state.errors += "error parsing version data: ${e.message}"
                }
                continue
            }

            when (rawAggregationType) {
                SectionNames.SINCE_CHARGED ->
                    aggregationType = BatteryStatsProto.AggregationType.SINCE_CHARGED
                SectionNames.CURRENT ->
                    aggregationType = BatteryStatsProto.AggregationType.CURRENT
                SectionNames.UNPLUGGED ->
                    aggregationType = BatteryStatsProto.AggregationType.SINCE_UNPLUGGED
                SectionNames.INFO -> Unit
                else -> throw CheckinParseException(
                    "unsupported aggregation type $rawAggregationType",
                )
            }

            val remaining = reader.remaining
            var uid = appId(rawUid)
            if (uid == 0) {
                // Some lines provide uid 0 even though 0 is not the package's uid. Try to
                // claim the line for the package named in it (but never for "android",
                // whose uid is already known).
                val guessed = guessPackage(remaining.joinToString(","), "", packageList).packageInfo
                if (guessed != null && guessed.name.isNotEmpty() && guessed.name != "android") {
                    uid = appId(guessed.uid)
                }
            }

            var app = state.apps[uid]
            if (app == null) {
                val guessed = guessPackage(remaining.joinToString(","), "", packageList).packageInfo
                if (guessed != null && guessed.name.isNotEmpty() && guessed.name != "android") {
                    app = BatteryApp.Builder()
                    app.name = guessed.name
                    app.uid = uid
                    app.child = app.child + childFrom(guessed)
                    state.apps[uid] = app
                } else {
                    if (!knownUids.containsKey(uid)) {
                        if (isSandboxedProcess(uid)) {
                            state.warnings += "found sandboxed uid for section \"$section\""
                        } else {
                            state.warnings += "found unexpected uid $uid for section \"$section\""
                        }
                    }
                    app = BatteryApp.Builder()
                    app.uid = uid
                    state.apps[uid] = app
                }
            }

            val parsed = parseSection(
                state = state,
                reportVersion = reportVersion,
                rawUid = rawUid,
                section = section,
                record = remaining,
                app = app,
            )
            if (parsed.warning != null) {
                state.warnings += parsed.warning
            }
            if (!parsed.parsed) {
                state.warnings += "unknown data category $section"
            }
        }

        if (reportVersion == -1) {
            state.errors += "no report version found"
        } else if (reportVersion < MIN_PARSE_REPORT_VERSION) {
            state.errors += "old report version $reportVersion"
        } else if (reportVersion > MAX_UPSTREAM_REPORT_VERSION) {
            // Kept for parity with upstream. The section parsers still understand the
            // newer layouts up to MAX_SUPPORTED_REPORT_VERSION; this is informational.
            state.warnings += "newer report version $reportVersion found"
        }
        if (reportVersion > MAX_SUPPORTED_REPORT_VERSION) {
            state.warnings += "report version $reportVersion is newer than supported version " +
                "$MAX_SUPPORTED_REPORT_VERSION; some fields may be missing"
        }
    }

    /** Assembles and post-processes the final proto. */
    private fun buildStats(): BatteryStatsProto {
        processAppInfo()

        for (app in state.apps.values) {
            aggregateAppApk(app)
            postProcessApp(app)
        }

        // The system APP power item is the sum of all per-app pwi values.
        val appPower = BatterySystem.PowerUseItem.Builder()
        appPower.name = BatterySystem.PowerUseItem.Name.APP
        appPower.computed_power_mah = state.allAppComputedPowerMah
        state.system.power_use_item = state.system.power_use_item + appPower.build()

        // Screen-off time is not printed; derive it from battery realtime and screen-on time.
        state.system.misc?.let { miscMessage ->
            val misc = miscMessage.newBuilder()
            val batteryRealtime = state.system.battery?.battery_realtime_msec ?: 0f
            val difference = batteryRealtime - (misc.screen_on_time_msec ?: 0f)
            misc.screen_off_time_msec = difference
            if (difference < 0) {
                state.errors += "negative screen off time"
            }
            state.system.misc = misc.build()
        }

        postProcessSystem(state.system, reportVersion)

        val root = BatteryStatsProto.Builder()
        root.report_version = reportVersion
        root.aggregation_type = aggregationType
        root.start_time_usec =
            ((state.system.battery?.start_clock_time_msec ?: 0L) * 1000L)
        root.end_time_usec = report.timeUsec
        val build = Build.Builder()
        build.fingerprint = report.buildId
        root.build = build.build()
        root.device_group = report.deviceGroup
        root.checkin_rule = report.checkinRule
        root.sdk_version = report.sdkVersion
        root.radio = report.radio
        root.bootloader = report.bootloader
        root.country_code = report.countryCode
        root.time_zone = report.timeZone

        if (report.cellOperator.isNotEmpty()) {
            root.carrier = report.cellOperator + "/" + report.countryCode
        }
        state.resolvedUsagePackages["com.google.android.gms"]?.let {
            root.gms_version = it.version_code
        }

        root.system = state.system.build()
        root.app = state.apps.values.map { it.build() }
        return root.build()
    }

    /**
     * Chooses the display name and version data for each UID, mirroring upstream
     * `processAppInfo`. Its upstream return value is discarded, so mismatch errors are
     * intentionally not reported.
     */
    private fun processAppInfo() {
        val sharedUidMap = HashMap<Int, String>()
        for (pkg in state.packages) {
            if (pkg.sharedUserId.isEmpty()) continue
            val existing = sharedUidMap[pkg.uid]
            if (existing != null && existing != pkg.sharedUserId) {
                state.errors += "got UID ${pkg.uid} with two different shared UID labels: " +
                    "$existing and ${pkg.sharedUserId}"
                continue
            }
            sharedUidMap[pkg.uid] = pkg.sharedUserId
        }

        for ((uid, app) in state.apps) {
            var known = false
            val sharedUid = sharedUidMap[uid].orEmpty()

            val label = groupName(sharedUid)
            if (label.isNotEmpty()) {
                app.name = label
                known = true
            }

            val childNames = ArrayList<String>()
            for (child in app.child) {
                childNames += child.name.orEmpty()
                val group = packageUidGroupName(child.name.orEmpty())
                if (!known && group.isNotEmpty()) {
                    app.name = group
                    known = true
                }
            }

            app.uid = uid
            knownUids[uid]?.let {
                app.name = it
                known = true
            }

            if (app.child.size == 1) {
                val headChild = app.child[0]
                app.version_code = headChild.version_code
                app.version_name = headChild.version_name
            }

            if (!known) {
                if (sharedUid.isNotEmpty() && childNames.size != 1) {
                    app.name = "SharedUserID($sharedUid)"
                    continue
                }

                var prefix = ""
                if (childNames.isEmpty() && app.process.isNotEmpty()) {
                    // No package metadata, but process names may reveal what this UID is.
                    for (process in app.process) {
                        childNames += process.name.orEmpty()
                    }
                    prefix = "process:"
                }
                childNames.sort()
                app.name = prefix + childNames.joinToString("|")
            }
        }
    }

    /** Merges each child's APK data into the app-level `apk` field. */
    private fun aggregateAppApk(app: BatteryApp.Builder) {
        var wakeups = 0f
        val services = LinkedHashMap<String, BatteryAppService>()

        for (child in app.child) {
            val childApk = child.apk ?: continue
            wakeups += childApk.wakeups ?: 0f
            for (service in childApk.service) {
                val key = service.name.orEmpty()
                val existing = services[key]
                services[key] = if (existing == null) {
                    service
                } else {
                    val b = existing.newBuilder()
                    b.start_time_msec =
                        (b.start_time_msec ?: 0f) + (service.start_time_msec ?: 0f)
                    b.starts = (b.starts ?: 0f) + (service.starts ?: 0f)
                    b.launches = (b.launches ?: 0f) + (service.launches ?: 0f)
                    b.build()
                }
            }
        }

        if (services.isEmpty()) {
            // There were no apk lines for this UID.
            return
        }

        val apk = BatteryAppApk.Builder()
        apk.wakeups = wakeups
        apk.service = services.values.toList()
        app.apk = apk.build()
    }
}

private fun childFrom(pkg: PackageInfoModel): BatteryAppChild {
    val child = BatteryAppChild.Builder()
    child.name = pkg.name
    child.version_code = pkg.versionCode.toInt()
    child.version_name = pkg.versionName
    return child.build()
}

private fun childFrom(pkg: PackageInfo): BatteryAppChild {
    val child = BatteryAppChild.Builder()
    child.name = pkg.pkg_name
    child.version_code = pkg.version_code
    child.version_name = pkg.version_name
    return child.build()
}

/**
 * Post-processes system-level data whose old/new representations overlap.
 *
 * Mirrors `postProcessSystem`: for report version 14+ the legacy misc traffic fields are
 * filled from the dedicated global sections; controller activity is copied into the
 * legacy fields where possible.
 */
private fun postProcessSystem(system: BatterySystem.Builder, reportVersion: Int) {
    // Charge and discharge steps are printed in reverse chronological order.
    if (system.charge_step.size > 1) {
        system.charge_step = system.charge_step.reversed()
    }
    if (system.discharge_step.size > 1) {
        system.discharge_step = system.discharge_step.reversed()
    }

    fun makeGlobalWifiController() {
        val gw = system.global_wifi ?: return
        val controller = BatteryController.Builder()
        controller.idle_time_msec = (gw.wifi_idle_time_msec ?: 0f).toLong()
        controller.rx_time_msec = (gw.wifi_rx_time_msec ?: 0f).toLong()
        controller.power_mah = (gw.wifi_power_mah ?: 0f).toLong()
        val tx = BatteryTxLevel.Builder()
        tx.level = 0
        tx.time_msec = (gw.wifi_tx_time_msec ?: 0f).toLong()
        controller.tx = controller.tx + tx.build()
        system.global_wifi_controller = controller.build()
    }

    fun makeGlobalBluetooth() {
        val controller = system.global_bluetooth_controller ?: return
        val txTime = controller.tx.sumOf { it.time_msec ?: 0L }
        val gb = BatterySystem.GlobalBluetooth.Builder()
        gb.bluetooth_idle_time_msec = (controller.idle_time_msec ?: 0L).toFloat()
        gb.bluetooth_rx_time_msec = (controller.rx_time_msec ?: 0L).toFloat()
        gb.bluetooth_power_mah = (controller.power_mah ?: 0L).toFloat()
        gb.bluetooth_tx_time_msec = txTime.toFloat()
        system.global_bluetooth = gb.build()
    }

    if (reportVersion >= 17) {
        val globalWifiController = system.global_wifi_controller
        if (reportVersion >= 18 || globalWifiController != null) {
            // This report is post-controller-activity-change. Populate legacy fields where
            // possible.
            if (globalWifiController != null) {
                val globalWifi = system.global_wifi?.newBuilder()
                    ?: BatterySystem.GlobalWifi.Builder()
                globalWifi.wifi_idle_time_msec =
                    (globalWifiController.idle_time_msec ?: 0L).toFloat()
                globalWifi.wifi_rx_time_msec =
                    (globalWifiController.rx_time_msec ?: 0L).toFloat()
                globalWifi.wifi_power_mah =
                    (globalWifiController.power_mah ?: 0L).toFloat()
                val txTime = globalWifiController.tx.sumOf { it.time_msec ?: 0L }
                globalWifi.wifi_tx_time_msec = txTime.toFloat()
                system.global_wifi = globalWifi.build()
            }
            makeGlobalBluetooth()
        } else {
            makeGlobalWifiController()

            system.global_bluetooth_controller?.let { controller ->
                if (controller.tx.size == 1) {
                    // Version 17 swapped power and tx_time for the old gble layout.
                    val builder = controller.newBuilder()
                    val tx = controller.tx[0].newBuilder()
                    val power = builder.power_mah ?: 0L
                    val txTime = tx.time_msec ?: 0L
                    builder.power_mah = txTime
                    tx.time_msec = power
                    builder.tx = listOf(tx.build())
                    system.global_bluetooth_controller = builder.build()
                }
                makeGlobalBluetooth()
            }
        }
    }

    if (reportVersion < 17) {
        system.global_bluetooth?.let { gb ->
            val controller = BatteryController.Builder()
            controller.idle_time_msec = (gb.bluetooth_idle_time_msec ?: 0f).toLong()
            controller.rx_time_msec = (gb.bluetooth_rx_time_msec ?: 0f).toLong()
            controller.power_mah = (gb.bluetooth_power_mah ?: 0f).toLong()
            val tx = BatteryTxLevel.Builder()
            tx.level = 0
            tx.time_msec = (gb.bluetooth_tx_time_msec ?: 0f).toLong()
            controller.tx = controller.tx + tx.build()
            system.global_bluetooth_controller = controller.build()
        }
        makeGlobalWifiController()
    }

    system.misc?.let { miscMessage ->
        val misc = miscMessage.newBuilder()
        if (reportVersion >= 14) {
            system.global_wifi?.let { gw ->
                misc.wifi_on_time_msec = gw.wifi_on_time_msec
                misc.wifi_running_time_msec = gw.wifi_running_time_msec
            }
            system.global_network?.let { network ->
                misc.mobile_bytes_rx = network.mobile_bytes_rx
                misc.mobile_bytes_tx = network.mobile_bytes_tx
                misc.wifi_bytes_rx = network.wifi_bytes_rx
                misc.wifi_bytes_tx = network.wifi_bytes_tx
            }
        } else {
            val gw = BatterySystem.GlobalWifi.Builder()
            gw.wifi_on_time_msec = misc.wifi_on_time_msec
            gw.wifi_running_time_msec = misc.wifi_running_time_msec
            system.global_wifi = gw.build()
        }
        system.misc = misc.build()
    }
}

/** Fills the legacy app wifi fields from controller data (and vice versa). */
private fun postProcessApp(app: BatteryApp.Builder) {
    val controller = app.wifi_controller
    if (controller != null) {
        val wifi = app.wifi?.newBuilder() ?: BatteryAppWifi.Builder()
        wifi.idle_time_msec = (controller.idle_time_msec ?: 0L).toFloat()
        wifi.rx_time_msec = (controller.rx_time_msec ?: 0L).toFloat()
        val txTime = controller.tx.sumOf { it.time_msec ?: 0L }
        wifi.tx_time_msec = txTime.toFloat()
        app.wifi = wifi.build()
    } else {
        val wifi = app.wifi ?: return
        val c = BatteryController.Builder()
        c.idle_time_msec = (wifi.idle_time_msec ?: 0f).toLong()
        c.rx_time_msec = (wifi.rx_time_msec ?: 0f).toLong()
        val tx = BatteryTxLevel.Builder()
        tx.level = 0
        tx.time_msec = (wifi.tx_time_msec ?: 0f).toLong()
        c.tx = c.tx + tx.build()
        app.wifi_controller = c.build()
    }
}
