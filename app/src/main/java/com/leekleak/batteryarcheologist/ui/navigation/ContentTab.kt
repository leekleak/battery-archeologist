package com.leekleak.batteryarcheologist.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.leekleak.batteryarcheologist.R

/**
 * Top-level destinations shown in the floating bottom toolbar.
 *
 * Kept in the navigation package so the view model, toolbar and host screen
 * all agree on the same destination set without depending on each other.
 */
enum class ContentTab(
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
) {
    OVERVIEW(R.string.tab_overview, R.drawable.ic_overview),
    TIMELINE(R.string.tab_timeline, R.drawable.ic_timeline),
    SYSTEM(R.string.tab_system, R.drawable.ic_system),
    APPS(R.string.tab_apps, R.drawable.ic_apps),
    REPORTS(R.string.tab_reports, R.drawable.ic_reports),
    ;

    val testTag: String get() = "tab_${name.lowercase()}"
}
