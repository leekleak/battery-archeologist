package com.leekleak.batteryarcheologist.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.ui.navigation.ContentTab
import com.leekleak.batteryarcheologist.ui.theme.BatteryArcheologistTheme
import com.leekleak.batteryarcheologist.ui.theme.LocalBlurEnabled
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Height of the left-aligned page title row. */
val TOP_BAR_HEIGHT: Dp = 52.dp

/** Horizontal page margin used by the scaffold and screen content. */
val PAGE_PADDING: Dp = 16.dp

private val TITLE_BOTTOM_PADDING = 6.dp
private val CONTENT_TOP_GAP = 6.dp
private val CONTENT_BOTTOM_GAP = 8.dp

/**
 * App shell with an edge-to-edge scrolling content area, a blurred Material page
 * title and an overlaid bottom bar.
 *
 * Keep [scrollState] non-null for ordinary [Column] content: the scaffold then
 * owns the vertical scroll, horizontal page padding and the top/bottom spacers
 * that keep content clear of the system bars and the floating toolbar.
 *
 * Pass `scrollState = null` when the content contains a `LazyColumn` (or other
 * intrinsically scrollable box): the [content] lambda then receives the full
 * [PaddingValues] to apply itself, and the scaffold adds no spacers or
 * horizontal padding.
 *
 * When [onBack] is non-null a back button is shown before the title. When
 * [bottomBar] is null no bottom bar is rendered and no toolbar padding is
 * reserved below the content.
 */
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun AppScaffold(
    title: String,
    modifier: Modifier = Modifier,
    scrollState: ScrollState? = rememberScrollState(),
    hazeState: HazeState? = rememberHazeState(),
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.(PaddingValues) -> Unit,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentTop = statusBarTop + TOP_BAR_HEIGHT + CONTENT_TOP_GAP
    val toolbarPadding = if (bottomBar != null) NAVBAR_PADDING else 0.dp
    val contentBottom = navigationBarBottom + CONTENT_BOTTOM_GAP + toolbarPadding

    val contentPadding = if (scrollState != null) {
        PaddingValues()
    } else {
        PaddingValues(
            start = PAGE_PADDING,
            top = contentTop,
            end = PAGE_PADDING,
            bottom = contentBottom,
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Box(modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .fillMaxSize()
                    .then(hazeState?.let { Modifier.hazeSource(it) } ?: Modifier)
                    .padding(horizontal = if (scrollState != null) PAGE_PADDING else 0.dp)
                    .then(
                        if (scrollState != null) {
                            Modifier.verticalScroll(scrollState)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                if (scrollState != null) {
                    Spacer(Modifier.height(contentTop))
                }
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    content(contentPadding)
                }
                if (scrollState != null) {
                    Spacer(Modifier.height(contentBottom))
                }
            }

            AppTitleBar(
                title = title,
                hazeState = hazeState,
                onBack = onBack,
                actions = actions,
            )

            if (bottomBar != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                ) {
                    bottomBar()
                }
            }
        }
    }
}

/**
 * Page title drawn over the scrollable content. The bar itself is transparent:
 * [hazeBlur] paints a soft, progressively fading blur of whatever is behind it,
 * so the content slides under the title instead of being clipped by an opaque
 * surface.
 */
@Composable
private fun AppTitleBar(
    title: String,
    hazeState: HazeState?,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
) {
    val blurEnabled = LocalBlurEnabled.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                hazeState?.let {
                    Modifier.hazeBlur(
                        input = HazeInput.Sources(it),
                        style = HazeMaterials.ultraThin().then {
                            progressive(
                                HazeProgressive.verticalGradient(
                                    startIntensity = 1f,
                                    endIntensity = 0f,
                                ),
                            )
                            blurEnabled(blurEnabled)
                        },
                    )
                } ?: Modifier,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = PAGE_PADDING)
                .padding(bottom = TITLE_BOTTOM_PADDING)
                .height(TOP_BAR_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("btn_back"),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = actions,
            )
        }
    }
}

@Preview(name = "AppScaffold light", showBackground = true)
@Composable
private fun AppScaffoldLightPreview() {
    BatteryArcheologistTheme {
        AppScaffold(
            title = "Overview",
            actions = {
                TextButton(onClick = {}) { Text("Capture") }
            },
            bottomBar = {
                BottomBar(selected = ContentTab.OVERVIEW, onSelect = {})
            },
        ) {
            SectionCard(title = "Battery", subtitle = "Pixel 9 · 87%") {
                SummaryRow("Screen on", "3h 12m")
                SummaryRow("Screen off", "8h 04m")
            }
            SectionCard(title = "Top apps") {
                SummaryRow("Chrome", "12.4%")
                SummaryRow("YouTube", "7.1%")
                SummaryRow("Maps", "4.8%")
            }
            SectionCard(title = "Reports") {
                SummaryRow("Saved captures", "6")
            }
        }
    }
}

@Preview(
    name = "AppScaffold dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AppScaffoldDarkPreview() {
    BatteryArcheologistTheme {
        AppScaffold(
            title = "Timeline",
            actions = {
                TextButton(onClick = {}) { Text("Share") }
            },
            bottomBar = {
                BottomBar(selected = ContentTab.TIMELINE, onSelect = {})
            },
        ) {
            SectionCard(title = "Discharge", subtitle = "86% → 34% over 9h 21m") {
                SummaryRow("Screen on", "4h 02m")
                SummaryRow("Awake", "5h 14m")
                SummaryRow("Deep sleep", "6h 38m")
            }
            SectionCard(title = "Top apps") {
                SummaryRow("Chrome", "18.2%")
                SummaryRow("Spotify", "9.6%")
            }
        }
    }
}
