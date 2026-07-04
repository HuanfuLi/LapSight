package com.huanfuli.lapsight.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.huanfuli.lapsight.shared.ui.LapSightTheme

/**
 * Safety/caution copy row: amber sidebar + caution-colored text. The one way
 * safety language renders (closed-course use, stationary-config warnings).
 * The copy itself is a Non-Negotiable and must pass through verbatim.
 */
@Composable
fun SafetyNote(
    text: String,
    modifier: Modifier = Modifier,
) {
    val caution = LapSightTheme.colors.statusCaution
    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(MaterialTheme.shapes.extraSmall)
                .background(caution),
        )
        Spacer(Modifier.width(LapSightTheme.spacing.sm))
        Text(
            text = text,
            color = caution,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
