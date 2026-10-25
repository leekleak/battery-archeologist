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
    TIMELINE(R.string.tab_timeline, R.drawable.ic_timeline),
    APPS(R.string.tab_apps, R.drawable.ic_apps),
    OTHER(R.string.tab_other, R.drawable.ic_system),
    ;

    val testTag: String get() = "tab_${name.lowercase()}"
}
