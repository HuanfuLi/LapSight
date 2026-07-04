package com.huanfuli.lapsight.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.huanfuli.lapsight.shared.ui.LapSightTheme

/** Semantic tone of a [StatusChip]. */
enum class ChipTone { Ready, Caution, Error, Neutral, Demo, Accent }

/**
 * Small status marker (GPS state, DEMO, readiness). Replaces the ad-hoc
 * badge boxes so every status pill shares one engraved-label look: tinted
 * fill, hairline border, uppercase caption.
 *
 * Simulated data must never read as live (D-42) — [ChipTone.Demo] keeps the
 * amber warning look for that copy.
 */
@Composable
fun StatusChip(
    text: String,
    tone: ChipTone,
    modifier: Modifier = Modifier,
) {
    val color = tone.toneColor()
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.45f), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = LapSightTheme.spacing.sm, vertical = LapSightTheme.spacing.xs),
    ) {
        Text(
            text = text.uppercase(),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun ChipTone.toneColor(): Color = when (this) {
    ChipTone.Ready -> LapSightTheme.colors.statusReady
    ChipTone.Caution -> LapSightTheme.colors.statusCaution
    ChipTone.Error -> LapSightTheme.colors.statusError
    ChipTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    ChipTone.Demo -> LapSightTheme.colors.demo
    ChipTone.Accent -> MaterialTheme.colorScheme.primary
}
