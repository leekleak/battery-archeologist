package com.leekleak.batteryarcheologist.ui.timeline

import com.leekleak.batteryarcheologist.core.model.TimelineEntry

/**
 * Deterministic timeline data used by previews and manual UI checks.
 *
 * The fixture spans four hours at a fixed epoch, so labels, lanes, and the
 * report marker are identical on every run and on every device.
 */
data class TimelineFixture(
    val title: String,
    val entries: List<TimelineEntry>,
    val batteryEntries: List<TimelineEntry>,
    val reportTakenMs: Long,
)

object TimelineFixtures {
    /** 2023-11-14T22:13:20Z. Fixed so screenshots are reproducible. */
    const val EPOCH_MS: Long = 1_700_000_000_000L

    const val DURATION_MS: Long = 4L * 60L * 60L * 1_000L

    private const val MINUTE_MS: Long = 60_000L

    fun build(): TimelineFixture {
        val reportTakenMs = EPOCH_MS + DURATION_MS
        val batteryEntries = buildBattery()
        val metricEntries = buildList {
            addAll(screenEntries())
            addAll(wifiEntries())
            addAll(wakelockEntries())
            addAll(topAppEntries())
            addAll(chargingEntries())
            addAll(cpuEntries())
        }
        return TimelineFixture(
            title = "Battery timeline",
            entries = batteryEntries + metricEntries,
            batteryEntries = batteryEntries,
            reportTakenMs = reportTakenMs,
        )
    }

    /**
     * One sample every ten minutes. The battery discharges while unplugged and
     * recovers during the 90–140 minute charging window.
     */
    private fun buildBattery(): List<TimelineEntry> {
        val points = ArrayList<TimelineEntry>(25)
        var levelTenths = 880
        var minute = 0
        while (minute <= 240) {
            points.add(
                bar(
                    metric = "Battery Level",
                    value = (levelTenths / 10).toString(),
                    startMinute = minute,
                    endMinute = minute,
                    type = "int",
                ),
            )
            minute += 10
            if (minute <= 240) {
                levelTenths += if (minute in 90..140) 9 else -9
            }
        }
        return points
    }

    private fun screenEntries(): List<TimelineEntry> = listOf(
        bar("Screen", "on", 0, 14),
        bar("Screen", "off", 14, 32),
        bar("Screen", "on", 32, 55),
        bar("Screen", "off", 55, 78),
        bar("Screen", "on", 78, 96),
        bar("Screen", "off", 96, 112),
        bar("Screen", "on", 112, 128),
        bar("Screen", "off", 128, 150),
        bar("Screen", "on", 150, 168),
        bar("Screen", "off", 168, 188),
        bar("Screen", "on", 188, 210),
        bar("Screen", "off", 210, 240),
    )

    private fun wifiEntries(): List<TimelineEntry> = listOf(
        bar("Wifi on", "on", 0, 70),
        bar("Wifi on", "off", 70, 118),
        bar("Wifi on", "on", 118, 240),
    )

    private fun wakelockEntries(): List<TimelineEntry> = listOf(
        bar("Wakelock_in", "Audio mix", 6, 18, type = "string"),
        bar("Wakelock_in", "JobScheduler", 26, 31, type = "string"),
        bar("Wakelock_in", "Sync", 30, 48, type = "string"),
        bar("Wakelock_in", "Location", 52, 58, type = "string"),
        bar("Wakelock_in", "Audio mix", 92, 105, type = "string"),
        bar("Wakelock_in", "Sync", 118, 170, type = "string"),
        bar("Wakelock_in", "Upload", 150, 156, type = "string"),
        bar("Wakelock_in", "Location", 200, 214, type = "string"),
    )

    private fun topAppEntries(): List<TimelineEntry> = listOf(
        bar("Top app", "com.leekleak.batteryarcheologist", 0, 12, type = "string"),
        bar("Top app", "com.android.chrome", 12, 34, type = "string"),
        bar("Top app", "com.google.android.youtube", 34, 46, type = "string"),
        bar("Top app", "com.android.chrome", 46, 72, type = "string"),
        bar("Top app", "com.leekleak.batteryarcheologist", 72, 88, type = "string"),
        bar("Top app", "com.google.android.youtube", 88, 104, type = "string"),
        bar("Top app", "com.android.chrome", 104, 126, type = "string"),
        bar("Top app", "com.google.android.youtube", 126, 140, type = "string"),
        bar("Top app", "com.android.chrome", 140, 166, type = "string"),
        bar("Top app", "com.leekleak.batteryarcheologist", 166, 180, type = "string"),
        bar("Top app", "com.google.android.youtube", 180, 210, type = "string"),
        bar("Top app", "com.android.chrome", 210, 240, type = "string"),
    )

    private fun chargingEntries(): List<TimelineEntry> = listOf(
        bar("Charging on", "usb", 90, 140, type = "string"),
        instant("Charging status", "plugged", 90, type = "string"),
        instant("Charging status", "unplugged", 140, type = "string"),
    )

    private fun cpuEntries(): List<TimelineEntry> = listOf(
        bar("CPU running", "running", 18, 29, type = "bool", opt = "JobScheduler"),
        bar("CPU running", "running", 84, 92, type = "bool", opt = "Alarm"),
        bar("CPU running", "running", 123, 143, type = "bool", opt = "Sync"),
        bar("CPU running", "running", 205, 219, type = "bool", opt = "Location"),
    )

    private fun bar(
        metric: String,
        value: String,
        startMinute: Int,
        endMinute: Int,
        type: String = "bool",
        opt: String = "",
    ): TimelineEntry = TimelineEntry(
        metric = metric,
        type = type,
        startMs = EPOCH_MS + startMinute * MINUTE_MS,
        endMs = EPOCH_MS + endMinute * MINUTE_MS,
        value = value,
        opt = opt,
    )

    private fun instant(
        metric: String,
        value: String,
        atMinute: Int,
        type: String = "string",
    ): TimelineEntry = TimelineEntry(
        metric = metric,
        type = type,
        startMs = EPOCH_MS + atMinute * MINUTE_MS,
        endMs = EPOCH_MS + atMinute * MINUTE_MS,
        value = value,
    )
}
