package com.leekleak.batteryarcheologist.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey

/**
 * Owns the observable Navigation 3 back stack.
 *
 * The stack is never empty: it always starts at [ReportsKey] and keeps at
 * least that entry in place, so [goBack] only pops pushed destinations.
 */
class AppNavigator(initial: List<NavKey> = listOf(ReportsKey)) {

    val backStack: SnapshotStateList<NavKey> = mutableStateListOf<NavKey>().apply {
        addAll(initial.ifEmpty { listOf(ReportsKey) })
    }

    val current: NavKey get() = backStack.last()

    /** Pushes [key] unless it is already the top destination. */
    fun goTo(key: NavKey) {
        if (backStack.lastOrNull() != key) {
            backStack.add(key)
        }
    }

    /**
     * Pops the top destination.
     *
     * Returns `false` when only the root [ReportsKey] remains.
     */
    fun goBack(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    companion object {
        private const val REPORTS_ENCODED = "reports"
        private const val PERMISSION_ENCODED = "permission"
        private const val VIEWER_PREFIX = "viewer:"

        /** Saves keys as stable strings: `reports`, `permission` or `viewer:<name>`. */
        val Saver: Saver<AppNavigator, Any> = listSaver(
            save = { navigator -> navigator.backStack.map { it.encode() } },
            restore = { encoded: List<String> ->
                AppNavigator(encoded.map { decodeKey(it) }.ifEmpty { listOf(ReportsKey) })
            },
        )

        private fun NavKey.encode(): String = when (this) {
            ReportsKey -> REPORTS_ENCODED
            PermissionKey -> PERMISSION_ENCODED
            is ReportViewerKey -> "$VIEWER_PREFIX$reportName"
            else -> REPORTS_ENCODED
        }

        private fun decodeKey(encoded: String): NavKey = when {
            encoded == REPORTS_ENCODED -> ReportsKey
            encoded == PERMISSION_ENCODED -> PermissionKey
            encoded.startsWith(VIEWER_PREFIX) && encoded.length > VIEWER_PREFIX.length ->
                ReportViewerKey(encoded.removePrefix(VIEWER_PREFIX))
            else -> ReportsKey
        }
    }
}

/** Remembers an [AppNavigator] across configuration changes and process death. */
@Composable
fun rememberAppNavigator(): AppNavigator = rememberSaveable(saver = AppNavigator.Saver) {
    AppNavigator(listOf(ReportsKey))
}
