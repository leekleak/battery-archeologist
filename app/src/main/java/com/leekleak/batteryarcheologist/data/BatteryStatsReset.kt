package com.leekleak.batteryarcheologist.data

/**
 * Interprets the output of `dumpsys batterystats --reset`.
 *
 * Exit 0 is not proof of success: some OEM builds exit 0 while printing a
 * rejection (permission denial or an unknown option). A non-zero exit is
 * treated as a failure. Callers should therefore treat a `null` result as
 * "no error was reported", not as proof that the counters are actually empty.
 *
 * @return a user-facing failure message, or `null` when the reset looked
 *   successful.
 */
internal fun batteryStatsResetError(
    exitCode: Int,
    stdout: String,
    stderr: String,
): String? {
    if (exitCode == TIMEOUT_EXIT_CODE) return TIMEOUT_MESSAGE

    if (exitCode != 0) {
        return firstNonBlankLine(stderr)
            ?: firstNonBlankLine(stdout)
            ?: "Reset failed with exit code $exitCode"
    }

    val outputs = listOf(stderr, stdout)
    val rejectionLine = outputs
        .asSequence()
        .flatMap { it.lineSequence() }
        .map { it.trim() }
        .firstOrNull { line ->
            line.isNotEmpty() && REJECTION_MARKERS.any { line.contains(it, ignoreCase = true) }
        }
    if (rejectionLine != null) return rejectionLine

    val rejected = outputs.any { output ->
        REJECTION_MARKERS.any { output.contains(it, ignoreCase = true) }
    }
    return if (rejected) "Reset failed: permission denied or reset unsupported" else null
}

private fun firstNonBlankLine(text: String): String? =
    text.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }

private const val TIMEOUT_EXIT_CODE = -2
private const val TIMEOUT_MESSAGE =
    "Reset timed out; battery stats may or may not have been cleared"
private val REJECTION_MARKERS = listOf("Permission Denial", "Unknown option", "Unknown command")
