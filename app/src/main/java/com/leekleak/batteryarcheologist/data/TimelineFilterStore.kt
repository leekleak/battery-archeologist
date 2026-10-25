package com.leekleak.batteryarcheologist.data

import android.content.Context

/**
 * Which timeline metrics the user wants to see.
 *
 * [active] = false means "all metrics", including metrics introduced in newer
 * reports. When [active] is true, only [metrics] are shown.
 */
data class TimelineFilter(
    val active: Boolean = false,
    val metrics: Set<String> = emptySet(),
)

/** Persists the global timeline metric filter. */
interface TimelineFilterStore {
    fun load(): TimelineFilter
    fun save(filter: TimelineFilter)
}

/** [TimelineFilterStore] backed by a small private SharedPreferences file. */
class SharedPreferencesTimelineFilterStore(context: Context) : TimelineFilterStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun load(): TimelineFilter = TimelineFilter(
        active = preferences.getBoolean(KEY_ACTIVE, false),
        metrics = preferences.getStringSet(KEY_METRICS, null)?.toSet().orEmpty(),
    )

    override fun save(filter: TimelineFilter) {
        preferences.edit()
            .putBoolean(KEY_ACTIVE, filter.active)
            .putStringSet(KEY_METRICS, filter.metrics.toSet())
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "timeline_filter"
        const val KEY_ACTIVE = "active"
        const val KEY_METRICS = "metrics"
    }
}
