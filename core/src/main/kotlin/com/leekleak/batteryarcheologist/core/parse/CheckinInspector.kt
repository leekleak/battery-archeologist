package com.leekleak.batteryarcheologist.core.parse

import com.leekleak.batteryarcheologist.core.model.CheckinSnapshot

/**
 * Fast structural inspection of a `dumpsys batterystats -c` / checkin capture.
 *
 * This is deliberately independent from the full Battery Historian parser so the
 * app can validate captures on device before the ported parser grows.
 */
object CheckinInspector {
    private val versionRe = Regex("""^9,\d+,[a-z],vers,(\d+),""")
    private val historyRe = Regex("""^9,h,""")
    private val historyStringPoolRe = Regex("""^9,hsp,""")
    private val resetRe = Regex(""":RESET:TIME:""")
    private val batteryRe = Regex("""^9,\d+,[a-z],bt,""")

    fun inspect(raw: String): CheckinSnapshot {
        var version: Int? = null
        var total = 0
        var history = 0
        var historyStringPool = 0
        var resets = 0
        var hasBattery = false
        val preview = ArrayList<String>(12)

        raw.lineSequence().forEach { line ->
            total++
            if (preview.size < 12 && line.isNotBlank()) preview += line.take(160)
            if (version == null) {
                versionRe.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { version = it }
            }
            if (historyRe.containsMatchIn(line)) history++
            if (historyStringPoolRe.containsMatchIn(line)) historyStringPool++
            if (resetRe.containsMatchIn(line)) resets++
            if (batteryRe.containsMatchIn(line)) hasBattery = true
        }

        return CheckinSnapshot(
            checkinVersion = version,
            totalLines = total,
            historyLines = history,
            historyStringPoolLines = historyStringPool,
            resetEvents = resets,
            hasBatteryTime = hasBattery,
            previewLines = preview,
        )
    }
}
