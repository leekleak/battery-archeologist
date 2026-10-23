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
 * Kotlin port of `UIDAndPackageNameMapping`, `PackageUIDMapping`, and the
 * UID-group helpers from third_party/battery-historian/parseutils/parseutils.go
 * and checkinparse/checkin_parse.go. The checkin package is not a dependency
 * of this port, so the small lookup tables it exposes are copied here.
 */

package com.leekleak.batteryarcheologist.core.history.parseutils

import com.leekleak.batteryarcheologist.core.historianutils.HistorianUtils
import com.leekleak.batteryarcheologist.core.model.PackageInfoModel
import com.leekleak.batteryarcheologist.core.packageinfo.appId
import com.leekleak.batteryarcheologist.core.packageinfo.appIdFromString
import com.leekleak.batteryarcheologist.core.packageinfo.guessPackage

/**
 * UID groups that have a well-known label. Port of `checkinparse.sharedUIDLabelMap`.
 */
internal val sharedUidLabelMap: Map<String, String> = mapOf(
    "android.media" to "MEDIA",
    "android.uid.bluetooth" to "BLUETOOTH",
    "android.uid.nfc" to "NFC",
    "android.uid.phone" to "RADIO", // associated with uid 1001
    "android.uid.shared" to "CONTACTS_PROVIDER",
    "android.uid.shell" to "SHELL",
    "android.uid.system" to "ANDROID_SYSTEM",
    "android.uid.systemui" to "SYSTEM_UI",
    "com.google.android.calendar.uid.shared" to "GOOGLE_CALENDAR",
    "com.google.uid.shared" to "GOOGLE_SERVICES",
)

/**
 * Packages known to live in a shared UID group.
 * Port of `checkinparse.packageNameToSharedUIDMap`.
 */
internal val packageToSharedUidName: Map<String, String> = mapOf(
    // com.google.uid.shared
    "com.google.android.apps.gtalkservice" to "GOOGLE_SERVICES",
    "com.google.android.backuptransport" to "GOOGLE_SERVICES",
    "com.google.android.gms" to "GOOGLE_SERVICES",
    "com.google.android.gms.car.userfeedback" to "GOOGLE_SERVICES",
    "com.google.android.googleapps" to "GOOGLE_SERVICES",
    "com.google.android.gsf" to "GOOGLE_SERVICES",
    "com.google.android.gsf.login" to "GOOGLE_SERVICES",
    "com.google.android.gsf.notouch" to "GOOGLE_SERVICES",
    "com.google.android.providers.gmail" to "GOOGLE_SERVICES",
    "com.google.android.sss.authbridge" to "GOOGLE_SERVICES",
    "com.google.gch.gateway" to "GOOGLE_SERVICES",

    // com.google.android.calendar.uid.shared
    "com.android.calendar" to "GOOGLE_CALENDAR",
    "com.google.android.calendar" to "GOOGLE_CALENDAR",
    "com.google.android.syncadapters.calendar" to "GOOGLE_CALENDAR",

    // android.uid.system
    "android" to "ANDROID_SYSTEM",
    "com.android.changesettings" to "ANDROID_SYSTEM",
    "com.android.inputdevices" to "ANDROID_SYSTEM",
    "com.android.keychain" to "ANDROID_SYSTEM",
    "com.android.location.fused" to "ANDROID_SYSTEM",
    "com.android.providers.settings" to "ANDROID_SYSTEM",
    "com.android.settings" to "ANDROID_SYSTEM",
    "com.google.android.canvas.settings" to "ANDROID_SYSTEM",
    "com.lge.SprintHiddenMenu" to "ANDROID_SYSTEM",
    "com.nvidia.tegraprofiler.security" to "ANDROID_SYSTEM",
    "com.qualcomm.atfwd" to "ANDROID_SYSTEM",
    "com.qualcomm.display" to "ANDROID_SYSTEM",

    // android.uid.phone, associated with uid 1001
    "com.android.phone" to "RADIO",
    "com.android.providers.telephony" to "RADIO",
    "com.android.sdm.plugins.connmo" to "RADIO",
    "com.android.sdm.plugins.dcmo" to "RADIO",
    "com.android.sdm.plugins.sprintdm" to "RADIO",
    "com.htc.android.qxdm2sd" to "RADIO",
    "com.android.mms.service" to "RADIO",
    "com.android.server.telecom" to "RADIO",
    "com.android.sprint.lifetimedata" to "RADIO",
    "com.android.stk" to "RADIO",
    "com.motorola.service.ims" to "RADIO",
    "com.qualcomm.qti.imstestrunner" to "RADIO",
    "com.qualcomm.qti.rcsbootstraputil" to "RADIO",
    "com.qualcomm.qti.rcsimsbootstraputil" to "RADIO",
    "com.qualcomm.qcrilmsgtunnel" to "RADIO",
    "com.qualcomm.shutdownlistner" to "RADIO",
    "org.codeaurora.ims" to "RADIO",
    "com.asus.atcmd" to "RADIO",
    "com.mediatek.imeiwriter" to "RADIO",

    // android.uid.nfc
    "com.android.nfc" to "NFC",
    "com.android.nfc3" to "NFC",
    "com.google.android.uiccwatchdog" to "NFC",

    // android.uid.shared
    "com.android.contacts" to "CONTACTS_PROVIDER",
    "com.android.providers.applications" to "CONTACTS_PROVIDER",
    "com.android.providers.contacts" to "CONTACTS_PROVIDER",
    "com.android.providers.userdictionary" to "CONTACTS_PROVIDER",

    // android.media
    "com.android.gallery" to "MEDIA",
    "com.android.providers.downloads" to "MEDIA",
    "com.android.providers.downloads.ui" to "MEDIA",
    "com.android.providers.drm" to "MEDIA",
    "com.android.providers.media" to "MEDIA",

    // android.uid.systemui
    "com.android.keyguard" to "SYSTEM_UI",
    "com.android.systemui" to "SYSTEM_UI",

    // android.uid.bluetooth
    "com.android.bluetooth" to "BLUETOOTH",

    // android.uid.shell
    "com.android.shell" to "SHELL",
)

/**
 * Constant UIDs from `system/core/include/private/android_filesystem_config.h`.
 * Port of `checkinparse.KnownUIDs` (GIDs excluded).
 */
internal val knownUids: Map<Int, String> = mapOf(
    0 to "ROOT",
    1000 to "ANDROID_SYSTEM",
    1001 to "RADIO",
    1002 to "BLUETOOTH",
    1003 to "GRAPHICS",
    1004 to "INPUT",
    1005 to "AUDIO",
    1006 to "CAMERA",
    1007 to "LOG",
    1008 to "COMPASS",
    1009 to "MOUNT",
    1010 to "WIFI",
    1011 to "ADB",
    1012 to "INSTALL",
    1013 to "MEDIA",
    1014 to "DHCP",
    1015 to "SDCARD_RW",
    1016 to "VPN",
    1017 to "KEYSTORE",
    1018 to "USB",
    1019 to "DRM",
    1020 to "MDNSR",
    1021 to "GPS",
    1023 to "MEDIA_RW",
    1024 to "MTP",
    1026 to "DRMRPC",
    1027 to "NFC",
    1028 to "SDCARD_R",
    1029 to "CLAT",
    1030 to "LOOP_RADIO",
    1031 to "MEDIA_DRM",
    1032 to "PACKAGE_INFO",
    1033 to "SDCARD_PICS",
    1034 to "SDCARD_AV",
    1035 to "SDCARD_ALL",
    1036 to "LOGD",
    1037 to "SHARED_RELRO",
    1038 to "DBUS",
    1039 to "TLSDATE",
    1040 to "MEDIA_EX",
    1041 to "AUDIOSERVER",
    1042 to "METRICS_COLL",
    1043 to "METRICSD",
    1044 to "WEBSERV",
    1045 to "DEBUGGERD",
    1046 to "MEDIA_CODEC",
    1047 to "CAMERASERVER",
    1048 to "FIREWALL",
    1049 to "TRUNKS",
    1050 to "NVRAM",
    1051 to "DNS",
    1052 to "DNS_TETHER",
    1053 to "WEBVIEW_ZYGOTE",
    1054 to "VEHICLE_NETWORK",
    1055 to "MEDIA_AUDIO",
    1056 to "MEDIA_VIDEO",
    1057 to "MEDIA_IMAGE",
    1058 to "TOMBSTONED",
    1059 to "MEDIA_OBB",
    1060 to "ESE",
    1061 to "OTA_UPDATE",
    2000 to "SHELL",
    2001 to "CACHE",
    2002 to "DIAG",
    3001 to "NET_BT_ADMIN",
    3002 to "NET_BT",
    3003 to "INET",
    3004 to "NET_RAW",
    3005 to "NET_ADMIN",
    3006 to "NET_BW_STATS",
    3007 to "NET_BW_ACCT",
    3008 to "NET_BT_STACK",
    3009 to "READPROC",
    3010 to "WAKELOCK",
    9997 to "EVERYBODY",
    9998 to "MISC",
    9999 to "NOBODY",
)

/** Port of `checkinparse.GroupName`. Returns `null` for unknown labels. */
internal fun groupName(sharedUidLabel: String): String? = sharedUidLabelMap[sharedUidLabel]

/** Port of `checkinparse.PackageUIDGroupName`. Returns `null` for unknown packages. */
internal fun packageUidGroupName(pkg: String): String? = packageToSharedUidName[pkg]

/** Port of `checkinparse.KnownUIDs` lookup. Returns `null` for unknown UIDs. */
internal fun knownUidName(uid: Int): String? = knownUids[uid]

/**
 * Mapping between package names and UIDs.
 *
 * Port of Go `PackageUIDMapping`. Immutable from the outside; the parser
 * creates it once per report via [UIDAndPackageNameMapping].
 *
 * @property uidToPackage maps full UIDs to package names. Shared UIDs combine
 *   package names with `;`.
 * @property packageToUID maps a package name to its app id (the UID with the
 *   user id removed).
 * @property sharedUIDName maps a full UID to its predefined, or synthesized,
 *   shared-UID label. Property name kept in Go spelling intentionally.
 * @property pkgList packages parsed from a bug report, used for fuzzy matches.
 */
data class PackageUIDMapping(
    val uidToPackage: Map<Int, String> = emptyMap(),
    val packageToUID: Map<String, Int> = emptyMap(),
    val sharedUIDName: Map<Int, String> = emptyMap(),
    val pkgList: List<PackageInfoModel> = emptyList(),
) {
    /**
     * Finds the best [PackageInfoModel] for [suid], mutating its `pkg` field.
     *
     * The UID is checked against well-known UIDs and shared UID groups first,
     * then the identifier is matched against the package list, and finally the
     * checkin `apk` mapping is used as a fallback.
     *
     * @return an error message when the uid string cannot be parsed, or `null`.
     */
    fun matchServiceWithPackageInfo(suid: ServiceUID): String? {
        val uid = appIdFromString(suid.uid)
            ?: return "error getting appID from string: '${suid.uid}' is not a valid uid"

        if (uid != 0) {
            // Some valid history entries are logged with uid 0, so it is
            // deliberately excluded from this first check.
            knownUidName(uid)?.let { name ->
                suid.pkg = PackageInfoModel(name = name, uid = uid)
                return null
            }
            sharedUIDName[uid]?.let { name ->
                suid.pkg = PackageInfoModel(name = name, uid = uid)
                return null
            }
        }

        val guess = guessPackage(suid.service, suid.uid, pkgList)
        guess.error?.let { return it }
        val pkg = guess.packageInfo
        if (pkg != null) {
            groupName(pkg.sharedUserId)?.let { name ->
                suid.pkg = PackageInfoModel(name = name, uid = pkg.uid)
                return null
            }
            packageUidGroupName(pkg.name)?.let { name ->
                suid.pkg = PackageInfoModel(name = name, uid = pkg.uid)
                return null
            }
        }

        // Hold this check until after GuessPackage in case it finds something better.
        val combined = uidToPackage[uid]
        if (uid != 0 && combined != null && combined.contains(';')) {
            suid.pkg = PackageInfoModel(name = combined, uid = uid)
            return null
        }

        // Many applications incorrectly match with "android". If the service
        // name did not produce a useful match, fall back to the package name
        // recorded in the checkin log. This order matters for shared UIDs,
        // where the service string is usually more specific.
        var resolved = pkg
        if (resolved == null || resolved.name == "android") {
            val fromUid = uidToPackage[uid]
            if (fromUid != null) {
                val secondGuess = guessPackage(fromUid, suid.uid, pkgList)
                secondGuess.error?.let { return it }
                resolved = secondGuess.packageInfo
                    ?: PackageInfoModel(name = fromUid, uid = uid)
            } else {
                val service = suid.service.trim('"')
                val mappedUid = packageToUID[service]
                if (mappedUid != null) {
                    val secondGuess = guessPackage(service, mappedUid.toString(), pkgList)
                    secondGuess.error?.let { return it }
                    resolved = secondGuess.packageInfo
                        ?: PackageInfoModel(name = service, uid = mappedUid)
                }
            }
        }
        suid.pkg = resolved
        return null
    }

    /** Returns the best package name for [uid]. Port of Go `packageName`. */
    fun packageName(uid: Int): String =
        knownUidName(uid)
            ?: sharedUIDName[uid]
            ?: uidToPackage[uid]
            ?: ""
}

/**
 * Builds the UID/package mappings for a checkin capture.
 *
 * Port of Go `UIDAndPackageNameMapping`. [packages] augments the `apk` lines
 * because a checkin log does not list every installed app.
 */
fun UIDAndPackageNameMapping(checkin: String, packages: List<PackageInfoModel>): MappingResult {
    val uidToPackage = LinkedHashMap<Int, String>()
    val packageToUid = LinkedHashMap<String, Int>()
    val sharedUidName = LinkedHashMap<Int, String>()
    val errors = ArrayList<String>()

    for (rawLine in checkin.split('\n')) {
        val line = rawLine.trim()
        val match = HistoryRegex.checkinApkLine.find(line) ?: continue
        val uidText = match.groups["uid"]?.value ?: continue
        val uid = uidText.toIntOrNull()
        if (uid == null) {
            errors.add("invalid UID in checkin 'apk' line: \"$uidText\"")
            continue
        }
        val apk = match.groups["pkgName"]?.value ?: continue
        val existing = uidToPackage[uid]
        if (existing != null) {
            if (!existing.contains(apk)) {
                uidToPackage[uid] = "$existing;$apk"
            }
        } else {
            uidToPackage[uid] = apk
        }
        val appId = appId(uid)
        val previous = packageToUid[apk]
        if (previous != null && previous != appId) {
            errors.add("duplicate package names found with different UIDs: $previous and $appId")
        } else {
            packageToUid[apk] = appId
        }
    }

    for (pkg in packages) {
        if (pkg.name.isEmpty()) continue
        val previousUid = packageToUid[pkg.name]
        if (previousUid == null) {
            packageToUid[pkg.name] = pkg.uid
        } else if (previousUid != pkg.uid) {
            errors.add(
                "mismatched UIDs between checkin log and package list: " +
                    "$previousUid and ${pkg.uid}",
            )
        }

        val existing = uidToPackage[pkg.uid]
        if (existing != null) {
            if (!existing.contains(pkg.name)) {
                uidToPackage[pkg.uid] = "$existing;${pkg.name}"
            }
        } else {
            uidToPackage[pkg.uid] = pkg.name
        }

        val sharedUserId = pkg.sharedUserId
        if (pkg.uid != 0 && sharedUserId.isNotEmpty()) {
            val name = groupName(sharedUserId) ?: "SharedUserID($sharedUserId)"
            val previousName = sharedUidName[pkg.uid]
            if (previousName != null && previousName != name) {
                errors.add(
                    "uid ${pkg.uid} had different shared user IDs: " +
                        "\"$previousName\" vs \"$name\"",
                )
                continue
            }
            sharedUidName[pkg.uid] = name
        }
    }

    return MappingResult(
        mapping = PackageUIDMapping(
            uidToPackage = uidToPackage,
            packageToUID = packageToUid,
            sharedUIDName = sharedUidName,
            pkgList = packages,
        ),
        errors = errors,
    )
}
