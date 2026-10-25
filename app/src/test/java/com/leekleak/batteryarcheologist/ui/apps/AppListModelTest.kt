package com.leekleak.batteryarcheologist.ui.apps

import com.leekleak.batteryarcheologist.core.analytics.AppPowerStat
import com.leekleak.batteryarcheologist.data.AppUsage
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppListModelTest {

    @Test
    fun `pinned apps move to the top alphabetically and leave the ranked list`() {
        val apps = listOf(
            usage("com.zeta", uid = 1),
            usage("com.beta", uid = 2),
            usage("com.alpha", uid = 3),
        )
        val labels = mapOf(
            "com.zeta" to "Zeta",
            "com.alpha" to "Alpha",
        )

        val sections = buildAppListSections(
            apps = apps,
            pinnedNames = setOf("com.zeta", "com.alpha"),
            query = "",
            labelsByPackage = labels,
        )

        assertEquals(listOf("Alpha", "Zeta"), sections.pinned.map { it.displayName })
        assertEquals(listOf("com.beta"), sections.apps.map { it.usage.app.name })
    }

    @Test
    fun `search matches labels package names and uids`() {
        val apps = listOf(
            usage("com.example.video", uid = 10123),
            usage("com.example.music", uid = 10124),
        )
        val info = mapOf(
            "com.example.video" to "Video Player",
        )

        val byLabel = buildAppListSections(apps, emptySet(), "player", info)
        assertEquals(
            listOf("com.example.video"),
            (byLabel.pinned + byLabel.apps).map { it.usage.app.name },
        )

        val byPackage = buildAppListSections(apps, emptySet(), "music", info)
        assertEquals(
            listOf("com.example.music"),
            (byPackage.pinned + byPackage.apps).map { it.usage.app.name },
        )

        val byUid = buildAppListSections(apps, emptySet(), "10123", info)
        assertEquals(
            listOf("com.example.video"),
            (byUid.pinned + byUid.apps).map { it.usage.app.name },
        )
    }

    @Test
    fun `search with no matches returns empty sections`() {
        val sections = buildAppListSections(
            apps = listOf(usage("com.example.video", uid = 10123)),
            pinnedNames = emptySet(),
            query = "nothing here",
            labelsByPackage = emptyMap(),
        )

        assertTrue(sections.isEmpty)
    }

    @Test
    fun `pseudo apps get readable fallback names`() {
        val sections = buildAppListSections(
            apps = listOf(usage("SYSTEM_UI", uid = 10185)),
            pinnedNames = emptySet(),
            query = "",
            labelsByPackage = emptyMap(),
        )

        assertEquals("System UI", sections.apps.single().displayName)
        assertEquals("System UI", fallbackAppName("SYSTEM_UI"))
        assertEquals("Android System", fallbackAppName("ANDROID_SYSTEM"))
        assertEquals("SHELL", fallbackAppName("SHELL"))
        assertEquals("com.example.video", fallbackAppName("com.example.video"))
    }

    private fun usage(name: String, uid: Int): AppUsage = AppUsage(
        app = BatteryStats.App(name = name, uid = uid),
        power = AppPowerStat(
            name = name,
            uid = uid,
            devicePowerMah = 0f,
            devicePowerPct = 0f,
            hasDevicePower = false,
        ),
    )
}
