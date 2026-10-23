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

// Mutable state shared by the checkin parse loop and the section parsers.

package com.leekleak.batteryarcheologist.core.checkin.checkinparse

import com.leekleak.batteryarcheologist.core.checkin.checkinutil.BatteryReport
import com.leekleak.batteryarcheologist.core.checkin.checkinutil.IntCounter
import com.leekleak.batteryarcheologist.core.model.PackageInfoModel
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.App as BatteryApp
import com.leekleak.batteryarcheologist.proto.batterystats.Batterystats.BatteryStats.System as BatterySystem
import com.leekleak.batteryarcheologist.proto.usagestats.AndroidPackageInfo
import java.util.TreeMap

/** Identifies an APK line independently of multi-user UID remapping. */
internal data class ApkId(val rawUid: Int, val name: String)

/** Outcome of parsing one checkin section. */
internal data class SectionParseResult(
    val parsed: Boolean,
    val warning: String? = null,
)

/** State accumulated while reading a single checkin capture. */
internal class ParserState(
    val report: BatteryReport,
    val packages: List<PackageInfoModel>,
    val counter: IntCounter = IntCounter(),
) {
    val warnings = ArrayList<String>()
    val errors = ArrayList<String>()

    /** UID -> app proto builder. A TreeMap keeps output deterministic. */
    val apps = TreeMap<Int, BatteryApp.Builder>()

    /** System-level stats; attached to the root proto once parsing finishes. */
    val system: BatterySystem.Builder = BatterySystem.newBuilder()

    /** APK wakeup counts are only counted once per raw UID/package pair. */
    val apkSeen = HashSet<ApkId>()

    /** Sum of all per-app `pwi` values; emitted as the system APP power item. */
    var allAppComputedPowerMah: Float = 0f

    /** Package/UID map after merging the checkin `uid` rows and caller-supplied packages. */
    var resolvedPackages: Map<String, PackageInfoModel> = emptyMap()

    /** Same data as [resolvedPackages], in the generated usagestats proto shape. */
    var resolvedUsagePackages: Map<String, AndroidPackageInfo.PackageInfo> = emptyMap()
}
