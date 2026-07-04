package com.huanfuli.lapsight.shared.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.huanfuli.lapsight.shared.ui.LapSightTheme

/**
 * Numeric instrument readout: lap clocks, deltas, speeds, sample counts.
 * Renders in the bundled mono face (IBM Plex Mono) so digits are tabular —
 * a running clock never jitters horizontally. Use for values, not prose.
 */
@Composable
fun TimingText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style.copy(fontFamily = LapSightTheme.monoFamily),
        textAlign = textAlign,
        maxLines = maxLines,
    )
}
