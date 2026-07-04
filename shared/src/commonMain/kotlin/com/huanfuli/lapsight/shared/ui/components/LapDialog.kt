package com.huanfuli.lapsight.shared.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
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
                )
            }
        },
        dismissButton = when {
            buttons != null -> null
            dismissText != null -> {
                { LapDialogTextButton(text = dismissText, onClick = onDismiss ?: onDismissRequest) }
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
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (destructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
