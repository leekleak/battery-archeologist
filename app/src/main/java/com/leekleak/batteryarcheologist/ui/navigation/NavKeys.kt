package com.leekleak.batteryarcheologist.ui.navigation

import androidx.navigation3.runtime.NavKey

/**
 * Navigation 3 destination keys.
 *
 * The back stack starts at [ReportsKey]. Opening a saved report pushes
 * [ReportViewerKey] on top of it, while [PermissionKey] is shown when the app
 * needs the user to set up Shizuku before reports can be captured.
 */
data object ReportsKey : NavKey

data class ReportViewerKey(val reportName: String) : NavKey

data object PermissionKey : NavKey
