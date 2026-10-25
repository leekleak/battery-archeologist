/*
 * Dialog and bottom-sheet surfaces styled after Traffic Light's `FancyDialog`
 * and sheet headers (ui/settings/SettingsWidgets.kt, ui/history/History.kt),
 * GPL-3.0, same author.
 */

package com.leekleak.batteryarcheologist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.ui.theme.googleSans

/**
 * Rounded tonal container used by every fancy surface: a subtle card that sits
 * on the dialog or sheet background without any elevation shadow.
 */
@Composable
fun Modifier.fancyCard(
    color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
): Modifier = this
    .clip(MaterialTheme.shapes.medium)
    .background(color)

/**
 * Standard header of a fancy bottom sheet: optional leading visual (icon or app
 * icon), a Google Sans title with an optional supporting line, and optional
 * trailing actions.
 */
@Composable
fun FancySheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val titleFont = remember { googleSans(weight = 600f) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                fontFamily = titleFont,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * Fancy modal bottom sheet: a header, padded content, and an optional
 * end-aligned action row, all on the stock Material sheet container.
 *
 * The sheet always opens fully expanded; partially expanded states are not used
 * because every sheet here is a short, focused task.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FancyBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Sheets render in their own window, so the root-level
                // `testTagsAsResourceId` from AppRoot does not reach here.
                .semantics { testTagsAsResourceId = true }
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            header?.invoke(this)
            content()
            if (footer != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    footer()
                }
            }
        }
    }
}

/**
 * Fancy dialog: icon + title header, caller content, and a footer with the
 * dismiss action plus one optional primary action.
 *
 * The dialog sizes itself instead of using the platform default width, because
 * some devices render that width edge to edge. A fixed 24dp gutter keeps the
 * card clear of the screen edges, and the card is capped at [MAX_DIALOG_WIDTH]
 * so it stays readable on tablets and foldables.
 *
 * [dismissLabel] and [dismissModifier] exist so call sites can keep their
 * established wording (Cancel) and test tags.
 */
@Composable
fun FancyDialog(
    onDismissRequest: () -> Unit,
    title: String,
    icon: Painter,
    modifier: Modifier = Modifier,
    dismissLabel: String = stringResource(R.string.action_close),
    dismissModifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(16.dp),
    actionButton: @Composable (RowScope.() -> Unit) = {},
    content: @Composable (ColumnScope.() -> Unit),
) {
    val titleFont = remember { googleSans(weight = 600f) }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // The window fills the screen, so the scrim has to handle outside taps
        // itself; the card swallows its own taps in turn.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(onDismissRequest) {
                    detectTapGestures { onDismissRequest() }
                }
                .padding(horizontal = 24.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = modifier
                    .pointerInput(Unit) { detectTapGestures { } }
                    // Dialogs render in their own window, so the root-level
                    // `testTagsAsResourceId` from AppRoot does not reach here.
                    .semantics { testTagsAsResourceId = true }
                    .widthIn(max = MAX_DIALOG_WIDTH)
                    .fillMaxWidth()
                    .fancyCard(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(16.dp),
                verticalArrangement = verticalArrangement,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = title,
                        fontFamily = titleFont,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                content()
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismissRequest, modifier = dismissModifier) {
                        Text(dismissLabel)
                    }
                    actionButton()
                }
            }
        }
    }
}

/** Upper bound for [FancyDialog] on tablets and foldables, in dp. */
private val MAX_DIALOG_WIDTH = 420.dp
