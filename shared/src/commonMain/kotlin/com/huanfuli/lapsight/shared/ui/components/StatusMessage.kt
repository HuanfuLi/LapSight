package com.huanfuli.lapsight.shared.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.huanfuli.lapsight.shared.ui.LapSightTheme

/**
 * Inline operation-result line ("Exported …", "Couldn't rename: …").
 * One styling for every save/export/rename status so success and failure
 * always read the same way across screens.
 */
@Composable
fun StatusMessage(
    text: String,
    tone: ChipTone,
    modifier: Modifier = Modifier,
) {
    val color = when (tone) {
        ChipTone.Ready -> LapSightTheme.colors.statusReady
        ChipTone.Caution -> LapSightTheme.colors.statusCaution
        ChipTone.Error -> LapSightTheme.colors.statusError
        ChipTone.Demo -> LapSightTheme.colors.demo
        ChipTone.Accent -> MaterialTheme.colorScheme.primary
        ChipTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier,
    )
}
