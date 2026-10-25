package com.leekleak.batteryarcheologist.ui.reports

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leekleak.batteryarcheologist.R
import com.leekleak.batteryarcheologist.ui.components.FancyBottomSheet
import com.leekleak.batteryarcheologist.ui.components.FancySheetHeader
import com.leekleak.batteryarcheologist.ui.components.fancyCard
import com.leekleak.batteryarcheologist.ui.components.formatBytes
import com.leekleak.batteryarcheologist.ui.components.reportSourceLabel

/**
 * Full raw text of one report, either a saved file or the latest unsaved
 * capture.
 *
 * Unlike the old fixed-height preview card this sheet keeps the whole capture
 * and lets the text scroll vertically, so large bugreports remain inspectable.
 * Sharing is only offered for reports that were written to the reports
 * directory, because the share intent needs a real file.
 */
@Composable
internal fun RawReportSheet(
    rawText: String,
    savedName: String?,
    sizeBytes: Long,
    source: String?,
    onShare: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val shareAction: (@Composable () -> Unit)? = onShare?.let { share ->
        {
            FilledTonalButton(
                onClick = share,
                modifier = Modifier.testTag("btn_share_raw_sheet"),
            ) {
                Text(stringResource(R.string.action_share))
            }
        }
    }

    FancyBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("raw_capture"),
        header = {
            FancySheetHeader(
                title = stringResource(R.string.reports_raw_title),
                subtitle = subtitle(
                    savedName = savedName,
                    sizeBytes = sizeBytes,
                    source = source,
                ),
                leading = {
                    Icon(
                        painter = painterResource(R.drawable.description),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                },
                trailing = shareAction,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fancyCard()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            SelectionContainer {
                Text(
                    text = rawText.ifBlank {
                        stringResource(R.string.reports_raw_empty)
                    },
                    modifier = Modifier.testTag("raw_preview"),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun subtitle(
    savedName: String?,
    sizeBytes: Long,
    source: String?,
): String = stringResource(
    R.string.reports_raw_subtitle,
    reportSourceLabel(source),
    formatBytes(sizeBytes),
    savedName ?: stringResource(R.string.reports_raw_not_saved),
)
