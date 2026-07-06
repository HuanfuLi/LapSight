package com.huanfuli.lapsight.shared.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.huanfuli.lapsight.shared.ui.LapSightTheme

/**
 * Themed dialog wrapper — instrument-panel surface, hairline-consistent
 * shape, and ≥48dp action buttons (stock M3 text buttons are 40dp; too small
 * for gloved use, SAFE-02).
 *
 * Standard two-action form via [confirmText]/[dismissText]; multi-action
 * dialogs (draft recovery) pass a custom [buttons] row of
 * [LapDialogTextButton]s instead. Pass a no-op [onDismissRequest] to require
 * an explicit choice (recovery prompt, D-16).
 */
@Composable
fun LapDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    destructiveConfirm: Boolean = false,
    confirmIcon: ImageVector? = null,
    confirmIconOnly: Boolean = false,
    confirmContentDescription: String? = null,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    dismissIcon: ImageVector? = null,
    dismissIconOnly: Boolean = false,
    dismissContentDescription: String? = null,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = when {
            text != null || content != null -> {
                {
                    Column {
                        if (text != null) {
                            Text(text, style = MaterialTheme.typography.bodyMedium)
                        }
                        content?.invoke(this)
                    }
                }
            }
            else -> null
        },
        confirmButton = {
            if (buttons != null) {
                Row(modifier = Modifier.fillMaxWidth(), content = buttons)
            } else if (confirmText != null && onConfirm != null) {
                LapDialogTextButton(
                    text = confirmText,
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    destructive = destructiveConfirm,
                    icon = confirmIcon,
                    iconOnly = confirmIconOnly,
                    contentDescription = confirmContentDescription ?: confirmText,
                )
            }
        },
        dismissButton = when {
            buttons != null -> null
            dismissText != null -> {
                {
                    LapDialogTextButton(
                        text = dismissText,
                        onClick = onDismiss ?: onDismissRequest,
                        icon = dismissIcon,
                        iconOnly = dismissIconOnly,
                        contentDescription = dismissContentDescription ?: dismissText,
                    )
                }
            }
            else -> null
        },
    )
}

/** Dialog action button with the 48dp touch floor and semantic coloring. */
@Composable
fun LapDialogTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    icon: ImageVector? = null,
    iconOnly: Boolean = false,
    contentDescription: String = text,
) {
    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LapSightTheme.spacing.xs),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = if (iconOnly) contentDescription else null,
                    modifier = Modifier.size(22.dp),
                )
            }
            if (!iconOnly) {
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    if (iconOnly) {
        Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = 60.dp).widthIn(min = 72.dp),
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(horizontal = 18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (destructive) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (destructive) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
                disabledContainerColor = LapSightTheme.colors.disabledContainer,
                disabledContentColor = LapSightTheme.colors.disabledContent,
            ),
        ) { content() }
    } else {
        TextButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 48.dp).widthIn(min = 48.dp),
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(horizontal = 16.dp),
            colors = ButtonDefaults.textButtonColors(
                contentColor = if (destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            ),
        ) { content() }
    }
}
