// Copyright 2016 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// Kotlin port of third_party/battery-historian/bugreportutils/bugreportutils.go.
//
// Differences from the Go implementation, all intentional:
// - File type detection uses zip/gzip magic bytes plus a control-byte heuristic
//   instead of net/http content sniffing; gzip archives are accepted as well.
// - Zip entries are visited in archive order instead of Go map order, so bug report
//   extraction is deterministic.
// - Parse failures are thrown as BugReportException instead of returned alongside a
//   partially populated result.
// - Timestamps use java.time. The upstream Go layout "2006-01-02 15:04:05" is
//   represented by the Java pattern "uuuu-MM-dd HH:mm:ss".

package com.leekleak.batteryarcheologist.core.bugreport

import com.leekleak.batteryarcheologist.core.historianutils.HistorianUtils
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.zip.ZipInputStream

/** Thrown when a bug report or checkin cannot be read or parsed. */
class BugReportException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Basic information about a device's sensor.
 *
 * [totalTimeMs] and [count] are populated by the timeline parser, not by the bug
 * report metadata parser.
 */
data class SensorInfo(
    val name: String = "",
    val type: String = "",
    val version: Int = 0,
    val number: Int = 0,
    val totalTimeMs: Long = 0L,
    val count: Float = 0f,
)

/** Metadata about the device being analyzed. */
data class MetaInfo(
    val deviceId: String,
    val sdkVersion: Int,
    val buildFingerprint: String,
    val modelName: String,
    val sensors: Map<Int, SensorInfo>,
)

/**
 * The name and UID for an app, as parsed from the bug report's PID mappings.
 *
 * This is a local adapter type: unlike PackageInfoModel it keeps the raw UID string,
 * because bug reports contain abbreviated or invalid UIDs that the parser reports as
 * warnings instead of failing the whole report.
 */
data class AppInfo(
    val name: String,
    val uid: String = "",
)

/** Result of [BugreportUtils.extractPIDMappings]. */
data class PidMappings(
    val mappings: Map<String, List<AppInfo>>,
    val warnings: List<String> = emptyList(),
)

/** A bug report extracted from a text, zip, or gzip file. */
data class ExtractedBugReport(
    val contents: String,
    val fileName: String,
)

/**
 * Common bug report parsing functions.
 *
 * Ported from the Go `bugreportutils` package. The sensor extraction understands both
 * the legacy (MNC-and-before) and the NRD42+ sensorservice dump formats.
 */
object BugreportUtils {
    /** Hard-coded sensor number for GPS in `android.os.BatteryStats.Sensor`. */
    const val GPS_SENSOR_NUMBER = -10000

    /**
     * Timestamp pattern commonly printed in bug reports.
     *
     * Upstream Go uses the reference layout "2006-01-02 15:04:05"; this is its
     * java.time equivalent.
     */
    const val TIME_LAYOUT = "uuuu-MM-dd HH:mm:ss"

    /** Matches the beginning of a bug report section. */
    val bugReportSectionRe = Regex("""------\s+(?<section>.*)\s+-----""")

    /** Matches the time information from the dumpstate line at the start of a bug report. */
    val dumpstateRe = Regex("""==\sdumpstate:\s(?<timestamp>\d+-\d+-\d+\s\d+:\d+:\d+)""")

    /** Matches the timezone string in a bug report. */
    val timeZoneRe = Regex("""^\[persist.sys.timezone\]:\s+\[(?<timezone>\S+)\]""")

    private val deviceIdRe = Regex("""DeviceID: (?<deviceID>[0-9]+)""")
    private val sdkVersionRe = Regex("""\[ro.build.version.sdk\]:\s+\[(?<sdkVersion>\d+)\]""")
    private val buildFingerprintRe = Regex("""Build\s+fingerprint:\s+'(?<build>\S+)'""")
    private val modelNameRe = Regex("""\[ro.product.model\]:\s+\[(?<modelName>.*)\]""")
    private val pidRe = Regex("""PID #(?<pid>\d+): ProcessRecord[^:]+:(?<app>[^/]+)/(?<uid>.*)}""")

    // Sensor list line in the sensorservice dump of a bug report from MNC or before.
    private val sensorLineLegacyRe = Regex(
        """(?<sensorName>[^|]+)\|(?<sensorManufacturer>[^|]+)\|""" +
            """(?:\s*version=(?<versionNumber>\d+)\s*\|)?\s*(?<sensorTypeString>[^|]+)""" +
            """\|\s*(?<sensorNumber>0x[0-9A-Fa-f]+)""",
    )

    // Sensor list line in the sensorservice dump starting from NRD42 and onwards.
    private val sensorLineModernRe = Regex(
        """(?<sensorNumber>0x?[0-9A-Fa-f]+)\)\s*""" +
            """(?<sensorName>[^|]+)\s*\|(?<sensorManufacturer>[^|]+)\|\s*ver:\s*""" +
            """(?<versionNumber>\d+)\s*\|\s*type:\s*(?<sensorTypeString>[^(]+)\(\d+\)\s*\|""",
    )

    private val abbreviatedUidRe = Regex("""u(?<userId>\d+)(?<aidType>[ias])(?<appId>\d+)""")

    private val timestampWithFractionRe =
        Regex("""^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})(?:\.(\d+))?$""")

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern(TIME_LAYOUT).withResolverStyle(ResolverStyle.STRICT)

    private val utc: ZoneId = ZoneId.of("UTC")

    /**
     * Returns a map of the contents of each file from [bytes], with the key being the
     * file name.
     *
     * Supported formats are text/plain, application/zip and gzip. For zipped files,
     * each file name is prefixed with the zip file's name and a `~`.
     *
     * @throws BugReportException if the format is not recognized.
     */
    fun contents(fileName: String, bytes: ByteArray): Map<String, ByteArray> =
        when (detectFileKind(bytes)) {
            FileKind.TEXT -> mapOf(fileName to bytes)
            FileKind.ZIP -> unzipAndExtract(fileName, bytes)
            FileKind.GZIP -> mapOf(fileName to decompressGzip(bytes))
            FileKind.UNKNOWN ->
                throw BugReportException("incorrect file format detected: \"application/octet-stream\"")
        }

    /** Returns true if [bytes] looks like a bug report. */
    fun isBugReport(bytes: ByteArray): Boolean = isBugReport(bytes.toString(Charsets.UTF_8))

    /** Returns true if [report] looks like a bug report. */
    fun isBugReport(report: String): Boolean =
        dumpstateRe.containsMatchIn(report) &&
            buildFingerprintRe.containsMatchIn(report) &&
            bugReportSectionRe.containsMatchIn(report)

    /**
     * Extracts the device ID, build fingerprint, model name, SDK version and sensors
     * from the bug report.
     *
     * @throws BugReportException if the SDK version cannot be found or a sensor line
     *   cannot be parsed.
     */
    fun parseMetaInfo(input: String): MetaInfo {
        var deviceId = ""
        var buildFingerprint = ""
        var modelName = ""
        var sdkVersion = -1

        for (line in input.lineSequence()) {
            val deviceIdMatch = HistorianUtils.subexpNames(deviceIdRe, line)
            val sdkVersionMatch = HistorianUtils.subexpNames(sdkVersionRe, line)
            val buildFingerprintMatch = HistorianUtils.subexpNames(buildFingerprintRe, line)
            val modelNameMatch = HistorianUtils.subexpNames(modelNameRe, line)

            when {
                deviceIdMatch != null -> deviceId = deviceIdMatch["deviceID"].orEmpty()

                sdkVersionMatch != null -> {
                    val value = sdkVersionMatch["sdkVersion"].orEmpty()
                    sdkVersion = value.toIntOrNull()
                        ?: throw BugReportException("error parsing SDK version: $value")
                }

                // Only the first instance of this line in the bug report is guaranteed
                // to be correct. All following instances may be wrong, so ignore them.
                buildFingerprintMatch != null -> {
                    if (buildFingerprint.isEmpty()) {
                        buildFingerprint = buildFingerprintMatch["build"].orEmpty()
                    }
                }

                modelNameMatch != null -> modelName = modelNameMatch["modelName"].orEmpty()
            }

            if (deviceId.isNotEmpty() && buildFingerprint.isNotEmpty() &&
                sdkVersion != -1 && modelName.isNotEmpty()
            ) {
                break
            }
        }

        if (sdkVersion == -1) {
            throw BugReportException("unable to find device SDK version")
        }

        return MetaInfo(
            deviceId = deviceId.ifEmpty { "not available" },
            sdkVersion = sdkVersion,
            buildFingerprint = buildFingerprint,
            modelName = modelName.ifEmpty { "unknown device" },
            sensors = extractSensorInfo(input),
        )
    }

    /**
     * Extracts device sensor information found in the sensorservice dump of a bug
     * report.
     *
     * Both the MNC-and-earlier and NRD42+ line formats are recognized. Parsing stops
     * at the first service dump after sensorservice, matching upstream.
     */
    private fun extractSensorInfo(input: String): Map<Int, SensorInfo> {
        var inSensorsSection = false
        val sensors = LinkedHashMap<Int, SensorInfo>()

        loop@ for (line in input.lineSequence()) {
            val service = HistorianUtils.subexpNames(HistorianUtils.serviceDumpRe, line)
            if (service != null) {
                val isSensorsService = service["service"] == "sensorservice"
                when {
                    inSensorsSection && !isSensorsService -> break@loop
                    isSensorsService -> {
                        inSensorsSection = true
                        continue@loop
                    }
                    else -> continue@loop
                }
            }
            if (!inSensorsSection) continue@loop

            val result = HistorianUtils.subexpNames(sensorLineLegacyRe, line)
                ?: HistorianUtils.subexpNames(sensorLineModernRe, line)
                ?: continue@loop

            val numberText = result["sensorNumber"].orEmpty()
            val number = try {
                parseInt32(numberText)
            } catch (e: NumberFormatException) {
                throw BugReportException("error parsing sensor number: $numberText", e)
            }

            val versionText = result["versionNumber"].orEmpty()
            val version = if (versionText.isEmpty()) {
                0
            } else {
                versionText.toIntOrNull()
                    ?: throw BugReportException("error parsing sensor version: $versionText")
            }

            sensors[number] = SensorInfo(
                name = result["sensorName"].orEmpty(),
                type = result["sensorTypeString"].orEmpty(),
                version = version,
                number = number,
            )
        }

        // The GPS sensor is not listed by sensorservice, but battery stats use this
        // hard-coded number for it.
        sensors[GPS_SENSOR_NUMBER] = SensorInfo(name = "GPS", number = GPS_SENSOR_NUMBER)
        return sensors
    }

    /**
     * Extracts and returns only the lines in [input] that are included in the
     * "CHECKIN BATTERYSTATS" section. Lines are trimmed, as upstream does.
     */
    fun extractBatterystatsCheckin(input: String): String {
        var inBatterystatsSection = false
        val checkinLines = ArrayList<String>()

        loop@ for (rawLine in input.lineSequence()) {
            val line = rawLine.trim()
            val section = HistorianUtils.subexpNames(bugReportSectionRe, line)
            if (section != null) {
                val isBatterystats = section["section"]?.contains("CHECKIN BATTERYSTATS") == true
                when {
                    inBatterystatsSection && !isBatterystats -> break@loop
                    isBatterystats -> {
                        inBatterystatsSection = true
                        continue@loop
                    }
                    else -> continue@loop
                }
            }
            if (inBatterystatsSection) {
                checkinLines += line
            }
        }

        return checkinLines.joinToString("\n")
    }

    /**
     * Extracts and returns the first valid bug report found in [bytes], along with the
     * determined file name.
     *
     * @throws BugReportException if no file in [bytes] is a valid bug report.
     */
    fun extractBugReport(fileName: String, bytes: ByteArray): ExtractedBugReport {
        val files = contents(fileName, bytes)
        for ((name, data) in files) {
            if (isBugReport(data)) {
                return ExtractedBugReport(contents = data.toString(Charsets.UTF_8), fileName = name)
            }
        }
        throw BugReportException("$fileName did not contain a valid bug report")
    }

    /**
     * Returns mappings from PID to app names and UIDs extracted from the bug report,
     * plus any warnings about invalid UIDs.
     */
    fun extractPIDMappings(contents: String): PidMappings {
        val mappings = LinkedHashMap<String, MutableList<AppInfo>>()
        val warnings = ArrayList<String>()

        for (line in contents.lineSequence()) {
            val result = HistorianUtils.subexpNames(pidRe, line) ?: continue
            val pid = result["pid"].orEmpty()
            val name = result["app"].orEmpty()
            val rawUid = result["uid"].orEmpty()
            val uid = appIdFromString(rawUid)
            if (uid == null) {
                warnings += "invalid uid: $rawUid"
                mappings.getOrPut(pid) { ArrayList() } += AppInfo(name = name)
            } else {
                mappings.getOrPut(pid) { ArrayList() } += AppInfo(name = name, uid = uid.toString())
            }
        }

        return PidMappings(mappings.mapValues { it.value.toList() }, warnings)
    }

    /**
     * Converts a timestamp in [TIME_LAYOUT], combined with the fraction of a second,
     * to a Unix ms timestamp based on [zone].
     *
     * The fraction is passed separately, e.g. for `2015-05-28 19:50:27.636636` the
     * timestamp is `2015-05-28 19:50:27` and the remainder is `636636`. Any fractional
     * seconds embedded in [timestamp] are accepted (as Go's time.Parse does) but only
     * its whole seconds are used; [remainder] supplies the milliseconds.
     *
     * @throws IllegalArgumentException if [zone] is null.
     */
    fun timeStampToMs(timestamp: String, remainder: String, zone: ZoneId?): Long {
        val zoneId = requireNotNull(zone) { "missing location" }
        val match = timestampWithFractionRe.matchEntire(timestamp)
            ?: throw DateTimeParseException("invalid timestamp: $timestamp", timestamp, 0)
        val wallTime = LocalDateTime.parse(match.groupValues[1], timeFormatter)
        return wallTime.atZone(zoneId).toEpochSecond() * 1000 + secFractionAsMs(remainder)
    }

    /**
     * Converts the fraction of a second to milliseconds, e.g. `123456` from
     * `27.123456` corresponds to 123 ms (and 27 seconds). Only the leading three
     * digits are used; shorter fractions are padded with zeros.
     */
    fun secFractionAsMs(fraction: String): Long {
        val padded = fraction + "000"
        val milliseconds = padded.take(3)
        return milliseconds.toLongOrNull()
            ?: throw NumberFormatException("invalid second fraction: $fraction")
    }

    /**
     * Extracts the time zone from a bug report.
     *
     * If the timezone line is missing, UTC is returned: the phone was likely just
     * reset and everything is in UTC.
     *
     * @throws BugReportException if the timezone line names an unknown zone.
     */
    fun timeZone(contents: String): ZoneId {
        for (line in contents.lineSequence()) {
            val result = HistorianUtils.subexpNames(timeZoneRe, line) ?: continue
            val timezone = result["timezone"].orEmpty()
            return try {
                ZoneId.of(timezone)
            } catch (e: DateTimeException) {
                throw BugReportException("unknown time zone $timezone", e)
            }
        }
        return utc
    }

    /**
     * Returns the parsed dumpstate information as a zoned wall time.
     *
     * @throws BugReportException if the timezone is invalid or no dumpstate line is
     *   present.
     */
    fun dumpState(contents: String): ZonedDateTime {
        val zone = timeZone(contents)
        for (line in contents.lineSequence()) {
            val result = HistorianUtils.subexpNames(dumpstateRe, line) ?: continue
            val timestamp = result["timestamp"].orEmpty().trim()
            return LocalDateTime.parse(timestamp, timeFormatter).atZone(zone)
        }
        throw BugReportException("could not find dumpstate information in bugreport")
    }

    private fun decompressGzip(bytes: ByteArray): ByteArray = try {
        HistorianUtils.gzipDecompress(bytes)
    } catch (e: IOException) {
        throw BugReportException("failed to decompress gzip file: ${e.message}", e)
    }

    private fun unzipAndExtract(fileName: String, bytes: ByteArray): Map<String, ByteArray> {
        if (!isZip(bytes)) {
            throw BugReportException("failed to open ZIP file: not a ZIP archive")
        }
        val files = LinkedHashMap<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val data = try {
                        zip.readBytes()
                    } catch (e: IOException) {
                        throw BugReportException("error copying from ZIP file: ${e.message}", e)
                    }
                    // Don't recursively extract sub-ZIP files; this is also used for
                    // .jar files elsewhere upstream.
                    files[fileName + "~" + entry.name] = data
                    zip.closeEntry()
                }
            }
        } catch (e: BugReportException) {
            throw e
        } catch (e: IOException) {
            throw BugReportException("failed to open ZIP file: ${e.message}", e)
        }
        return files
    }

    private enum class FileKind { TEXT, ZIP, GZIP, UNKNOWN }

    private fun detectFileKind(bytes: ByteArray): FileKind = when {
        isZip(bytes) -> FileKind.ZIP
        startsWith(bytes, GZIP_HEADER) -> FileKind.GZIP
        looksLikeText(bytes) -> FileKind.TEXT
        else -> FileKind.UNKNOWN
    }

    private fun isZip(bytes: ByteArray): Boolean =
        startsWith(bytes, ZIP_LOCAL_HEADER) ||
            startsWith(bytes, ZIP_EMPTY_HEADER) ||
            startsWith(bytes, ZIP_SPANNED_HEADER)

    /**
     * Approximation of Go's text sniffing: text does not contain binary control
     * bytes. UTF-16 BOMs are treated as text.
     */
    private fun looksLikeText(bytes: ByteArray): Boolean {
        if (startsWith(bytes, UTF16_LE_BOM) || startsWith(bytes, UTF16_BE_BOM)) return true
        val limit = minOf(bytes.size, 512)
        for (i in 0 until limit) {
            val value = bytes[i].toInt() and 0xFF
            when {
                value <= 0x08 -> return false
                value == 0x0B -> return false
                value in 0x0E..0x1A -> return false
                value in 0x1C..0x1F -> return false
            }
        }
        return true
    }

    private fun startsWith(bytes: ByteArray, prefix: IntArray): Boolean {
        if (bytes.size < prefix.size) return false
        return prefix.indices.all { (bytes[it].toInt() and 0xFF) == prefix[it] }
    }

    /**
     * Port of Go's `packageutils.AppIDFromString`: returns the app ID (base UID),
     * stripping the user ID, or `null` if the value is invalid.
     */
    private fun appIdFromString(uid: String): Int? {
        // The empty string is a valid/expected value to pass through here.
        if (uid.isEmpty()) return 0

        val result = HistorianUtils.subexpNames(abbreviatedUidRe, uid)
        if (result != null) {
            val appId = result["appId"].orEmpty().toIntOrNull() ?: return null
            return when (result["aidType"]) {
                "i" -> appId + FIRST_ISOLATED_UID
                "a" -> appId + FIRST_APPLICATION_UID
                "s" -> appId
                else -> null
            }
        }

        val value = uid.toIntOrNull() ?: return null
        return appId(value)
    }

    /** Port of Go's `packageutils.AppID`. */
    private fun appId(uid: Int): Int {
        val userScoped = uid % PER_USER_RANGE
        return if (userScoped in FIRST_SHARED_APPLICATION_GID..LAST_SHARED_APPLICATION_GID) {
            userScoped + FIRST_APPLICATION_UID - FIRST_SHARED_APPLICATION_GID
        } else {
            userScoped
        }
    }

    /**
     * Port of `strconv.ParseInt(value, 0, 32)`: accepts 0x/0b/0o prefixes and
     * leading-zero octal, as Go's base-0 parsing does.
     */
    private fun parseInt32(value: String): Int {
        var text = value
        var negative = false
        when {
            text.startsWith("-") -> {
                negative = true
                text = text.substring(1)
            }
            text.startsWith("+") -> text = text.substring(1)
        }
        val radix = when {
            text.length > 2 && text.startsWith("0x", ignoreCase = true) -> {
                text = text.substring(2)
                16
            }
            text.length > 2 && text.startsWith("0b", ignoreCase = true) -> {
                text = text.substring(2)
                2
            }
            text.length > 2 && text.startsWith("0o", ignoreCase = true) -> {
                text = text.substring(2)
                8
            }
            text.length > 1 && text.startsWith("0") -> 8
            else -> 10
        }
        val magnitude = text.toLongOrNull(radix) ?: throw NumberFormatException("invalid number: $value")
        val parsed = if (negative) -magnitude else magnitude
        if (parsed < Int.MIN_VALUE || parsed > Int.MAX_VALUE) {
            throw NumberFormatException("value out of range: $value")
        }
        return parsed.toInt()
    }

    private const val PER_USER_RANGE = 100_000

    /** Start of the UID range reserved for applications. */
    private const val FIRST_APPLICATION_UID = 10_000

    /** First UID used for fully isolated sandboxed processes. */
    private const val FIRST_ISOLATED_UID = 99_000

    /** First gid for applications to share resources. */
    private const val FIRST_SHARED_APPLICATION_GID = 50_000

    /** Last gid for applications to share resources. */
    private const val LAST_SHARED_APPLICATION_GID = 59_999

    private val ZIP_LOCAL_HEADER = intArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val ZIP_EMPTY_HEADER = intArrayOf(0x50, 0x4B, 0x05, 0x06)
    private val ZIP_SPANNED_HEADER = intArrayOf(0x50, 0x4B, 0x07, 0x08)
    private val GZIP_HEADER = intArrayOf(0x1F, 0x8B)
    private val UTF16_LE_BOM = intArrayOf(0xFF, 0xFE)
    private val UTF16_BE_BOM = intArrayOf(0xFE, 0xFF)
}
