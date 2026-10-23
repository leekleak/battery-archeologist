package com.leekleak.batteryarcheologist.core.model

data class ShellResult(
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
    val truncated: Boolean = false,
) {
    val succeeded: Boolean get() = exitCode == 0
}
