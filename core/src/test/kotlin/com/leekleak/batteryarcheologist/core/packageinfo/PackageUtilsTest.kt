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

// Kotlin port of battery-historian/packageutils/packageutils_test.go, adapted to
// PackageInfoModel instead of the usagestats PackageInfo protobuf.

package com.leekleak.batteryarcheologist.core.packageinfo

import com.leekleak.batteryarcheologist.core.model.PackageInfoModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageUtilsTest {

    @Test
    fun `app id strips user id and maps shared gids`() {
        assertEquals(10002, appId(10002))
        assertEquals(14, appId(1000014))
        assertEquals(10023, appId(50023))
        assertEquals(10000, appId(50000))
        assertEquals(19999, appId(59999))
        assertEquals(99005, appId(99005))
        assertEquals(0, appId(0))
    }

    @Test
    fun `app id from string handles plain abbreviated and shared uids`() {
        assertEquals(0, appIdFromString(""))
        assertEquals(10001, appIdFromString("10001"))
        assertEquals(10025, appIdFromString("u0a25"))
        assertEquals(10025, appIdFromString("u10a25"))
        assertEquals(99007, appIdFromString("u0i7"))
        assertEquals(42, appIdFromString("u0s42"))
        assertEquals(10023, appIdFromString("50023"))
        assertEquals(14, appIdFromString("1000014"))
    }

    @Test
    fun `app id from string rejects unparseable uids`() {
        assertNull(appIdFromString("text"))
        assertNull(appIdFromString("123-456-7890"))
        assertNull(appIdFromString("u0a"))
    }

    @Test
    fun `sandboxed processes are isolated uids`() {
        assertTrue(isSandboxedProcess(99000))
        assertTrue(isSandboxedProcess(99500))
        assertTrue(isSandboxedProcess(99999))
        assertFalse(isSandboxedProcess(98999))
        assertFalse(isSandboxedProcess(10000))
        assertFalse(isSandboxedProcess(100000))
    }

    @Test
    fun `guesses packages when all packages have uids`() {
        val packageList = listOf(
            pkg("android", 1),
            pkg("com.android.vending", 2),
            // Two packages sharing the same uid.
            pkg("com.android.providers.contacts", 3),
            pkg("com.android.providers.calendar", 3),
            pkg("com.google.android.apps.inbox", 4),
            pkg("com.google.android.apps.cloudprint", 5),
            pkg("com.google.android.apps.docs.editors.docs", 6),
            pkg("com.google.android.apps.docs.editors.sheets", 7),
            pkg("com.google.android.apps.fitness", 8),
            pkg("com.google.android.apps.plus", 9),
            pkg("com.google.android.music", 11),
            // Two packages sharing the same uid.
            pkg("com.google.android.gms", 12),
            pkg("com.google.android.gsf", 12),
            pkg("com.google.android.apps.docs", 13),
            pkg("com.google.android.keep", 14),
            pkg("com.google.android.talk", 15),
            pkg("com.google.android.plus", 16),
            pkg("com.google.android.googlequicksearchbox", 17),
            pkg("com.google.android.deskclock", 18),
            pkg("com.google.android.gm", 19),
            pkg("com.android.vending", 10023),
        )

        assertGuesses(
            packageList,
            listOf(
                // UID matching works correctly for non-shared uids.
                ServiceCase("com.google.android.keep/com.google/XXX@gmail.com", "14", "com.google.android.keep"),
                ServiceCase(
                    "com.google.android.apps.plus.content.EsProvider/com.google/XXX@gmail.com/extra",
                    "9",
                    "com.google.android.apps.plus",
                ),
                ServiceCase(
                    "com.google.android.apps.bigtop.provider.bigtopprovider/com.google/XXX@google.com",
                    "4",
                    "com.google.android.apps.inbox",
                ),
                ServiceCase("gmail-ls/com.google/XXX@gmail.com", "19", "com.google.android.gm"),
                ServiceCase(
                    "com.google.android.apps.docs.editors.trix/com.google/XXX@gmail.com",
                    "7",
                    "com.google.android.apps.docs.editors.sheets",
                ),
                ServiceCase(
                    "com.google.android.apps.cloudprint.cloudprintprovider",
                    "5",
                    "com.google.android.apps.cloudprint",
                ),
                // Gmail can have a sync under the com.android.contacts name.
                ServiceCase("com.android.contacts/com.google/XXX@gmail.com", "19", "com.google.android.gm"),

                // UID and package name matching work correctly for shared uids.
                ServiceCase(
                    "com.google.android.gms.drive.sync/com.google/XXX@gmail.com",
                    "12",
                    "com.google.android.gms",
                ),
                ServiceCase(
                    "com.google.android.gms.games.background/com.google/XXX@google.com",
                    "12",
                    "com.google.android.gms",
                ),
                ServiceCase(
                    "com.google.android.location.reporting/com.google/XXX@google.com",
                    "12",
                    "com.google.android.gms",
                ),
                ServiceCase(
                    "com.google.android.location.copresence/com.google/XXX@google.com",
                    "12",
                    "com.google.android.gms",
                ),

                // Package name matching works for cases where a uid is missing.
                ServiceCase("com.android.calendar/com.google/XXX@google.com", expected = "com.android.providers.calendar"),
                ServiceCase("subscribedfeeds/com.google/XXX@google.com", expected = "com.google.android.gsf"),
                ServiceCase(
                    "com.google.android.apps.photos.GooglePhotoDownsyncProvider/com.google/XXX@google.com",
                    expected = "com.google.android.apps.plus",
                ),
                ServiceCase(
                    "com.google.android.apps.bigtop.provider.bigtopprovider/com.google/XXX@google.com",
                    expected = "com.google.android.apps.inbox",
                ),
                ServiceCase("gmail-ls/com.google/XXX@gmail.com", expected = "com.google.android.gm"),
                ServiceCase(
                    "com.google.android.gms.games.background/com.google/XXX@google.com",
                    expected = "com.google.android.gms",
                ),

                // Secondary user uids.
                ServiceCase("com.google.android.keep/com.google/XXX@gmail.com", "1000014", "com.google.android.keep"),
                ServiceCase(
                    "com.google.android.apps.plus.content.EsProvider/com.google/XXX@gmail.com/extra",
                    "1000009",
                    "com.google.android.apps.plus",
                ),
                ServiceCase(
                    "com.google.android.apps.bigtop.provider.bigtopprovider/com.google/XXX@google.com",
                    "1000004",
                    "com.google.android.apps.inbox",
                ),
                ServiceCase("gmail-ls/com.google/XXX@gmail.com", "1000019", "com.google.android.gm"),
                ServiceCase(
                    "com.google.android.apps.docs.editors.trix/com.google/XXX@gmail.com",
                    "1000007",
                    "com.google.android.apps.docs.editors.sheets",
                ),

                // A shared gid maps back to the package uid.
                ServiceCase("dex2oat", "50023", "com.android.vending"),
            ),
        )
    }

    @Test
    fun `guesses packages when some packages are missing uids`() {
        val packageList = listOf(
            pkg("com.google.android.volta"),
            pkg("com.google.android.inputmethod.pinyin"),
            pkg("android", 1),
            pkg("com.android.vending", 2),
            // Two packages sharing the same uid.
            pkg("com.android.providers.contacts", 3),
            pkg("com.android.providers.calendar", 3),
            pkg("com.google.android.apps.inbox"),
            pkg("com.google.android.apps.cloudprint"),
            pkg("com.google.android.apps.docs.editors.docs", 6),
            pkg("com.google.android.apps.docs.editors.sheets", 7),
            pkg("com.google.android.apps.fitness", 8),
            pkg("com.google.android.apps.plus", 9),
            pkg("com.google.android.gm", 10),
            pkg("com.google.android.music", 11),
            // Two packages sharing the same uid.
            pkg("com.google.android.gms", 12),
            pkg("com.google.android.gsf", 12),
            pkg("com.google.android.apps.docs", 13),
            pkg("com.google.android.keep"),
            pkg("com.google.android.talk", 15),
            pkg("com.google.android.plus", 16),
            pkg("com.google.android.googlequicksearchbox"),
            pkg("com.google.android.deskclock", 18),
        )

        assertGuesses(
            packageList,
            listOf(
                // UID matching works correctly for non-shared uids.
                ServiceCase(
                    "com.google.android.apps.plus.content.EsProvider/com.google/XXX@gmail.com/extra",
                    "9",
                    "com.google.android.apps.plus",
                ),
                ServiceCase("gmail-ls/com.google/XXX@gmail.com", "10", "com.google.android.gm"),
                ServiceCase(
                    "com.google.android.apps.docs.editors.trix/com.google/XXX@gmail.com",
                    "7",
                    "com.google.android.apps.docs.editors.sheets",
                ),
                // Gmail can have a sync under the com.android.contacts name.
                ServiceCase("com.android.contacts/com.google/XXX@gmail.com", "10", "com.google.android.gm"),

                // UID and package name matching work correctly for shared uids.
                ServiceCase(
                    "com.google.android.gms.drive.sync/com.google/XXX@gmail.com",
                    "12",
                    "com.google.android.gms",
                ),
                ServiceCase(
                    "com.google.android.gms.games.background/com.google/XXX@google.com",
                    "12",
                    "com.google.android.gms",
                ),
                ServiceCase(
                    "com.google.android.location.reporting/com.google/XXX@google.com",
                    "12",
                    "com.google.android.gms",
                ),
                ServiceCase(
                    "com.google.android.location.copresence/com.google/XXX@google.com",
                    "12",
                    "com.google.android.gms",
                ),

                // Package name matching works for cases where a uid is missing.
                ServiceCase("com.google.android.keep/com.google/XXX@gmail.com", "14", "com.google.android.keep"),
                ServiceCase(
                    "com.google.android.apps.bigtop.provider.bigtopprovider/com.google/XXX@google.com",
                    "4",
                    "com.google.android.apps.inbox",
                ),
                ServiceCase(
                    "com.google.android.apps.cloudprint.cloudprintprovider",
                    "5",
                    "com.google.android.apps.cloudprint",
                ),
                ServiceCase("com.android.calendar/com.google/XXX@google.com", expected = "com.android.providers.calendar"),
                ServiceCase("subscribedfeeds/com.google/XXX@google.com", expected = "com.google.android.gsf"),
                ServiceCase(
                    "com.google.android.apps.photos.GooglePhotoDownsyncProvider/com.google/XXX@google.com",
                    expected = "com.google.android.apps.plus",
                ),
                ServiceCase(
                    "com.google.android.apps.bigtop.provider.bigtopprovider/com.google/XXX@google.com",
                    expected = "com.google.android.apps.inbox",
                ),
                ServiceCase(
                    "Volta-com.google.android.volta.BatteryStatsCollectorService",
                    "1010076",
                    "com.google.android.volta",
                ),
                ServiceCase(
                    "*sync*/com.google.android.apps.inputmethod.pinyin.user_dictionary/com.google/XXX@google.com",
                    "10047",
                    "com.google.android.inputmethod.pinyin",
                ),
            ),
        )
    }

    @Test
    fun `guesses packages when all uids are zero`() {
        val packageList = listOf(
            pkg("com.google.android.volta"),
            pkg("com.google.android.inputmethod.pinyin"),
            pkg("android"),
            pkg("com.android.vending"),
            pkg("com.android.providers.contacts"),
            pkg("com.android.providers.calendar"),
            pkg("com.google.android.apps.inbox"),
            pkg("com.google.android.apps.cloudprint"),
            pkg("com.google.android.apps.fitness"),
            pkg("com.google.android.apps.plus"),
            pkg("com.google.android.gm"),
            pkg("com.google.android.gms"),
            pkg("com.google.android.gsf"),
            pkg("com.google.android.apps.docs"),
            pkg("com.google.android.plus"),
            pkg("com.google.android.googlequicksearchbox"),
        )

        // With every uid at zero, everything has to be matched from the identifying string.
        assertGuesses(
            packageList,
            listOf(
                ServiceCase("subscribedfeeds/com.google/XXX@google.com", expected = "com.google.android.gsf"),
                ServiceCase(
                    "com.google.android.apps.bigtop.provider.bigtopprovider/com.google/XXX@google.com",
                    expected = "com.google.android.apps.inbox",
                ),

                // These exercise the missing-uid bucket and then the identifier matching.
                ServiceCase("gmail-ls/com.google/XXX@gmail.com", "10", "com.google.android.gm"),
                ServiceCase(
                    "com.google.android.location.reporting/com.google/XXX@google.com",
                    "12",
                    "com.google.android.gms",
                ),
                ServiceCase(
                    "com.google.android.gms.plus.action/com.google/XXX@google.com",
                    "12",
                    "com.google.android.gms",
                ),
                // Without the uid there is no way to know this belongs to Gmail, so the
                // contacts provider is the documented best guess.
                ServiceCase("com.android.contacts/com.google/XXX@gmail.com", "10", "com.android.providers.contacts"),
            ),
        )
    }

    @Test
    fun `returns no package for invalid service descriptions`() {
        val packageList = listOf(
            pkg("android", 1),
            pkg("com.android.vending", 2),
            // Two packages sharing the same uid.
            pkg("com.android.providers.contacts", 3),
            pkg("com.android.providers.calendar", 3),
            pkg("com.google.android.apps.inbox", 4),
            pkg("com.google.android.apps.cloudprint", 5),
            pkg("com.google.android.apps.docs.editors.docs", 6),
            pkg("com.google.android.apps.docs.editors.sheets", 7),
            pkg("com.google.android.apps.fitness", 8),
            pkg("com.google.android.apps.plus", 9),
            pkg("com.google.android.music", 11),
            // Two packages sharing the same uid.
            pkg("com.google.android.gms", 12),
            pkg("com.google.android.gsf", 12),
            pkg("com.google.android.apps.docs", 13),
            pkg("com.google.android.keep", 14),
            pkg("com.google.android.talk", 15),
            pkg("com.google.android.plus", 16),
            pkg("com.google.android.googlequicksearchbox", 17),
            pkg("com.google.android.deskclock", 18),
            pkg("com.google.android.gm", 19),
        )

        assertGuesses(
            packageList,
            listOf(
                // UID matching works for empty and non-identifying service strings.
                ServiceCase("", "14", "com.google.android.keep"),
                ServiceCase("", "4", "com.google.android.apps.inbox"),
                ServiceCase("Naaa, what's up doc?", "5", "com.google.android.apps.cloudprint"),

                // Both fields empty.
                ServiceCase("", "", null),
                ServiceCase("", expected = null),

                // Non-identifying service strings with no uid.
                ServiceCase("Dude, where's my UID?", expected = null),

                // Uids that are not in the package list.
                ServiceCase("To infinity, and beyond!", "123456789", null),
                ServiceCase("", "27", null),
            ),
        )

        // An unparseable uid yields an error and no package.
        val guess = guessPackage("Here's my number, so call me maybe", "123-456-7890", packageList)
        val error = guess.error
        assertNotNull("error not thrown with invalid unparsable uid", error)
        assertTrue(error!!.contains("error getting appID from string"))
        assertTrue(error.contains("123-456-7890"))
        assertNull(guess.packageInfo)
    }

    @Test
    fun `guesses packages from live data with missing uids`() {
        // This device was running a build that did not upload uids.
        val packageList = listOf(
            pkg("android"),
            pkg("com.google.android.gm"),
            pkg("com.google.android.gm.exchange"),
            pkg("com.google.android.gms"),
            pkg("com.google.android.googlequicksearchbox"),
            pkg("com.google.android.gsf"),
            pkg("com.google.android.gsf.login"),
        )

        assertGuesses(
            packageList,
            listOf(
                ServiceCase("com.google.android.gms.fitness/com.google/XXX@gmail.com", "10007", "com.google.android.gms"),
                ServiceCase("com.google.android.gms.games/com.google/XXX@gmail.com", "10007", "com.google.android.gms"),
                ServiceCase("com.google.android.gms.games/com.google/XXX@google.com", "10007", "com.google.android.gms"),
                ServiceCase("com.google.android.gms.people/com.google/XXX@gmail.com", "10007", "com.google.android.gms"),
                ServiceCase(
                    "com.google.android.gms.plus.action/com.google/XXX@google.com",
                    "10007",
                    "com.google.android.gms",
                ),
                ServiceCase(
                    "com.google.android.location.reporting/com.google/XXX@gmail.com",
                    "10007",
                    "com.google.android.gms",
                ),
                ServiceCase("gmail-ls/com.google/XXX@google.com", "10070", "com.google.android.gm"),
                ServiceCase("gmail-ls/com.google/XXX@gmail.com", "10070", "com.google.android.gm"),
            ),
        )
    }

    private fun assertGuesses(packageList: List<PackageInfoModel>, cases: List<ServiceCase>) {
        for (case in cases) {
            val guess = guessPackage(case.service, case.uid, packageList)
            assertNull("unexpected error for $case", guess.error)
            assertEquals("wrong package for $case", case.expected, guess.packageInfo?.name)
        }
    }

    private fun pkg(name: String, uid: Int = 0) = PackageInfoModel(name = name, uid = uid)

    private data class ServiceCase(val service: String, val uid: String = "", val expected: String?)
}
