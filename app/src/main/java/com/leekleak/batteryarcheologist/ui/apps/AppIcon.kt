package com.leekleak.batteryarcheologist.ui.apps

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import coil3.ImageLoader
import coil3.asImage
import coil3.compose.SubcomposeAsyncImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options

/** Coil model for an installed package's launcher icon. */
internal data class AppIconRequest(val packageName: String)

/**
 * Loads an app icon through the [android.content.pm.PackageManager] inside
 * Coil's asynchronous pipeline.
 */
internal class AppIconFetcher private constructor(
    private val packageName: String,
    private val context: Context,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        return ImageFetchResult(
            image = drawable.asImage(),
            isSampled = false,
            dataSource = DataSource.DISK,
        )
    }

    internal class Factory : Fetcher.Factory<AppIconRequest> {
        override fun create(
            data: AppIconRequest,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = AppIconFetcher(data.packageName, options.context)
    }
}

/** Memory-cache key for [AppIconRequest] values. */
internal class AppIconKeyer : Keyer<AppIconRequest> {
    override fun key(data: AppIconRequest, options: Options): String =
        "app-icon:${data.packageName}"
}

/**
 * Square app icon loaded asynchronously by Coil.
 *
 * While the icon is loading, or when the package is not installed locally, a
 * monogram tile keeps the row height stable.
 */
@Composable
internal fun AppIcon(
    packageName: String,
    label: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 28)
    val iconModifier = modifier
        .size(size)
        .clip(shape)

    // Pseudo-apps from battery stats (ROOT, SYSTEM_UI, ...) are not packages,
    // so skip a guaranteed-failing PackageManager lookup.
    if (isPseudoAppName(packageName)) {
        AppIconMonogram(label = label, modifier = iconModifier)
        return
    }

    SubcomposeAsyncImage(
        model = AppIconRequest(packageName),
        contentDescription = null,
        modifier = iconModifier,
        contentScale = ContentScale.Crop,
        loading = {
            AppIconMonogram(label = label, modifier = Modifier.fillMaxSize())
        },
        error = {
            AppIconMonogram(label = label, modifier = Modifier.fillMaxSize())
        },
    )
}

@Composable
private fun AppIconMonogram(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** All-caps, dotless names such as `ANDROID_SYSTEM` cannot be package names. */
private fun isPseudoAppName(name: String): Boolean =
    name.isNotBlank() &&
        !name.contains('.') &&
        name.all { it.isUpperCase() || it.isDigit() || it == '_' }
