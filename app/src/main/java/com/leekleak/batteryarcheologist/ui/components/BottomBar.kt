package com.leekleak.batteryarcheologist.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.leekleak.batteryarcheologist.ui.navigation.ContentTab
import com.leekleak.batteryarcheologist.ui.theme.BatteryArcheologistTheme

/** Bottom inset reserved by the floating toolbar plus its screen margin. */
val NAVBAR_PADDING: Dp =
    FloatingToolbarDefaults.ContainerSize + FloatingToolbarDefaults.ScreenOffset - 8.dp

/**
 * Floating bottom navigation modeled on Traffic Light's toolbar: a bordered,
 * horizontally centered floating toolbar where only the selected tab shows its
 * label.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BottomBar(
    selected: ContentTab,
    onSelect: (ContentTab) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalFloatingToolbar(
            modifier = Modifier.border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                shape = MaterialTheme.shapes.extraLargeIncreased,
            ),
            expanded = true,
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors().copy(
                toolbarContainerColor = MaterialTheme.colorScheme.background,
            ),
        ) {
            ContentTab.entries.forEach { tab ->
                BottomBarButton(
                    tab = tab,
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                )
            }
        }
    }
}

@Composable
private fun BottomBarButton(
    tab: ContentTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(tab.titleRes)
    val haptic = LocalHapticFeedback.current
    val horizontalPadding by animateDpAsState(
        targetValue = if (selected) 24.dp else 12.dp,
        label = "bottomBarHorizontalPadding",
    )

    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier.testTag(tab.testTag),
        colors = if (selected) {
            ButtonDefaults.filledTonalButtonColors()
        } else {
            ButtonDefaults.textButtonColors()
        },
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(tab.iconRes),
                contentDescription = label,
                modifier = Modifier.size(24.dp),
            )
            AnimatedVisibility(visible = selected) {
                Text(
                    text = label,
                    modifier = Modifier.padding(start = 4.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

@Preview(name = "BottomBar light", showBackground = true)
@Composable
private fun BottomBarLightPreview() {
    BatteryArcheologistTheme {
        BottomBar(selected = ContentTab.OVERVIEW, onSelect = {})
    }
}

@Preview(
    name = "BottomBar dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun BottomBarDarkPreview() {
    BatteryArcheologistTheme {
        BottomBar(selected = ContentTab.TIMELINE, onSelect = {})
    }
}
