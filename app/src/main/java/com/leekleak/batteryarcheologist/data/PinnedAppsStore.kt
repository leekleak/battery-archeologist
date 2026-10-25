package com.leekleak.batteryarcheologist.data

import android.content.Context

/**
 * Persists the set of apps pinned across every report.
 *
 * Pins are keyed by the package name from the checkin report, so an app stays
 * pinned when it appears in a later capture, even if its UID changed.
 */
interface PinnedAppsStore {
    fun load(): Set<String>
    fun save(pins: Set<String>)
}

/** [PinnedAppsStore] backed by a small private SharedPreferences file. */
class SharedPreferencesPinnedAppsStore(context: Context) : PinnedAppsStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun load(): Set<String> =
        preferences.getStringSet(KEY_PINNED_APPS, null)?.toSet().orEmpty()

    override fun save(pins: Set<String>) {
        preferences.edit().putStringSet(KEY_PINNED_APPS, pins.toSet()).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "pinned_apps"
        const val KEY_PINNED_APPS = "pinned"
    }
}
