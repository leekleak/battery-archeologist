package com.leekleak.batteryarcheologist.data

import android.content.Context
import com.leekleak.batteryarcheologist.core.model.CheckinSnapshot
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Properties

/**
 * A saved raw report and the metadata stored beside it.
 *
 * [source] and [originalName] come from the sibling `.properties` file and are
 * null for older captures that predate metadata or when the file is unreadable.
 */
data class SavedReport(
    val name: String,
    val capturedAt: Instant?,
    val source: String?,
    val originalName: String?,
    val sizeBytes: Long,
)

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

    /**
     * Lists raw reports, newest first, using the sibling metadata when present
     * and falling back to the file timestamp for legacy captures.
     */
    fun listReports(): List<SavedReport> = reportsDir.listFiles()
        ?.filter { it.isFile && it.name.endsWith(".txt") }
        ?.mapNotNull { toSavedReport(it) }
        ?.sortedWith(
            compareByDescending<SavedReport> { it.capturedAt ?: Instant.EPOCH }
                .thenByDescending { it.name },
        )
        ?: emptyList()

    /** Metadata for one saved report, or null when [name] is not a safe report file. */
    fun metadata(name: String): SavedReport? {
        val file = resolve(name) ?: return null
        return toSavedReport(file)
    }

    /**
     * Deletes a raw report and its metadata.
     *
     * Uses the same canonical-path checks as [read] and [file], so names such as
     * `../other/file.txt` cannot escape the reports directory.
     *
     * @return true when the raw report existed and was deleted.
     */
    fun delete(name: String): Boolean {
        val rawFile = resolve(name) ?: return false
        val metaFile = metadataFile(rawFile)
        val deleted = rawFile.delete()
        if (deleted) metaFile.delete()
        return deleted
    }

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

    private fun resolve(name: String): File? {
        val file = File(reportsDir, name)
        if (!file.isFile || file.parentFile?.canonicalFile != reportsDir.canonicalFile) return null
        return file
    }

    private fun toSavedReport(file: File): SavedReport? {
        if (!file.isFile) return null
        val properties = readMetadata(file)
        val capturedAt = properties?.getProperty("capturedAt")
            ?.let { value -> runCatching { Instant.parse(value) }.getOrNull() }
            ?: runCatching { Instant.ofEpochMilli(file.lastModified()) }.getOrNull()
        return SavedReport(
            name = file.name,
            capturedAt = capturedAt,
            source = properties?.getProperty("source"),
            originalName = properties?.getProperty("originalName"),
            sizeBytes = properties?.getProperty("sizeBytes")?.toLongOrNull() ?: file.length(),
        )
    }

    private fun readMetadata(rawFile: File): Properties? {
        val metaFile = metadataFile(rawFile)
        if (!metaFile.isFile) return null
        return runCatching {
            Properties().apply { metaFile.inputStream().use { load(it) } }
        }.getOrNull()
    }

    private fun metadataFile(rawFile: File): File {
        val baseName = rawFile.name.substringBeforeLast('.', rawFile.name)
        return File(rawFile.parentFile, "$baseName.properties")
    }

    companion object {
        private val FILE_STAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
    }
}
