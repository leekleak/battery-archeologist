package com.leekleak.batteryarcheologist.ui.apps

import com.leekleak.batteryarcheologist.data.AppUsage
import java.util.Locale

/** One app row after label resolution and search filtering. */
data class AppListEntry(
    val usage: AppUsage,
    val displayName: String,
)

/**
 * Pinned apps stay in their own section so their order is stable between
 * recordings; everything else keeps the power ranking from the repository.
 */
data class AppListSections(
    val pinned: List<AppListEntry>,
    val apps: List<AppListEntry>,
) {
    val isEmpty: Boolean get() = pinned.isEmpty() && apps.isEmpty()
}

/**
 * Filters [apps] by label, package name or UID and splits the result into the
 * pinned section and the normal power-ranked list.
 *
 * Pinned entries are sorted alphabetically (not by power) so the same app
 * appears in the same position when the user switches reports. They are not
 * repeated in [AppListSections.apps].
 */
internal fun buildAppListSections(
    apps: List<AppUsage>,
    pinnedNames: Set<String>,
    query: String,
    labelsByPackage: Map<String, String>,
): AppListSections {
    val needle = query.trim().lowercase(Locale.US)
    val entries = apps.map { usage ->
        val packageName = usage.app.name.orEmpty()
        AppListEntry(
            usage = usage,
            displayName = labelsByPackage[packageName] ?: fallbackAppName(packageName),
        )
    }.filter { entry ->
        needle.isEmpty() || entry.matches(needle)
    }

    val pinned = entries
        .filter { it.usage.app.name.orEmpty() in pinnedNames }
        .sortedWith(
            compareBy<AppListEntry> { it.displayName.lowercase(Locale.US) }
                .thenBy { it.usage.app.name.orEmpty() },
        )
    val remaining = entries.filter { it.usage.app.name.orEmpty() !in pinnedNames }
    return AppListSections(pinned = pinned, apps = remaining)
}

private fun AppListEntry.matches(needle: String): Boolean {
    val packageName = usage.app.name.orEmpty().lowercase(Locale.US)
    return displayName.lowercase(Locale.US).contains(needle) ||
        packageName.contains(needle) ||
        (usage.app.uid ?: 0).toString().contains(needle)
}
