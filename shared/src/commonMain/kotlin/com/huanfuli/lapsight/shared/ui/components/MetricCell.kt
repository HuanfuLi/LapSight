package com.huanfuli.lapsight.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.huanfuli.lapsight.shared.ui.LapSightTheme

/** Size/layout class of a [MetricCell]. */
enum class MetricCellSize {
    /** Fullscreen dash telemetry: large mono value. */
    Dash,

    /** Status-bar / grid cell: compact mono value under a caption. */
    Compact,

    /** Horizontal label–value line for detail lists (ID, Source, Payload…). */
    Row,
}

/**
 * Labeled numeric readout — the one idiom for every label+value pairing
 * (dash telemetry, GPS status metrics, review detail lines). Values render
 * in the mono timing face; labels are tracked captions.
 *
 * @param tone optional semantic value color (delta/status); defaults to onSurface.
 */
@Composable
fun MetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    size: MetricCellSize = MetricCellSize.Compact,
    tone: Color? = null,
) {
    val valueColor = tone ?: MaterialTheme.colorScheme.onSurface
    when (size) {
        MetricCellSize.Row -> Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.weight(0.4f),
            )
            TimingText(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor,
                maxLines = 2,
                modifier = Modifier.weight(0.6f),
            )
        }

        else -> Column(
            modifier = modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.background)
                .padding(
                    horizontal = LapSightTheme.spacing.sm,
                    vertical = if (size == MetricCellSize.Dash) LapSightTheme.spacing.sm else LapSightTheme.spacing.xs,
                ),
        ) {
            Text(
                text = label.uppercase(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TimingText(
                text = value,
                style = when (size) {
                    MetricCellSize.Dash -> MaterialTheme.typography.headlineSmall
                    else -> MaterialTheme.typography.titleSmall
                },
                color = valueColor,
            )
        }
    }
}
