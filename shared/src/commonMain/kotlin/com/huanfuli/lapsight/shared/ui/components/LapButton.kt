package com.huanfuli.lapsight.shared.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.huanfuli.lapsight.shared.ui.LapSightTheme

/** Visual role of a [LapButton]. */
enum class LapButtonStyle {
    /** Filled accent — the one primary action on a surface. */
    Primary,

    /** Hairline outlined — secondary actions. */
    Secondary,

    /** Filled destructive container — stop/discard-class actions. */
    Destructive,

    /** Borderless text action — inline/low-emphasis. */
    Ghost,

    /** Borderless destructive text action (Discard-class). */
    GhostDestructive,
}

/** Height class. Standard = 48dp floor; Large = 56dp for timing-active controls. */
enum class LapButtonSize { Standard, Large }

/**
 * The app-wide button. Enforces the mounted-phone touch-target floor
 * (≥48dp, SAFE-02/D-29; 56dp for controls used while timing) and routes all
 * color through the theme so no call site restyles buttons ad hoc.
 */
@Composable
fun LapButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: LapButtonStyle = LapButtonStyle.Primary,
    size: LapButtonSize = LapButtonSize.Standard,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val minHeight = when (size) {
        LapButtonSize.Standard -> 48.dp
        LapButtonSize.Large -> 56.dp
    }
    val sized = modifier.heightIn(min = minHeight)
    val shape = MaterialTheme.shapes.medium
    val padding = PaddingValues(horizontal = LapSightTheme.spacing.md, vertical = LapSightTheme.spacing.sm)
    val label: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LapSightTheme.spacing.sm),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
    when (style) {
        LapButtonStyle.Primary -> Button(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            shape = shape,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) { label() }

        LapButtonStyle.Secondary -> OutlinedButton(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            shape = shape,
            contentPadding = padding,
            border = BorderStroke(1.dp, LapSightTheme.colors.cardBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) { label() }

        LapButtonStyle.Destructive -> Button(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            shape = shape,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) { label() }

        LapButtonStyle.Ghost, LapButtonStyle.GhostDestructive -> TextButton(
            onClick = onClick,
            modifier = sized,
            enabled = enabled,
            shape = shape,
            contentPadding = padding,
            colors = ButtonDefaults.textButtonColors(
                contentColor = if (style == LapButtonStyle.GhostDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            ),
        ) { label() }
    }
}
