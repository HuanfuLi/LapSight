package com.huanfuli.lapsight.shared.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.huanfuli.lapsight.shared.ui.LapSightTheme

/**
 * Uppercase eyebrow label for a settings group / list section / card header.
 * One style everywhere: tracked caption in the muted foreground, optional
 * accent count (e.g. "SESSIONS  3").
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    if (count == null) {
        Text(
            text = text.uppercase(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            modifier = modifier,
            maxLines = 1,
        )
    } else {
        Row(modifier = modifier) {
            Text(
                text = text.uppercase(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
            Spacer(Modifier.width(LapSightTheme.spacing.sm))
            Text(
                text = count.toString(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}
