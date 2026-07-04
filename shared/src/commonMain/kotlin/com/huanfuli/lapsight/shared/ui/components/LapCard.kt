package com.huanfuli.lapsight.shared.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.huanfuli.lapsight.shared.ui.LapSightTheme

/**
 * LapSight card: hairline-bordered raised surface, `shapes.large` corners.
 * The single card idiom for the whole app — replaces ad-hoc `Box + border`
 * and stock `Card` calls so surfaces read as one instrument panel family.
 *
 * @param title optional uppercase section header rendered above the content.
 * @param trailing optional slot on the header row (e.g. a [StatusChip]).
 * @param onClick if set the whole card is tappable (list rows).
 */
@Composable
fun LapCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = BorderStroke(LapCardDefaults.BorderWidth, LapSightTheme.colors.cardBorder)
    val shape = MaterialTheme.shapes.large
    val color = MaterialTheme.colorScheme.surface
    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth().padding(LapSightTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(LapSightTheme.spacing.sm),
        ) {
            if (title != null || trailing != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LapSightTheme.spacing.sm),
                ) {
                    if (title != null) {
                        SectionHeader(text = title, modifier = Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    trailing?.invoke()
                }
            }
            content()
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = color,
            border = border,
        ) { body() }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = color,
            border = border,
        ) { body() }
    }
}

object LapCardDefaults {
    val BorderWidth = 1.dp
}
