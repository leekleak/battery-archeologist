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

// Kotlin port of battery-historian/packageutils/packageutils.go, adapted to use
// PackageInfoModel instead of the usagestats PackageInfo protobuf.

package com.leekleak.batteryarcheologist.core.packageinfo

import com.leekleak.batteryarcheologist.core.model.PackageInfoModel

/** Number of uids allocated to each user (`UserHandle.PER_USER_RANGE`). */
const val PER_USER_RANGE = 100_000

/**
 * First uid (and gid) reserved for applications (`Process.FIRST_APPLICATION_UID`).
 *
 * Application uids run from this value up to 19999.
 */
const val FIRST_APPLICATION_UID = 10_000

/** First uid used for fully isolated sandboxed processes (`Process.FIRST_ISOLATED_UID`). */
const val FIRST_ISOLATED_UID = 99_000

/** Last uid used for fully isolated sandboxed processes (`Process.LAST_ISOLATED_UID`). */
const val LAST_ISOLATED_UID = 99_999

// Gids for applications sharing resources (`Process.FIRST_SHARED_APPLICATION_GID`).
private const val FIRST_SHARED_APPLICATION_GID = 50_000
private const val LAST_SHARED_APPLICATION_GID = 59_999

/**
 * Abbreviated uid such as `u0a2`, as printed by `UserHandle.formatUid`.
 *
 * The leading user id is ignored when computing the app id; group 1 is the user id,
 * group 2 the uid type (`i` = isolated, `a` = application, `s` = unmodified) and
 * group 3 the app id digits.
 */
private val abbreviatedUidRegex = Regex("""u(\d+)([ias])(\d+)""")

/**
 * The error message produced for uid strings that cannot be parsed.
 *
 * Kept identical in shape to the upstream Go message so callers that surface parsing
 * problems in a bug report show the same wording.
 */
internal fun invalidAppIdError(uid: String): String =
    "error getting appID from string: '$uid' is not a valid uid"

/**
 * This list is not comprehensive but covers the most common cases. It was curated from the
 * output of `adb shell dumpsys activity providers` and `adb shell dumpsys content` on several
 * devices. Some reported names can map to different applications; the process uid disambiguates
 * those cases before this table is consulted.
 */
private val syncAdapterToPackageName: Map<String, String> = mapOf(
    // com.android.calendar and com.android.contacts can be used by many applications and are thus
    // only differentiable by the process uid. If it gets to this point, the default packages
    // listed here appear to be installed on all devices.
    "com.android.calendar" to "com.android.providers.calendar",
    "com.android.contacts" to "com.android.providers.contacts",
    "com.android.gmail.ui" to "com.google.android.gm",
    "com.android.inputmethod.japanese" to "com.google.android.inputmethod.japanese",
    "com.android.inputmethod.korean" to "com.google.android.inputmethod.korean",
    "com.android.inputmethod.latin" to "com.google.android.inputmethod.latin",
    "com.android.inputmethod.pinyin" to "com.google.android.inputmethod.pinyin",
    "com.android.mail.notifier" to "com.google.android.gm",
    "com.google.android.apps.bigtop" to "com.google.android.apps.inbox",
    "com.google.android.apps.docs.editors.kix" to "com.google.android.apps.docs.editors.docs",
    "com.google.android.apps.docs.editors.punch" to "com.google.android.apps.docs.editors.slides",
    "com.google.android.apps.docs.editors.ritz" to "com.google.android.apps.docs.editors.sheets",
    "com.google.android.apps.docs.editors.sketchy" to "com.google.android.apps.docs.editors.slides",
    "com.google.android.apps.docs.editors.trix" to "com.google.android.apps.docs.editors.sheets",
    "com.google.android.apps.hangouts.content.EsProvider" to "com.google.android.talk",
    "com.google.android.apps.inputmethod.pinyin" to "com.google.android.inputmethod.pinyin",
    "com.google.android.apps.photos.content" to "com.google.android.apps.plus",
    "com.google.android.apps.photos.GooglePhotoDownsyncProvider" to "com.google.android.apps.plus",
    "com.google.android.finsky.AppIconProvider" to "com.android.vending",
    "com.google.android.finsky.QSBSuggestionsProvider2" to "com.android.vending",
    "com.google.android.finsky.RecentSuggestionsProvider" to "com.android.vending",
    "com.google.android.gm2.accountcache" to "com.google.android.gm",
    "com.google.android.gm2.conversation.provider" to "com.google.android.gm",
    "com.google.android.gmail.attachmentprovider" to "com.google.android.gm",
    "com.google.android.gmail.conversation.provider" to "com.google.android.gm",
    "com.google.android.gmail.provider" to "com.google.android.gm",
    "com.google.android.inputmethod.dictionarypack" to "com.google.android.inputmethod.latin",
    "com.google.android.launcher.settings" to "com.google.android.googlequicksearchbox",
    "com.google.android.location.copresence" to "com.google.android.gms",
    "com.google.android.location.internal" to "com.google.android.gms",
    "com.google.android.location.reporting" to "com.google.android.gms",
    "com.google.android.maps.NavigationAvailabilityProvider" to "com.google.android.apps.maps",
    "com.google.android.providers.talk" to "com.google.android.gsf",
    "com.google.android.velvet.extradex.ExtraDexHostProvider" to "com.google.android.googlequicksearchbox",
    "com.google.contacts.gal.provider" to "com.google.android.syncadapters.contacts",
    "com.google.plus.platform" to "com.google.android.apps.plus",
    "com.google.settings" to "com.google.android.gsf",
    "contacts;com.android.contacts" to "com.android.providers.contacts",
    "gmail-appindexing" to "com.google.android.gm",
    "gmail-disnot" to "com.google.android.gm",
    "gmail-ls" to "com.google.android.gm",
    "subscribedfeeds" to "com.google.android.gsf",
)

/**
 * The outcome of [guessPackage].
 *
 * @property packageInfo the best matching package, or null when nothing plausible was found.
 * @property error non-null when the supplied uid could not be parsed; matching then falls back
 *   to the identifier alone, exactly like the upstream Go implementation.
 */
data class PackageGuess(
    val packageInfo: PackageInfoModel?,
    val error: String? = null,
)

/**
 * Returns the app id (base uid) for [uid], stripping the user id from it.
 *
 * Shared application gids are translated into their corresponding app uids. Based on
 * `frameworks/base/core/java/android/os/UserHandle.java`.
 */
fun appId(uid: Int): Int {
    val normalized = uid % PER_USER_RANGE
    if (normalized in FIRST_SHARED_APPLICATION_GID..LAST_SHARED_APPLICATION_GID) {
        return normalized + FIRST_APPLICATION_UID - FIRST_SHARED_APPLICATION_GID
    }
    return normalized
}

/**
 * Returns the app id (base uid) for a uid string as found in bug reports and checkin logs.
 *
 * Accepts plain uids (user id included), abbreviated uids such as `u0a25` and shared
 * application gids.
 *
 * @return the app id, or null if [uid] is neither empty, numeric, nor an abbreviated uid.
 *   The empty string is a valid, expected input and yields 0.
 */
fun appIdFromString(uid: String): Int? {
    if (uid.isEmpty()) return 0

    val match = abbreviatedUidRegex.find(uid)
    if (match != null) {
        val value = match.groupValues[3].toIntOrNull() ?: return null
        return when (match.groupValues[2]) {
            "i" -> value + FIRST_ISOLATED_UID
            "a" -> value + FIRST_APPLICATION_UID
            else -> value // "s": unmodified app id
        }
    }

    val value = uid.toIntOrNull() ?: return null
    return appId(value)
}

/**
 * Returns true if [uid] belongs to a fully isolated sandboxed process.
 *
 * Such processes have no permissions of their own and appear as `u<user>i<appId>`
 * in bug reports.
 */
fun isSandboxedProcess(uid: Int): Boolean = uid in FIRST_ISOLATED_UID..LAST_ISOLATED_UID

/**
 * Returns the best guess for the package owning the given identifier (for example a sync
 * adapter or wakelock name), using [uid] and the package list from a bug report.
 *
 * Matching prefers a unique app id match. Shared uids and packages without a uid fall back
 * to substring matching against the known sync adapter names and then against the package
 * names themselves, preferring the longest match.
 *
 * The returned guess is never null, but [PackageGuess.packageInfo] may be.
 */
fun guessPackage(
    identifier: String,
    uid: String,
    packages: List<PackageInfoModel>,
): PackageGuess {
    val parsedAppId = appIdFromString(uid)
    val error = if (parsedAppId == null) invalidAppIdError(uid) else null
    val uidAppId = parsedAppId ?: 0

    if (uidAppId == 0) {
        return PackageGuess(guessPackageJustFromIdentifier(identifier, packages), error)
    }

    val matchedUidPackages = ArrayList<PackageInfoModel>()
    val missingUidPackages = ArrayList<PackageInfoModel>()
    for (pkg in packages) {
        val pkgAppId = appId(pkg.uid)
        if (pkgAppId == 0) {
            // A package has a uid of 0 if the device was unable to populate it; 0 is the default
            // for apps with unknown uids.
            missingUidPackages += pkg
        } else if (pkgAppId == uidAppId) {
            matchedUidPackages += pkg
        }
    }

    if (matchedUidPackages.size == 1) {
        return PackageGuess(matchedUidPackages[0], error)
    }

    // Found multiple packages with the same uid, or packages without a uid. Fall back to
    // comparing the identifier, considering packages without a uid first.
    val candidates = missingUidPackages + matchedUidPackages
    return PackageGuess(guessPackageJustFromIdentifier(identifier, candidates), error)
}

/**
 * Tries to guess the package based on the identifying string alone.
 *
 * There is no standard format for these names, so a curated sync adapter table is consulted
 * first; afterwards the identifier is matched against the known package names, keeping the
 * longest match to avoid e.g. "android" swallowing everything Google.
 *
 * Packages with an empty name never match (the upstream Go code would match all of them
 * because every string contains the empty string).
 */
private fun guessPackageJustFromIdentifier(
    identifier: String,
    packages: List<PackageInfoModel>,
): PackageInfoModel? {
    var target = identifier
    var longestKey = ""
    for ((key, packageName) in syncAdapterToPackageName) {
        if (identifier.contains(key) && key.length > longestKey.length) {
            target = packageName
            longestKey = key
        }
    }

    var likeliest: PackageInfoModel? = null
    for (pkg in packages) {
        if (pkg.name.isEmpty()) continue
        if (target.contains(pkg.name)) {
            if (likeliest == null || likeliest.name.length < pkg.name.length) {
                likeliest = pkg
            }
        }
    }
    return likeliest
}
