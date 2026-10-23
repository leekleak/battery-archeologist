package com.leekleak.batteryarcheologist.core.model

/**
 * State of the Shizuku binder as observed by the app.
 */
sealed interface ShizukuState {
    data object Unknown : ShizukuState

    /** Shizuku package is installed but no server is running. */
    data object NotRunning : ShizukuState

    /** The server is alive but this app has not been granted access. */
    data object PermissionDenied : ShizukuState

    /** The server is alive and this app is authorized. UID 2000 is ADB mode. */
    data class Ready(val uid: Int) : ShizukuState {
        val isAdb: Boolean get() = uid == 2000
        val isRoot: Boolean get() = uid == 0
    }
}
