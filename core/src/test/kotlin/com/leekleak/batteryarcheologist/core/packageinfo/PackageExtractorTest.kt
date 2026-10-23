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

// Kotlin port of the extraction tests in
// battery-historian/packageutils/packageutils_test.go, adapted to PackageInfoModel
// instead of the usagestats PackageInfo protobuf.

package com.leekleak.batteryarcheologist.core.packageinfo

import com.leekleak.batteryarcheologist.core.model.PackageInfoModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageExtractorTest {

    @Test
    fun `extracts apps from app ops dump`() {
        // The spaces at the beginning of the input lines are found in actual bug reports, so
        // they are included here to make sure they do not cause a problem.
        val input = listOf(
            // These lines must be skipped.
            "DUMP OF SERVICE android.security.keystore:",
            // This line does not appear in the keystore dump, but tests that it is skipped.
            "Package evil.fake.package",
            "-------------------------------------------------------------------------------",
            // Parsing starts here.
            "DUMP OF SERVICE appops:",
            // These must not break parsing.
            "Current AppOps Service state:",
            "  Clients:",
            "    android.os.Binder@2d88a7f2:",
            "      ClientState{mAppToken=android.os.Binder@2d88a7f2, local}",
            // Shared integer uid.
            "  Uid 1000:",
            "    Package com.android.settings:",
            // Intermediate lines must not affect parsing.
            "      COARSE_LOCATION: mode=0; duration=0",
            "    Package android:",
            "      VIBRATE: mode=0; time=+39s500ms ago; duration=+59ms",
            // Singular integer uid.
            "  Uid 1013:",
            "    Package media:",
            // Singular abbreviated uid.
            "  Uid u0a2:",
            "    Package com.android.providers.calendar:",
            // Shared abbreviated uid.
            "  Uid u0a12:",
            "    Package com.google.android.gsf:",
            "      WAKE_LOCK: mode=0; time=+23m48s927ms ago; duration=+39ms",
            "    Package com.google.android.syncadapters.contacts:",
            "    Package com.google.android.gms:",
            "DUMP OF SERVICE next.service:",
            // The app ops dump has ended, so this must be skipped.
            "Package one.line.too.late:",
        ).joinToString("\n")

        val expected = mapOf(
            "com.android.settings" to PackageInfoModel(name = "com.android.settings", uid = 1000),
            "android" to PackageInfoModel(name = "android", uid = 1000),
            "media" to PackageInfoModel(name = "media", uid = 1013),
            "com.android.providers.calendar" to PackageInfoModel(
                name = "com.android.providers.calendar",
                uid = 10002,
            ),
            "com.google.android.gsf" to PackageInfoModel(name = "com.google.android.gsf", uid = 10012),
            "com.google.android.syncadapters.contacts" to PackageInfoModel(
                name = "com.google.android.syncadapters.contacts",
                uid = 10012,
            ),
            "com.google.android.gms" to PackageInfoModel(name = "com.google.android.gms", uid = 10012),
        )

        val result = extractAppsFromAppOpsDump(input)
        assertExtracted(expected, result)
    }

    @Test
    fun `extracts apps from package dump`() {
        // The spaces at the beginning of the input lines are found in actual bug reports, so
        // they are included here to make sure they do not cause a problem.
        val input = listOf(
            // These lines must be skipped.
            "DUMP OF SERVICE android.security.keystore:",
            // This line does not appear in the keystore dump, but tests that it is skipped.
            "Package evil.fake.package",
            "-------------------------------------------------------------------------------",
            // Parsing starts here.
            "DUMP OF SERVICE package:",
            // These must not break parsing.
            "Database versions:",
            "  SDK Version: internal=22 external=22",
            "  DB Version: internal=3 external=3",
            "  [com.android.sdm.plugins.dcmo]",
            // Expected line in the service dump.
            "Packages:",
            // Single uid with all fields populated.
            "  Package [com.google.android.youtube] (1cce8bc):",
            "    userId=10089 gids=[3003, 1028, 1015]",
            "    pkg=Package{259e0c2a com.google.android.youtube}",
            "    codePath=/data/app/com.google.android.youtube-1",
            "    versionCode=60013301 targetSdk=21",
            "    versionName=6.0.13",
            "    firstInstallTime=2014-12-05 14:23:12",
            "    lastUpdateTime=2014-12-10 21:46:43",
            // Applications with a shared uid.
            "  Package [com.google.android.gms] (24f5913f):",
            "    userId=10012 gids=[]",
            "    sharedUser=SharedUserSetting{2e98f1d5 com.google.uid.shared/10012}",
            "    pkg=Package{1cd5fca7 com.google.android.gms}",
            "    versionCode=6759430 targetSdk=21",
            "    versionName=6.7.59 (1671217-430)",
            "    applicationInfo=ApplicationInfo{55fd874 com.google.android.gms}",
            "    timeStamp=2015-01-09 08:30:14",
            "  Package [com.google.android.gsf] (248a3a0c):",
            "    userId=10012 gids=[]",
            "    sharedUser=SharedUserSetting{2e98f1d5 com.google.uid.shared/10012}",
            "    pkg=Package{114b2cfd com.google.android.gsf}",
            "    versionCode=22 targetSdk=22",
            "    versionName=LollipopMR1-1629941",
            // Package that does not include "gids" in the userId line.
            "  Package [com.google.android.apps.photos] (784178f):",
            "    userId=10070",
            "    pkg=Package{c1660f0 com.google.android.apps.photos}",
            "    versionCode=40112 targetSdk=21",
            "    versionName=1.1.1.9",
            "    firstInstallTime=2015-06-05 14:21:22",
            "    lastUpdateTime=2015-06-25 10:11:29",
            // Renamed package (renamed to com.android.packageinstaller).
            "Package [com.google.android.packageinstaller] (33f4931):",
            "    compat name=com.android.packageinstaller",
            "    userId=10077",
            "    pkg=Package{7254816 com.android.packageinstaller}",
            "    versionCode=23 targetSdk=23",
            "    versionName=6.0-2256973",
            "    timeStamp=2015-09-15 18:26:51",
            "    firstInstallTime=2015-07-06 12:04:19",
            "    lastUpdateTime=2015-09-15 18:26:51",
            "Renamed packages:",
            "  com.google.android.packageinstaller,com.android.packageinstaller",
            // System packages are only hidden and listed in this category when they are updated
            // with newer versions, so they must not override the more recent entry above.
            "Hidden system packages:",
            "Package [com.google.android.youtube] (1ff2e9b6):",
            " userId=10089 gids=[]",
            "pkg=Package{166726b7 com.google.android.youtube}",
            "codePath=/system/app/YouTube",
            "versionCode=51604100 targetSdk=19",
            "versionName=5.16.4",
            "DUMP OF SERVICE next.service:",
            // The package dump has ended, so this must be skipped.
            "  Package [one.line.too.late] (248a3a0c):",
            "    userId=10012 gids=[]",
        ).joinToString("\n")

        // firstInstallTime/lastUpdateTime are recognised upstream but PackageInfoModel has no
        // fields for them yet.
        val expected = mapOf(
            "com.google.android.youtube" to PackageInfoModel(
                name = "com.google.android.youtube",
                versionCode = 60013301L,
                versionName = "6.0.13",
                uid = 10089,
            ),
            "com.google.android.gms" to PackageInfoModel(
                name = "com.google.android.gms",
                versionCode = 6759430L,
                versionName = "6.7.59",
                uid = 10012,
                sharedUserId = "com.google.uid.shared",
            ),
            "com.google.android.gsf" to PackageInfoModel(
                name = "com.google.android.gsf",
                versionCode = 22L,
                versionName = "LollipopMR1-1629941",
                uid = 10012,
                sharedUserId = "com.google.uid.shared",
            ),
            "com.google.android.apps.photos" to PackageInfoModel(
                name = "com.google.android.apps.photos",
                versionCode = 40112L,
                versionName = "1.1.1.9",
                uid = 10070,
            ),
            "com.android.packageinstaller" to PackageInfoModel(
                name = "com.android.packageinstaller",
                versionCode = 23L,
                versionName = "6.0-2256973",
                uid = 10077,
            ),
        )

        val result = extractAppsFromPackageDump(input)
        assertExtracted(expected, result)
    }

    @Test
    fun `extracts apps from bug report`() {
        val input = listOf(
            // These lines must be skipped.
            "DUMP OF SERVICE android.security.keystore:",
            // This line does not appear in the keystore dump, but tests that it is skipped.
            "Package evil.fake.package",
            "-------------------------------------------------------------------------------",
            // Parsing starts here.
            "DUMP OF SERVICE appops:",
            "Current AppOps Service state:",
            "  Clients:",
            "    android.os.Binder@2d88a7f2:",
            "      ClientState{mAppToken=android.os.Binder@2d88a7f2, local}",
            "  Uid 1000:",
            "    Package com.android.settings:",
            "      COARSE_LOCATION: mode=0; duration=0",
            "    Package android:",
            "      VIBRATE: mode=0; time=+39s500ms ago; duration=+59ms",
            // No corresponding info from the package dump.
            "  Uid 1013:",
            "    Package media:",
            "  Uid u0a2:",
            "    Package com.android.providers.calendar:",
            "  Uid u0a12:",
            "    Package com.google.android.gsf:",
            "      WAKE_LOCK: mode=0; time=+23m48s927ms ago; duration=+39ms",
            "    Package com.google.android.syncadapters.contacts:",
            // This gets combined with the info from the package dump.
            "    Package com.google.android.gms:",
            "DUMP OF SERVICE next.service:",
            // The app ops dump has ended, so this must be skipped.
            "Package one.line.too.late:",
            "DUMP OF SERVICE package:",
            "Database versions:",
            "  SDK Version: internal=22 external=22",
            "  DB Version: internal=3 external=3",
            "  [com.android.sdm.plugins.dcmo]",
            "Packages:",
            // Single uid with all fields populated; no corresponding info from app ops.
            "  Package [com.google.android.youtube] (1cce8bc):",
            "    userId=10089 gids=[3003, 1028, 1015]",
            "    pkg=Package{259e0c2a com.google.android.youtube}",
            "    codePath=/data/app/com.google.android.youtube-1",
            "    versionCode=60013301 targetSdk=21",
            "    versionName=6.0.13",
            "    firstInstallTime=2014-12-05 14:23:12",
            "    lastUpdateTime=2014-12-10 21:46:43",
            // Application with a shared uid; data primarily comes from the package dump.
            "  Package [com.google.android.gms] (24f5913f):",
            "    userId=10012 gids=[]",
            "    sharedUser=SharedUserSetting{2e98f1d5 com.google.uid.shared/10012}",
            "    pkg=Package{1cd5fca7 com.google.android.gms}",
            "    versionCode=6759430 targetSdk=21",
            "    versionName=6.7.59 (1671217-430)",
            "    applicationInfo=ApplicationInfo{55fd874 com.google.android.gms}",
            "    timeStamp=2015-01-09 08:30:14",
            "Hidden system packages:",
            // Hidden and must not override the more recent package listed above.
            "Package [com.google.android.youtube] (1ff2e9b6):",
            " userId=10089 gids=[]",
            "pkg=Package{166726b7 com.google.android.youtube}",
            "codePath=/system/app/YouTube",
            "versionCode=51604100 targetSdk=19",
            "versionName=5.16.4",
            "DUMP OF SERVICE next.service:",
            // The package dump has ended, so this must be skipped.
            "  Package [two.lines.too.late] (248a3a0c):",
            "    userId=12345 gids=[]",
        ).joinToString("\n")

        val expected = mapOf(
            "com.google.android.youtube" to PackageInfoModel(
                name = "com.google.android.youtube",
                versionCode = 60013301L,
                versionName = "6.0.13",
                uid = 10089,
            ),
            "com.android.settings" to PackageInfoModel(name = "com.android.settings", uid = 1000),
            "android" to PackageInfoModel(name = "android", uid = 1000),
            "media" to PackageInfoModel(name = "media", uid = 1013),
            "com.android.providers.calendar" to PackageInfoModel(
                name = "com.android.providers.calendar",
                uid = 10002,
            ),
            "com.google.android.gsf" to PackageInfoModel(name = "com.google.android.gsf", uid = 10012),
            "com.google.android.syncadapters.contacts" to PackageInfoModel(
                name = "com.google.android.syncadapters.contacts",
                uid = 10012,
            ),
            "com.google.android.gms" to PackageInfoModel(
                name = "com.google.android.gms",
                versionCode = 6759430L,
                versionName = "6.7.59",
                uid = 10012,
                sharedUserId = "com.google.uid.shared",
            ),
        )

        val result = extractAppsFromBugReport(input)
        assertExtracted(expected, result)
    }

    @Test
    fun `package dump wins over app ops entries`() {
        val input = listOf(
            "DUMP OF SERVICE appops:",
            "  Uid 10012:",
            "    Package com.google.android.gms:",
            "DUMP OF SERVICE package:",
            "Packages:",
            "  Package [com.google.android.gms] (24f5913f):",
            "    userId=10012",
            "    versionCode=6759430 targetSdk=21",
            "    versionName=6.7.59",
        ).joinToString("\n")

        val result = extractAppsFromBugReport(input)

        assertEquals(listOf("com.google.android.gms"), result.packages.keys.toList())
        assertEquals("6.7.59", result.packages.getValue("com.google.android.gms").versionName)
    }

    @Test
    fun `reports malformed package dump lines`() {
        val input = listOf(
            "DUMP OF SERVICE package:",
            "Packages:",
            "    compat name=com.example.renamed",
            "    userId=10012",
            "    versionCode=12 targetSdk=21",
            "    versionName=1.0",
            "  Package [com.example.app] (24f5913f):",
            "    userId=10012",
            "    sharedUser=SharedUserSetting{2e98f1d5 com.example.shared/10013}",
            "DUMP OF SERVICE next.service:",
        ).joinToString("\n")

        val result = extractAppsFromPackageDump(input)

        assertTrue(result.errors.contains("found compat line before package line"))
        assertTrue(result.errors.contains("found userId line before package line"))
        assertTrue(result.errors.contains("found versionCode line before package line"))
        assertTrue(result.errors.contains("found versionName line before package line"))
        assertTrue(result.errors.contains("sharedUser uid is different from package uid"))
        assertEquals(setOf("com.example.app"), result.packages.keys)
        assertEquals("", result.packages.getValue("com.example.app").sharedUserId)
    }

    @Test
    fun `reports shared user uid mismatch`() {
        val input = listOf(
            "DUMP OF SERVICE package:",
            "Packages:",
            "  Package [com.example.app] (24f5913f):",
            "    userId=10099",
            "    sharedUser=SharedUserSetting{2e98f1d5 com.example.shared/10012}",
        ).joinToString("\n")

        val result = extractAppsFromPackageDump(input)

        assertEquals(listOf("sharedUser uid is different from package uid"), result.errors)
        assertEquals("", result.packages.getValue("com.example.app").sharedUserId)
        assertEquals(10099, result.packages.getValue("com.example.app").uid)
    }

    private fun assertExtracted(expected: Map<String, PackageInfoModel>, actual: PackageExtraction) {
        assertEquals("unexpected parsing errors: ${actual.errors}", emptyList<String>(), actual.errors)
        assertEquals("unexpected package names", expected.keys, actual.packages.keys)
        for ((name, pkg) in expected) {
            assertEquals("parsed package '$name' not correct", pkg, actual.packages[name])
        }
        assertEquals("unexpected number of packages", expected.size, actual.packages.size)
    }
}
