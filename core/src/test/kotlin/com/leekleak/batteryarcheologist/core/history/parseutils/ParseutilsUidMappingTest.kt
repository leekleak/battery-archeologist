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
 * Port of TestUIDAndPackageNameMapping from
 * third_party/battery-historian/parseutils/parseutils_test.go.
 */

package com.leekleak.batteryarcheologist.core.history.parseutils

import com.leekleak.batteryarcheologist.core.model.PackageInfoModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseutilsUidMappingTest {
    @Test
    fun `maps uids and package names from apk lines and the package list`() {
        val inputCheckin = listOf(
            // Random lines that should be skipped.
            "9,0,l,pr,system,0,3150,0,0,0,0",
            "9,1000,l,wl,SyncManagerHandleSyncAlarm,0,f,0,1568,p,80,0,w,0",
            "9,0,l,sst,0",
            // Actual apk lines.
            "9,10005,l,apk,1,com.android.providers.calendar," +
                "com.android.providers.calendar.CalendarProviderIntentService,160,1,1",
            // Shared UID.
            "9,1001,l,apk,9,com.android.phone,com.android.phone.TelephonyDebugService,8630050,1,1",
            "9,1001,l,apk,0,com.android.stk,com.android.stk.StkAppService,8630050,1,1",
            // Legacy checkin format without the leading version field.
            "10014,l,apk,225,com.google.android.gms,com.google.android.gms.auth.GetToken,0,0,137",
            // Data also present in the package list.
            "9,10123,l,apk,25,com.google.android.youtube," +
                "com.google.android.youtube.ViralVideo,0,0,137",
            "9,10456,l,apk,5,com.google.android.apps.photos," +
                "com.google.android.apps.photos.AwesomePhoto,0,0,137",
            // Secondary user.
            "9,1010005,l,apk,5,com.android.providers.calendar," +
                "com.android.providers.calendar.CalendarProviderIntentService,160,1,1",
            "9,1010789,l,apk,1,com.google.android.play.games," +
                "com.google.android.play.games.SuperCoolGame,160,1,1",
            "9,1010456,l,apk,15,com.google.android.apps.photos," +
                "com.google.android.apps.photos.AwesomePhoto,0,0,137",
        ).joinToString("\n")

        val inputList = listOf(
            PackageInfoModel(name = "com.google.android.videos", uid = 10007),
            PackageInfoModel(
                name = "com.google.android.gsf",
                uid = 10014,
                sharedUserId = "com.google.uid.shared",
            ),
            PackageInfoModel(name = "com.google.android.youtube", uid = 10123),
            PackageInfoModel(name = "com.google.android.apps.photos", uid = 10456),
            PackageInfoModel(
                name = "com.random.app.one",
                uid = 10025,
                sharedUserId = "com.random.uid.shared",
            ),
            PackageInfoModel(
                name = "com.random.app.two",
                uid = 10025,
                sharedUserId = "com.random.uid.shared",
            ),
            PackageInfoModel(
                name = "com.random.app.three",
                uid = 10025,
                sharedUserId = "com.random.uid.shared",
            ),
        )

        val want = PackageUIDMapping(
            uidToPackage = mapOf(
                1001 to "com.android.phone;com.android.stk",
                10005 to "com.android.providers.calendar",
                10007 to "com.google.android.videos",
                10014 to "com.google.android.gms;com.google.android.gsf",
                10025 to "com.random.app.one;com.random.app.two;com.random.app.three",
                10123 to "com.google.android.youtube",
                10456 to "com.google.android.apps.photos",
                1010005 to "com.android.providers.calendar",
                1010456 to "com.google.android.apps.photos",
                1010789 to "com.google.android.play.games",
            ),
            packageToUID = mapOf(
                "com.android.phone" to 1001,
                "com.android.stk" to 1001,
                "com.android.providers.calendar" to 10005,
                "com.google.android.videos" to 10007,
                "com.google.android.gms" to 10014,
                "com.google.android.gsf" to 10014,
                "com.random.app.one" to 10025,
                "com.random.app.two" to 10025,
                "com.random.app.three" to 10025,
                "com.google.android.youtube" to 10123,
                "com.google.android.apps.photos" to 10456,
                "com.google.android.play.games" to 10789,
            ),
            sharedUIDName = mapOf(
                10014 to "GOOGLE_SERVICES",
                10025 to "SharedUserID(com.random.uid.shared)",
            ),
            pkgList = inputList,
        )

        val result = UIDAndPackageNameMapping(inputCheckin, inputList)
        assertTrue("unexpected errors: ${result.errors}", result.errors.isEmpty())
        assertEquals(want.uidToPackage, result.mapping.uidToPackage)
        assertEquals(want.packageToUID, result.mapping.packageToUID)
        assertEquals(want.sharedUIDName, result.mapping.sharedUIDName)
        assertEquals(want.pkgList, result.mapping.pkgList)
    }

    @Test
    fun `resolves known uids and shared uid labels`() {
        val mapping = PackageUIDMapping(
            uidToPackage = mapOf(10014 to "com.google.android.gms;com.google.android.gsf"),
            sharedUIDName = mapOf(10025 to "SharedUserID(com.random.uid.shared)"),
        )

        val known = ServiceUID(uid = "1000", service = "anything")
        assertEquals(null, mapping.matchServiceWithPackageInfo(known))
        assertEquals("ANDROID_SYSTEM", known.pkg?.name)

        val shared = ServiceUID(uid = "10025", service = "anything")
        assertEquals(null, mapping.matchServiceWithPackageInfo(shared))
        assertEquals("SharedUserID(com.random.uid.shared)", shared.pkg?.name)

        val combined = ServiceUID(uid = "10014", service = "anything")
        assertEquals(null, mapping.matchServiceWithPackageInfo(combined))
        assertEquals("com.google.android.gms;com.google.android.gsf", combined.pkg?.name)
        assertEquals("com.google.android.gms;com.google.android.gsf", mapping.packageName(10014))
    }
}
