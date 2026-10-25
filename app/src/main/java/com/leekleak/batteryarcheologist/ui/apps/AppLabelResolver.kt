package com.leekleak.batteryarcheologist.ui.apps

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves installed-app labels from the local [PackageManager] and caches
 * them for the app session.
 *
 * Icons are deliberately not loaded here; Coil fetches them per row so only
 * visible icons are decoded. Packages that are not installed on this device
 * are simply absent from the returned map and use a package-name fallback.
 */
class AppLabelResolver(context: Context) {
    private val packageManager = context.applicationContext.packageManager
    private val cache = ConcurrentHashMap<String, String>()

    suspend fun resolve(packageNames: Collection<String>): Map<String, String> =
        withContext(Dispatchers.IO) {
            buildMap {
                packageNames
                    .filter { it.isNotBlank() }
                    .distinct()
                    .forEach { packageName ->
                        val label = cache[packageName] ?: loadLabel(packageName)
                            .orEmpty()
                            .also { cache[packageName] = it }
                        if (label.isNotBlank()) put(packageName, label)
                    }
            }
        }

    private fun loadLabel(packageName: String): String? {
        val info = applicationInfo(packageName) ?: return null
        return runCatching {
            packageManager.getApplicationLabel(info)
                .toString()
                .takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun applicationInfo(packageName: String) =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0),
                )
            } else {
                packageManager.getApplicationInfo(packageName, 0)
            }
        }.getOrNull()
}

/**
 * Readable fallback when no installed package matches.
 *
 * Package names stay untouched. Pseudo-apps from battery stats such as
 * `ANDROID_SYSTEM` become `Android System`, while single-word entries such as
 * `SHELL` keep their original spelling.
 */
internal fun fallbackAppName(packageName: String?): String {
    val name = packageName?.trim().orEmpty()
    if (name.contains('.')) return name
    val parts = name.split('_')
    if (parts.size == 1) return name
    return parts.joinToString(" ") { part ->
        when (part) {
            "UI", "CPU", "GPU", "NFC", "GPS", "ID", "IP", "SIM", "USB" -> part
            else -> part.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
        }
    }
}
