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
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats as BatteryStatsProto
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.App as BatteryApp
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.App.Apk as BatteryAppApk
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.App.Apk.Service as BatteryAppService
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.App.Child as BatteryAppChild
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.App.Wifi as BatteryAppWifi
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.ControllerActivity as BatteryController
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.ControllerActivity.TxLevel as BatteryTxLevel
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.System as BatterySystem
import com.leekleak.batteryarcheologist.proto.build.BuildOuterClass
import com.leekleak.batteryarcheologist.proto.usagestats.AndroidPackageInfo

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
        val protos = LinkedHashMap<String, AndroidPackageInfo.PackageInfo>()
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
                    protos[name] = AndroidPackageInfo.PackageInfo.newBuilder()
                        .setPkgName(name)
                        .setUid(uid)
                        .build()
                    models[name] = PackageInfoModel(name = name, uid = uid)
                } else if (appId(existing.uid) == 0) {
                    // Some uploaded package entries have a uid of 0; prefer the checkin uid.
                    protos[name] = existing.toBuilder().setUid(uid).build()
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
            val uid = appId(pkg.uid)
            if (uid == 0) {
                // Packages uploaded with an unknown uid cannot be attributed.
                continue
            }
            val app = state.apps.getOrPut(uid) { BatteryApp.newBuilder().setUid(uid) }
            app.addChild(childFrom(pkg))
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
                    app = BatteryApp.newBuilder()
                        .setName(guessed.name)
                        .setUid(uid)
                    app.addChild(childFrom(guessed))
                    state.apps[uid] = app
                } else {
                    if (!knownUids.containsKey(uid)) {
                        if (isSandboxedProcess(uid)) {
                            state.warnings += "found sandboxed uid for section \"$section\""
                        } else {
                            state.warnings += "found unexpected uid $uid for section \"$section\""
                        }
                    }
                    app = BatteryApp.newBuilder().setUid(uid)
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
    private fun buildStats(): Batterystats.BatteryStats {
        processAppInfo()

        for (app in state.apps.values) {
            aggregateAppApk(app)
            postProcessApp(app)
        }

        // The system APP power item is the sum of all per-app pwi values.
        state.system.addPowerUseItem(
            BatterySystem.PowerUseItem.newBuilder()
                .setName(BatterySystem.PowerUseItem.Name.APP)
                .setComputedPowerMah(state.allAppComputedPowerMah),
        )

        // Screen-off time is not printed; derive it from battery realtime and screen-on time.
        if (state.system.hasMisc()) {
            val misc = state.system.misc.toBuilder()
            val batteryRealtime = if (state.system.hasBattery()) {
                state.system.battery.batteryRealtimeMsec
            } else {
                0f
            }
            val difference = batteryRealtime - misc.screenOnTimeMsec
            misc.setScreenOffTimeMsec(difference)
            if (difference < 0) {
                state.errors += "negative screen off time"
            }
            state.system.setMisc(misc)
        }

        postProcessSystem(state.system, reportVersion)

        val root = BatteryStatsProto.newBuilder()
            .setReportVersion(reportVersion)
            .setAggregationType(aggregationType)
            .setStartTimeUsec(
                (if (state.system.hasBattery()) state.system.battery.startClockTimeMsec else 0L) * 1000L,
            )
            .setEndTimeUsec(report.timeUsec)
            .setBuild(BuildOuterClass.Build.newBuilder().setFingerprint(report.buildId))
            .addAllDeviceGroup(report.deviceGroup)
            .addAllCheckinRule(report.checkinRule)
            .setSdkVersion(report.sdkVersion)
            .setRadio(report.radio)
            .setBootloader(report.bootloader)
            .setCountryCode(report.countryCode)
            .setTimeZone(report.timeZone)

        if (report.cellOperator.isNotEmpty()) {
            root.setCarrier(report.cellOperator + "/" + report.countryCode)
        }
        state.resolvedUsagePackages["com.google.android.gms"]?.let {
            root.setGmsVersion(it.versionCode)
        }

        root.setSystem(state.system)
        for (app in state.apps.values) {
            root.addApp(app)
        }
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
                app.setName(label)
                known = true
            }

            val childNames = ArrayList<String>()
            for (child in app.childList) {
                childNames += child.name
                val group = packageUidGroupName(child.name)
                if (!known && group.isNotEmpty()) {
                    app.setName(group)
                    known = true
                }
            }

            app.setUid(uid)
            knownUids[uid]?.let {
                app.setName(it)
                known = true
            }

            if (app.childCount == 1) {
                val headChild = app.getChild(0)
                app.setVersionCode(headChild.versionCode)
                app.setVersionName(headChild.versionName)
            }

            if (!known) {
                if (sharedUid.isNotEmpty() && childNames.size != 1) {
                    app.setName("SharedUserID($sharedUid)")
                    continue
                }

                var prefix = ""
                if (childNames.isEmpty() && app.processCount > 0) {
                    // No package metadata, but process names may reveal what this UID is.
                    for (process in app.processList) {
                        childNames += process.name
                    }
                    prefix = "process:"
                }
                childNames.sort()
                app.setName(prefix + childNames.joinToString("|"))
            }
        }
    }

    /** Merges each child's APK data into the app-level `apk` field. */
    private fun aggregateAppApk(app: BatteryApp.Builder) {
        var wakeups = 0f
        val services = LinkedHashMap<String, BatteryAppService>()

        for (child in app.childList) {
            if (!child.hasApk()) continue
            val apk = child.apk
            wakeups += apk.wakeups
            for (service in apk.serviceList) {
                val existing = services[service.name]
                services[service.name] = if (existing == null) {
                    service
                } else {
                    existing.toBuilder()
                        .setStartTimeMsec(existing.startTimeMsec + service.startTimeMsec)
                        .setStarts(existing.starts + service.starts)
                        .setLaunches(existing.launches + service.launches)
                        .build()
                }
            }
        }

        if (services.isEmpty()) {
            // There were no apk lines for this UID.
            return
        }

        val apk = BatteryAppApk.newBuilder().setWakeups(wakeups)
        for (service in services.values) {
            apk.addService(service)
        }
        app.setApk(apk)
    }
}

private fun childFrom(pkg: PackageInfoModel): BatteryAppChild.Builder =
    BatteryAppChild.newBuilder()
        .setName(pkg.name)
        .setVersionCode(pkg.versionCode.toInt())
        .setVersionName(pkg.versionName)

private fun childFrom(pkg: AndroidPackageInfo.PackageInfo): BatteryAppChild.Builder =
    BatteryAppChild.newBuilder()
        .setName(pkg.pkgName)
        .setVersionCode(pkg.versionCode)
        .setVersionName(pkg.versionName)

/**
 * Post-processes system-level data whose old/new representations overlap.
 *
 * Mirrors `postProcessSystem`: for report version 14+ the legacy misc traffic fields are
 * filled from the dedicated global sections; controller activity is copied into the
 * legacy fields where possible.
 */
private fun postProcessSystem(system: BatterySystem.Builder, reportVersion: Int) {
    // Charge and discharge steps are printed in reverse chronological order.
    if (system.chargeStepCount > 1) {
        val reversed = system.chargeStepList.reversed()
        system.clearChargeStep()
        system.addAllChargeStep(reversed)
    }
    if (system.dischargeStepCount > 1) {
        val reversed = system.dischargeStepList.reversed()
        system.clearDischargeStep()
        system.addAllDischargeStep(reversed)
    }

    fun makeGlobalWifiController() {
        if (!system.hasGlobalWifi()) return
        val gw = system.globalWifi
        system.setGlobalWifiController(
            BatteryController.newBuilder()
                .setIdleTimeMsec(gw.wifiIdleTimeMsec.toLong())
                .setRxTimeMsec(gw.wifiRxTimeMsec.toLong())
                .setPowerMah(gw.wifiPowerMah.toLong())
                .addTx(
                    BatteryTxLevel.newBuilder()
                        .setLevel(0)
                        .setTimeMsec(gw.wifiTxTimeMsec.toLong()),
                ),
        )
    }

    fun makeGlobalBluetooth() {
        if (!system.hasGlobalBluetoothController()) return
        val controller = system.globalBluetoothController
        var txTime = 0L
        for (tx in controller.txList) txTime += tx.timeMsec
        system.setGlobalBluetooth(
            BatterySystem.GlobalBluetooth.newBuilder()
                .setBluetoothIdleTimeMsec(controller.idleTimeMsec.toFloat())
                .setBluetoothRxTimeMsec(controller.rxTimeMsec.toFloat())
                .setBluetoothPowerMah(controller.powerMah.toFloat())
                .setBluetoothTxTimeMsec(txTime.toFloat()),
        )
    }

    if (reportVersion >= 17) {
        val globalWifiController =
            if (system.hasGlobalWifiController()) system.globalWifiController else null
        if (reportVersion >= 18 || globalWifiController != null) {
            // This report is post-controller-activity-change. Populate legacy fields where
            // possible.
            if (globalWifiController != null) {
                val globalWifi = if (system.hasGlobalWifi()) {
                    system.globalWifi.toBuilder()
                } else {
                    BatterySystem.GlobalWifi.newBuilder()
                }
                globalWifi.setWifiIdleTimeMsec(globalWifiController.idleTimeMsec.toFloat())
                globalWifi.setWifiRxTimeMsec(globalWifiController.rxTimeMsec.toFloat())
                globalWifi.setWifiPowerMah(globalWifiController.powerMah.toFloat())
                var txTime = 0L
                for (tx in globalWifiController.txList) txTime += tx.timeMsec
                globalWifi.setWifiTxTimeMsec(txTime.toFloat())
                system.setGlobalWifi(globalWifi)
            }
            makeGlobalBluetooth()
        } else {
            makeGlobalWifiController()

            if (system.hasGlobalBluetoothController()) {
                val controller = system.globalBluetoothController
                if (controller.txCount == 1) {
                    // Version 17 swapped power and tx_time for the old gble layout.
                    val builder = controller.toBuilder()
                    val tx = builder.getTx(0).toBuilder()
                    val power = builder.powerMah
                    val txTime = tx.timeMsec
                    builder.setPowerMah(txTime)
                    tx.setTimeMsec(power)
                    builder.setTx(0, tx)
                    system.setGlobalBluetoothController(builder)
                }
                makeGlobalBluetooth()
            }
        }
    }

    if (reportVersion < 17) {
        if (system.hasGlobalBluetooth()) {
            val gb = system.globalBluetooth
            system.setGlobalBluetoothController(
                BatteryController.newBuilder()
                    .setIdleTimeMsec(gb.bluetoothIdleTimeMsec.toLong())
                    .setRxTimeMsec(gb.bluetoothRxTimeMsec.toLong())
                    .setPowerMah(gb.bluetoothPowerMah.toLong())
                    .addTx(
                        BatteryTxLevel.newBuilder()
                            .setLevel(0)
                            .setTimeMsec(gb.bluetoothTxTimeMsec.toLong()),
                    ),
            )
        }
        makeGlobalWifiController()
    }

    if (system.hasMisc()) {
        val misc = system.misc.toBuilder()
        if (reportVersion >= 14) {
            if (system.hasGlobalWifi()) {
                misc.setWifiOnTimeMsec(system.globalWifi.wifiOnTimeMsec)
                misc.setWifiRunningTimeMsec(system.globalWifi.wifiRunningTimeMsec)
            }
            if (system.hasGlobalNetwork()) {
                val network = system.globalNetwork
                misc.setMobileBytesRx(network.mobileBytesRx)
                misc.setMobileBytesTx(network.mobileBytesTx)
                misc.setWifiBytesRx(network.wifiBytesRx)
                misc.setWifiBytesTx(network.wifiBytesTx)
            }
        } else {
            system.setGlobalWifi(
                BatterySystem.GlobalWifi.newBuilder()
                    .setWifiOnTimeMsec(misc.wifiOnTimeMsec)
                    .setWifiRunningTimeMsec(misc.wifiRunningTimeMsec),
            )
        }
        system.setMisc(misc)
    }
}

/** Fills the legacy app wifi fields from controller data (and vice versa). */
private fun postProcessApp(app: BatteryApp.Builder) {
    if (app.hasWifiController()) {
        val controller = app.wifiController
        val wifi = if (app.hasWifi()) app.wifi.toBuilder() else BatteryAppWifi.newBuilder()
        wifi.setIdleTimeMsec(controller.idleTimeMsec.toFloat())
        wifi.setRxTimeMsec(controller.rxTimeMsec.toFloat())
        var txTime = 0L
        for (tx in controller.txList) txTime += tx.timeMsec
        wifi.setTxTimeMsec(txTime.toFloat())
        app.setWifi(wifi)
    } else if (app.hasWifi()) {
        val wifi = app.wifi
        app.setWifiController(
            BatteryController.newBuilder()
                .setIdleTimeMsec(wifi.idleTimeMsec.toLong())
                .setRxTimeMsec(wifi.rxTimeMsec.toLong())
                .addTx(
                    BatteryTxLevel.newBuilder()
                        .setLevel(0)
                        .setTimeMsec(wifi.txTimeMsec.toLong()),
                ),
        )
    }
}
