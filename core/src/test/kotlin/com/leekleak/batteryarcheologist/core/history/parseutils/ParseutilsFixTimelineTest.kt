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
 * Port of TestFixTimeline from third_party/battery-historian/parseutils/parseutils_test.go.
 */

package com.leekleak.batteryarcheologist.core.history.parseutils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseutilsFixTimelineTest {
    @Test
    fun `normalizes timestamps around reboots`() {
        val input = listOf(
            "9,0,i,vers,12,116,LVX72L,LVY29G",
            "9,h,0:RESET:TIME:141688070",
            "9,h,0,Bl=46,Bs=d,Bh=g,Bp=u,Bt=326,Bv=3814,+r,+BP",
            "9,h,292:TIME:141688362",
            "9,h,36838,Bt=336",
            "9,h,207,Bp=n,Bt=390,Bv=3815,-BP",
            "9,h,4:START",
            "9,h,0:TIME:507421",
            "9,h,6996,Bl=44,Bs=d,Bh=g,Bp=n,Bt=285,Bv=3703,+r",
            "9,h,85:TIME:514503",
            "9,h,1686,+s,+S",
            "9,h,288,Sb=1",
            "9,h,386,Sb=0,+Eur=2",
            "9,h,309,+a,+Euf=2",
            "9,h,236,Pst=off",
            "9,h,1371,+Psc,Pst=out",
            "9,h,33233,Sb=1",
            "9,h,9286,Bl=43,Bv=3765",
            "9,h,0:TIME:1422918458646",
            "9,h,5571,Bl=58,Bs=d,Bh=g,Bp=n,Bt=227,Bv=3803,+r",
            "9,h,1246,+s,+S,Sb=4",
            "9,h,533,Sb=1,+Wr,+W",
            "9,h,605,-W",
            "9,h,2,+a,+W,Wsp=scan,+Eur=2",
            "9,h,351,-a,",
            "9,h,906,-a",
            "9,h,43,+a",
            "9,h,31,-a",
            "9,h,50,+a",
            "9,h,37,-Psc,-a,Pst=in",
            "9,h,788:TIME:1422918470191",
            "9,h,900,+Wl,+Ws,+Pr,Pcn=lte,Pss=2",
            "9,h,1065,-Ws",
            "9,h,1299,-Wl",
            "9,h,1629,+Wl,+Ws",
            "9,h,897,-Wl,-Ws",
            "9,h,161,+Eur=137",
            "9,h,3628,+Wl",
            "9,h,4429,+Ws,+a",
            "9,h,1185,-Ws",
            "9,h,3593,Pss=3",
            "9,h,6966,-a",
            "9,h,2215,-W",
            "9,h,980,+W",
            "9,h,3043,+S",
            "9,h,0,-S",
            "9,h,1184,+Ws",
            "9,h,193,Bl=57,Bv=3531,-s,-Ws",
            "9,h,5530,Wsp=asced",
            "9,h,771,Wsp=4-way",
            "9,h,21,Wss=4,Wsp=group",
            "9,h,130,+Ws,Wsp=compl",
            "9,h,391,-Ws",
            "9,h,2024,+s",
            "9,h,3067,-s,+a",
            "9,h,5377,-Wl",
            "9,h,3138,+Ws,-Pr,Pcn=none",
            "9,h,593,-Ws",
            "9,h,1032,+S",
            "9,h,1181,+Wl",
            "9,h,213,-Wl",
            "9,h,492,-S",
            "9,h,709:SHUTDOWN",
            "9,h,38:START",
            "9,h,0:TIME:1422979129104",
            "9,h,5902,Bl=56,Bs=d,Bh=g,Bp=a,Bt=143,Bv=3921,+r,+BP",
            "9,h,820,+Euf=2",
            // Random lines that should be filtered out.
            "9,0,i,uid,10079,com.google.android.youtube",
            "9,0,l,br,326100,171,0,0,1",
        ).joinToString("\n")

        val want = listOf(
            "9,0,i,vers,12,116,LVX72L,LVY29G",
            "9,h,0:RESET:TIME:141688070",
            "9,h,0,Bl=46,Bs=d,Bh=g,Bp=u,Bt=326,Bv=3814,+r,+BP",
            "9,h,292:TIME:141688362",
            "9,h,36838,Bt=336",
            "9,h,207,Bp=n,Bt=390,Bv=3815,-BP",
            "9,h,4:START",
            "9,h,0:TIME:1422918406152",
            "9,h,6996,Bl=44,Bs=d,Bh=g,Bp=n,Bt=285,Bv=3703,+r",
            "9,h,85:TIME:1422918413233",
            "9,h,1686,+s,+S",
            "9,h,288,Sb=1",
            "9,h,386,Sb=0,+Eur=2",
            "9,h,309,+a,+Euf=2",
            "9,h,236,Pst=off",
            "9,h,1371,+Psc,Pst=out",
            "9,h,33233,Sb=1",
            "9,h,9286,Bl=43,Bv=3765",
            "9,h,0:TIME:1422918460028",
            "9,h,5571,Bl=58,Bs=d,Bh=g,Bp=n,Bt=227,Bv=3803,+r",
            "9,h,1246,+s,+S,Sb=4",
            "9,h,533,Sb=1,+Wr,+W",
            "9,h,605,-W",
            "9,h,2,+a,+W,Wsp=scan,+Eur=2",
            "9,h,351,-a,",
            "9,h,906,-a",
            "9,h,43,+a",
            "9,h,31,-a",
            "9,h,50,+a",
            "9,h,37,-Psc,-a,Pst=in",
            "9,h,788:TIME:1422918470191",
            "9,h,900,+Wl,+Ws,+Pr,Pcn=lte,Pss=2",
            "9,h,1065,-Ws",
            "9,h,1299,-Wl",
            "9,h,1629,+Wl,+Ws",
            "9,h,897,-Wl,-Ws",
            "9,h,161,+Eur=137",
            "9,h,3628,+Wl",
            "9,h,4429,+Ws,+a",
            "9,h,1185,-Ws",
            "9,h,3593,Pss=3",
            "9,h,6966,-a",
            "9,h,2215,-W",
            "9,h,980,+W",
            "9,h,3043,+S",
            "9,h,0,-S",
            "9,h,1184,+Ws",
            "9,h,193,Bl=57,Bv=3531,-s,-Ws",
            "9,h,5530,Wsp=asced",
            "9,h,771,Wsp=4-way",
            "9,h,21,Wss=4,Wsp=group",
            "9,h,130,+Ws,Wsp=compl",
            "9,h,391,-Ws",
            "9,h,2024,+s",
            "9,h,3067,-s,+a",
            "9,h,5377,-Wl",
            "9,h,3138,+Ws,-Pr,Pcn=none",
            "9,h,593,-Ws",
            "9,h,1032,+S",
            "9,h,1181,+Wl",
            "9,h,213,-Wl",
            "9,h,492,-S",
            "9,h,709:SHUTDOWN",
            "9,h,38:START",
            "9,h,0:TIME:1422979129104",
            "9,h,5902,Bl=56,Bs=d,Bh=g,Bp=a,Bt=143,Bv=3921,+r,+BP",
            "9,h,820,+Euf=2",
        )

        val result = fixTimeline(input)
        assertNull(result.error)
        assertTrue(result.timestampsAltered)
        assertEquals(want, result.lines)
    }

    @Test
    fun `leaves history untouched when there is no later time statement`() {
        val input = listOf(
            "9,h,0:RESET:TIME:1422620451417",
            "9,h,1000,+S",
            "9,h,2000,-S",
        ).joinToString("\n")

        val result = fixTimeline(input)
        assertNull(result.error)
        assertFalse(result.timestampsAltered)
        assertEquals(input.split('\n'), result.lines)
    }
}
