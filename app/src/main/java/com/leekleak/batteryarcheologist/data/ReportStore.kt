package com.leekleak.batteryarcheologist.data

import android.content.Context
import com.leekleak.batteryarcheologist.core.model.CheckinSnapshot
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Properties

/**
 * Stores raw captures on the device so parsed views can be rebuilt later and
 * reports can be exported without another privileged read.
 */
class ReportStore(private val context: Context) {
    private val reportsDir: File
        get() = File(context.filesDir, "reports").apply { mkdirs() }

    fun saveQuickCapture(raw: String, snapshot: CheckinSnapshot): File {
        val stamp = FILE_STAMP.format(Instant.now())
        val rawFile = File(reportsDir, "$stamp-quick.txt")
        rawFile.writeText(raw, Charsets.UTF_8)

        val meta = Properties().apply {
            setProperty("capturedAt", Instant.now().toString())
            setProperty("source", "quick-checkin")
            setProperty("checkinVersion", snapshot.checkinVersion?.toString() ?: "")
            setProperty("totalLines", snapshot.totalLines.toString())
            setProperty("historyLines", snapshot.historyLines.toString())
            setProperty("historyStringPoolLines", snapshot.historyStringPoolLines.toString())
            setProperty("resetEvents", snapshot.resetEvents.toString())
            setProperty("sizeBytes", raw.toByteArray(Charsets.UTF_8).size.toString())
        }
        File(reportsDir, "$stamp-quick.properties").outputStream().use { meta.store(it, "Battery Archeologist capture") }
        return rawFile
    }

    fun saveImported(raw: String, originalName: String): File {
        val stamp = FILE_STAMP.format(Instant.now())
        val rawFile = File(reportsDir, "$stamp-import.txt")
        rawFile.writeText(raw, Charsets.UTF_8)
        val meta = Properties().apply {
            setProperty("capturedAt", Instant.now().toString())
            setProperty("source", "imported")
            setProperty("originalName", originalName)
            setProperty("sizeBytes", raw.toByteArray(Charsets.UTF_8).size.toString())
        }
        File(reportsDir, "$stamp-import.properties").outputStream().use { meta.store(it, "Battery Archeologist import") }
        return rawFile
    }

    fun list(): List<File> = reportsDir.listFiles()
        ?.filter { it.isFile && it.name.endsWith(".txt") }
        ?.sortedByDescending { it.name }
        ?: emptyList()

    fun read(name: String): String? {
        val file = File(reportsDir, name)
        if (!file.isFile || file.parentFile?.canonicalFile != reportsDir.canonicalFile) return null
        return file.readText(Charsets.UTF_8)
    }

    fun file(name: String): File? {
        val file = File(reportsDir, name)
        if (!file.isFile || file.parentFile?.canonicalFile != reportsDir.canonicalFile) return null
        return file
    }

    companion object {
        private val FILE_STAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
    }
}
