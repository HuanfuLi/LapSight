package com.huanfuli.lapsight.shared.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.huanfuli.lapsight.shared.ui.LapSightTheme

/**
 * Single-choice segmented control — replaces the repeated
 * selected-Button/unselected-OutlinedButton pairs (speed unit, location
 * source, theme, course direction). One bordered track, solid accent fill on
 * the selected segment. Whole control keeps the ≥48dp touch floor.
 *
 * @param optionEnabled per-option enablement (e.g. Phone GPS unavailable).
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    optionEnabled: (Int) -> Boolean = { true },
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, LapSightTheme.colors.cardBorder),
    ) {
        Row(
            modifier = Modifier
                .padding(SegmentInset)
                .selectableGroup(),
        ) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                val enabled = optionEnabled(index)
                val fill by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.background,
                )
                val textColor = when {
                    selected -> MaterialTheme.colorScheme.onPrimary
                    enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = SegmentMinHeight)
                        .clip(MaterialTheme.shapes.small)
                        .background(fill)
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            onClick = { onSelect(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelLarge,
                        color = textColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private val SegmentInset = 3.dp

// 42dp visible + 3dp inset on each side = 48dp control height (touch floor).
private val SegmentMinHeight = 42.dp
