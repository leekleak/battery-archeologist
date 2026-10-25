package com.leekleak.batteryarcheologist.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leekleak.batteryarcheologist.R
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RawReportSheet(
    rawText: String,
    savedName: String?,
    sizeBytes: Long,
    source: String?,
    onShare: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("raw_capture"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.reports_raw_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = subtitle(
                            savedName = savedName,
                            sizeBytes = sizeBytes,
                            source = source,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onShare != null) {
                    FilledTonalButton(
                        onClick = onShare,
                        modifier = Modifier.testTag("btn_share_raw_sheet"),
                    ) {
                        Text(stringResource(R.string.action_share))
                    }
                }
            }

            HorizontalDivider(Modifier.padding(horizontal = 24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
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
