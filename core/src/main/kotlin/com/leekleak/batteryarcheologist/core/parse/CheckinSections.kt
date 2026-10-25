package com.leekleak.batteryarcheologist.core.parse

/**
 * Lightweight extraction of the history portion of a `dumpsys batterystats -c`
 * capture. The full parser will consume this block, but keeping it here makes
 * the capture path testable and independent from the ported state machine.
 */
object CheckinSections {
    private const val VERSION_PREFIX = "9,"
    private const val HISTORY = ",h,"
    private const val STRING_POOL = ",hsp,"

    fun version(raw: String): Int? = raw.lineSequence()
        .firstOrNull { it.startsWith("9,") && it.contains(",i,vers,") }
        ?.split(',')
        ?.getOrNull(4)
        ?.toIntOrNull()

    fun historyLines(raw: String): List<String> = raw.lineSequence()
        .filter { line ->
            line.startsWith(VERSION_PREFIX) &&
                (line.contains(HISTORY) || line.contains(STRING_POOL))
        }
        .toList()

}
