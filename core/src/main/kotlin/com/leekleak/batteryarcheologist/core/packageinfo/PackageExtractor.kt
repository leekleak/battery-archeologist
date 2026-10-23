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
 */

// Kotlin port of battery-historian/packageutils/package_extractor.go, adapted to use
// PackageInfoModel instead of the usagestats PackageInfo protobuf.

package com.leekleak.batteryarcheologist.core.packageinfo

import com.leekleak.batteryarcheologist.core.model.PackageInfoModel

/** Matches the start of a service dump, capturing the service name (`DUMP OF SERVICE appops:`). */
private val serviceDumpRegex = Regex("""^DUMP\s+OF\s+SERVICE\s+(\S+):""")

/** Matches a uid line in the app ops section (for example `Uid 1000:`). */
private val appOpsUidRegex = Regex("""^Uid\s+(\S+):""")

/** Matches a package line in the app ops section (for example `Package android:`). */
private val appOpsPackageRegex = Regex("""Package\s+(\S+):""")

/** Matches a package line in the package section (for example `Package [android] (1cce8bc):`). */
private val packageDumpPackageRegex = Regex("""Package\s+\[(\S+)]\s+\(.*""")

/** Matches a compat name line in the package section (for example `compat name=com.foo`). */
private val packageDumpCompatRegex = Regex("""compat name=(\S+)""")

/** Matches a user id line in the package section (for example `userId=1000 gids=[3003]`). */
private val packageDumpUserIdRegex = Regex("""^userId=(\d+)(\s+gids.*)?""")

/** Matches a version code line (`versionCode=94 targetSdk=19` or `versionCode=94 minSdk=19 targetSdk=19`). */
private val packageDumpVersionCodeRegex = Regex("""versionCode=(\d+)(\sminSdk=\S+)?\stargetSdk=\d+""")

/** Matches a version name line (for example `versionName=4.0.3`). */
private val packageDumpVersionNameRegex = Regex("""versionName=(\S+)""")

/** Matches a shared user line (`sharedUser=SharedUserSetting{d4e2481 android.uid.bluetooth/1002}`). */
private val packageDumpSharedUserRegex = Regex("""sharedUser=SharedUserSetting\{\S+\s+(\S+)/(\d+)\}""")

/**
 * Application information extracted from a bug report section.
 *
 * @property packages package name to [PackageInfoModel] for every package encountered. Iteration
 *   order is deterministic: package dump entries first (in dump order) for [extractAppsFromBugReport],
 *   then app ops entries that were not already present.
 * @property errors non-fatal problems found while scanning, in encounter order.
 */
data class PackageExtraction(
    val packages: Map<String, PackageInfoModel> = emptyMap(),
    val errors: List<String> = emptyList(),
)

/**
 * Looks through a full bug report and extracts as much application info as possible.
 *
 * The package service dump is preferred because it carries more data; app ops entries are only
 * added for packages the package dump did not mention.
 */
fun extractAppsFromBugReport(text: String): PackageExtraction {
    val packageDump = extractAppsFromPackageDump(text)
    val appOps = extractAppsFromAppOpsDump(text)

    val packages = LinkedHashMap<String, PackageInfoModel>(packageDump.packages)
    for ((name, pkg) in appOps.packages) {
        // Avoid listing a package twice.
        packages.putIfAbsent(name, pkg)
    }
    // Preserve upstream error ordering: app ops problems first, then package dump problems.
    return PackageExtraction(packages, appOps.errors + packageDump.errors)
}

/**
 * Extracts package names and their uids from the app ops service dump of a bug report.
 *
 * Only the `DUMP OF SERVICE appops:` section is scanned; the scan stops at the next service dump.
 */
internal fun extractAppsFromAppOpsDump(text: String): PackageExtraction {
    val packages = LinkedHashMap<String, PackageInfoModel>()
    val errors = ArrayList<String>()
    var inAppOpsSection = false
    var currentAppId = 0

    for (rawLine in text.lineSequence()) {
        val line = rawLine.trim()

        val service = serviceDumpRegex.find(line)?.groupValues?.get(1)
        if (service != null) {
            if (service == "appops") {
                inAppOpsSection = true
            } else if (inAppOpsSection) {
                // Just exited the app ops section.
                break
            }
            continue
        }
        if (!inAppOpsSection) continue

        val uidMatch = appOpsUidRegex.find(line)
        if (uidMatch != null) {
            val rawUid = uidMatch.groupValues[1]
            val parsed = appIdFromString(rawUid)
            if (parsed == null) errors += invalidAppIdError(rawUid)
            currentAppId = parsed ?: 0
        }

        val packageMatch = appOpsPackageRegex.find(line)
        if (packageMatch != null) {
            val name = packageMatch.groupValues[1]
            packages[name] = PackageInfoModel(name = name, uid = currentAppId)
        }
    }

    return PackageExtraction(packages, errors)
}

/**
 * Extracts as much application info as possible from the package service dump of a bug report.
 *
 * Only the `Packages:` list is read; the scan stops at `Hidden system packages:` (those packages
 * are older versions of already listed packages) or at the next service dump.
 */
internal fun extractAppsFromPackageDump(text: String): PackageExtraction {
    val packages = LinkedHashMap<String, PackageInfoModel>()
    val errors = ArrayList<String>()
    var inPackageDumpSection = false
    var inPackagesList = false
    var current: PackageInfoModel? = null

    fun flushCurrent() {
        val pkg = current ?: return
        packages[pkg.name] = pkg
        current = null
    }

    for (rawLine in text.lineSequence()) {
        val line = rawLine.trim()

        val service = serviceDumpRegex.find(line)?.groupValues?.get(1)
        if (service != null) {
            if (service == "package") {
                inPackageDumpSection = true
            } else if (inPackageDumpSection) {
                // Just exited the package dump section.
                break
            }
            continue
        }
        if (!inPackageDumpSection) continue

        if (line == "Packages:") {
            inPackagesList = true
            continue
        }
        if (line == "Hidden system packages:") break
        if (!inPackagesList) continue

        val packageMatch = packageDumpPackageRegex.find(line)
        if (packageMatch != null) {
            flushCurrent()
            current = PackageInfoModel(name = packageMatch.groupValues[1])
            continue
        }

        val compatMatch = packageDumpCompatRegex.find(line)
        if (compatMatch != null) {
            val pkg = current
            if (pkg == null) {
                errors += "found compat line before package line"
                continue
            }
            // A renamed package is reported under its new name.
            current = pkg.copy(name = compatMatch.groupValues[1])
            continue
        }

        val userIdMatch = packageDumpUserIdRegex.find(line)
        if (userIdMatch != null) {
            val pkg = current
            if (pkg == null) {
                errors += "found userId line before package line"
                continue
            }
            val rawUid = userIdMatch.groupValues[1]
            val parsed = appIdFromString(rawUid)
            if (parsed == null) errors += invalidAppIdError(rawUid)
            current = pkg.copy(uid = parsed ?: 0)
            continue
        }

        val versionCodeMatch = packageDumpVersionCodeRegex.find(line)
        if (versionCodeMatch != null) {
            val pkg = current
            if (pkg == null) {
                errors += "found versionCode line before package line"
                continue
            }
            val rawVersionCode = versionCodeMatch.groupValues[1]
            val versionCode = rawVersionCode.toLongOrNull()
            if (versionCode == null) {
                errors += "error getting version code from string: '$rawVersionCode' is not a valid integer"
            } else {
                current = pkg.copy(versionCode = versionCode)
            }
            continue
        }

        val versionNameMatch = packageDumpVersionNameRegex.find(line)
        if (versionNameMatch != null) {
            val pkg = current
            if (pkg == null) {
                errors += "found versionName line before package line"
                continue
            }
            current = pkg.copy(versionName = versionNameMatch.groupValues[1])
            continue
        }

        // firstInstallTime and lastUpdateTime are recognised by the upstream extractor, but
        // PackageInfoModel has no fields for them yet, so those lines are ignored here.

        val sharedUserMatch = packageDumpSharedUserRegex.find(line)
        if (sharedUserMatch != null) {
            val pkg = current
            if (pkg == null) {
                errors += "found sharedUser line before package line"
                continue
            }
            val label = sharedUserMatch.groupValues[1]
            val parsedUid = appIdFromString(sharedUserMatch.groupValues[2])
            if (parsedUid == null) errors += invalidAppIdError(sharedUserMatch.groupValues[2])
            if (pkg.uid != (parsedUid ?: 0)) {
                errors += "sharedUser uid is different from package uid"
                continue
            }
            current = pkg.copy(sharedUserId = label)
        }
    }

    flushCurrent()
    return PackageExtraction(packages, errors)
}
