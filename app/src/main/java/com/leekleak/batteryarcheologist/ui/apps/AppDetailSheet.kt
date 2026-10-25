package com.leekleak.batteryarcheologist.ui.apps

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.core.analytics.AppPowerStat
import com.leekleak.batteryarcheologist.core.analytics.AppPowerStats
import com.leekleak.batteryarcheologist.data.AppUsage
import com.leekleak.batteryarcheologist.proto.batterystats.BatteryStats
import com.leekleak.batteryarcheologist.ui.components.FancyBottomSheet
import com.leekleak.batteryarcheologist.ui.components.FancySectionCard
import com.leekleak.batteryarcheologist.ui.components.FancySheetHeader
import com.leekleak.batteryarcheologist.ui.components.SummaryRow
import com.leekleak.batteryarcheologist.ui.components.formatByteCount
import com.leekleak.batteryarcheologist.ui.theme.BatteryArcheologistTheme

/**
 * Per-app detail shown from [AppsScreen] in a fancy bottom sheet.
 *
 * Battery capacity belongs to the System tab and is intentionally not repeated
 * here, though per-app mAh estimates are shown next to their percentages.
 */
@Composable
fun AppDetailSheet(
    onDismissRequest: () -> Unit,
    usage: AppUsage,
    displayLabel: String? = null,
    pinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
) {
    FancyBottomSheet(onDismissRequest = onDismissRequest) {
        AppDetailContent(
            usage = usage,
            displayLabel = displayLabel,
            pinned = pinned,
            onTogglePin = onTogglePin,
        )
    }
}

/**
 * Sheet body without the modal chrome: header with the app icon, title and pin
 * action, followed by one rounded card per metric group.
 */
@Composable
fun AppDetailContent(
    usage: AppUsage,
    displayLabel: String? = null,
    pinned: Boolean = false,
    onTogglePin: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val app = usage.app
    val power = usage.power
    val cpuUser = app.cpu?.user_time_ms ?: 0f
    val cpuSystem = app.cpu?.system_time_ms ?: 0f
    val mobileBytes = (app.network?.mobile_bytes_rx ?: 0f) +
        (app.network?.mobile_bytes_tx ?: 0f)
    val wifiBytes = (app.network?.wifi_bytes_rx ?: 0f) +
        (app.network?.wifi_bytes_tx ?: 0f)
    val unknownLabel = stringResource(R.string.label_unknown)
    val packageName = app.name.orEmpty()
    val title = displayLabel ?: fallbackAppName(app.name).ifBlank { unknownLabel }
    val subtitle = if (displayLabel == null && packageName.contains('.')) {
        appUidSubtitle(app)
    } else {
        appDetailSubtitle(app)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("app_detail"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppDetailHeader(
            packageName = packageName,
            title = title,
            subtitle = subtitle,
            pinned = pinned,
            onTogglePin = onTogglePin,
        )

        FancySectionCard(title = stringResource(R.string.apps_section_power)) {
            if (power != null && power.hasDevicePower) {
                SummaryRow(
                    label = stringResource(R.string.apps_device_power),
                    value = "${AppPowerStats.formatPercent(power.devicePowerPct)} · " +
                        formatMah(power.devicePowerMah),
                )
            } else {
                SummaryRow(
                    label = stringResource(R.string.apps_device_power),
                    value = stringResource(R.string.label_unavailable),
                )
            }
        }

        FancySectionCard(title = stringResource(R.string.apps_section_cpu)) {
            SummaryRow(
                stringResource(R.string.apps_cpu_total),
                formatSeconds(cpuUser + cpuSystem),
            )
            SummaryRow(stringResource(R.string.apps_cpu_user), formatSeconds(cpuUser))
            SummaryRow(stringResource(R.string.apps_cpu_system), formatSeconds(cpuSystem))
        }

        FancySectionCard(title = stringResource(R.string.apps_section_activity)) {
            SummaryRow(
                stringResource(R.string.apps_foreground),
                formatSeconds(app.foreground?.total_time_msec ?: 0f),
            )
            SummaryRow(
                stringResource(R.string.apps_wakeups),
                app.apk?.wakeups?.let(::formatCount) ?: "—",
            )
            SummaryRow(
                stringResource(R.string.apps_camera),
                formatSeconds(app.camera?.total_time_msec ?: 0f),
            )
            SummaryRow(
                stringResource(R.string.apps_audio),
                formatSeconds(app.audio?.total_time_msec ?: 0f),
            )
            SummaryRow(
                stringResource(R.string.apps_flashlight),
                formatSeconds(app.flashlight?.total_time_msec ?: 0f),
            )
        }

        FancySectionCard(title = stringResource(R.string.apps_section_network)) {
            SummaryRow(
                stringResource(R.string.apps_mobile_traffic),
                formatByteCount(mobileBytes),
            )
            SummaryRow(
                stringResource(R.string.apps_wifi_traffic),
                formatByteCount(wifiBytes),
            )
        }
    }
}

/** App icon, name, subtitle and the pin toggle at the top of the sheet. */
@Composable
private fun AppDetailHeader(
    packageName: String,
    title: String,
    subtitle: String,
    pinned: Boolean,
    onTogglePin: (() -> Unit)?,
) {
    val pinAction: (@Composable () -> Unit)? = onTogglePin?.let { toggle ->
        {
            IconButton(
                onClick = toggle,
                modifier = Modifier.testTag("app_detail_pin"),
            ) {
                Icon(
                    painter = painterResource(
                        if (pinned) {
                            R.drawable.keep_off
                        } else {
                            R.drawable.keep
                        },
                    ),
                    contentDescription = stringResource(
                        if (pinned) R.string.apps_unpin else R.string.apps_pin,
                    ),
                )
            }
        }
    }
    FancySheetHeader(
        title = title,
        subtitle = subtitle,
        leading = {
            AppIcon(
                packageName = packageName,
                label = title,
                size = 52.dp,
            )
        },
        trailing = pinAction,
    )
}

/** Deterministic fixture shared by the local app-row and detail previews. */
internal fun previewAppUsage(): AppUsage {
    val app = BatteryStats.App(
        name = "com.example.video",
        uid = 10123,
        version_name = "4.2.1",
        power_use_item = BatteryStats.App.PowerUseItem(computed_power_mah = 18.42f),
        cpu = BatteryStats.App.Cpu(
            user_time_ms = 324_500f,
            system_time_ms = 48_200f,
        ),
        foreground = BatteryStats.App.Foreground(
            total_time_msec = 1_845_000f,
            count = 14f,
        ),
        apk = BatteryStats.App.Apk(wakeups = 87f),
        camera = BatteryStats.App.Camera(total_time_msec = 0f),
        audio = BatteryStats.App.Audio(total_time_msec = 126_000f),
        flashlight = BatteryStats.App.Flashlight(total_time_msec = 0f),
        network = BatteryStats.App.Network(
            mobile_bytes_rx = 1_250_000f,
            mobile_bytes_tx = 320_000f,
            wifi_bytes_rx = 12_400_000f,
            wifi_bytes_tx = 1_100_000f,
        ),
    )
    return AppUsage(
        app = app,
        power = AppPowerStat(
            name = "com.example.video",
            uid = 10123,
            devicePowerMah = 18.42f,
            devicePowerPct = 0.614f,
            hasDevicePower = true,
        ),
    )
}

@Preview(name = "App detail light", showBackground = true, widthDp = 411, heightDp = 820)
@Composable
private fun AppDetailSheetLightPreview() {
    BatteryArcheologistTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AppDetailContent(
                usage = previewAppUsage(),
                displayLabel = "Example Video",
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            )
        }
    }
}

@Preview(
    name = "App detail dark",
    showBackground = true,
    widthDp = 411,
    heightDp = 820,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AppDetailSheetDarkPreview() {
    BatteryArcheologistTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AppDetailContent(
                usage = previewAppUsage(),
                displayLabel = "Example Video",
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            )
        }
    }
}
