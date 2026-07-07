// Directory: ui/review — package stays `shared.ui` (see ReviewScreen.kt note).
package com.huanfuli.lapsight.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huanfuli.lapsight.shared.DriveDisplaySettings
import com.huanfuli.lapsight.shared.export.ExportShareTarget
import com.huanfuli.lapsight.shared.storage.LocalSessionStore
import com.huanfuli.lapsight.shared.track.ReviewEntryType
import com.huanfuli.lapsight.shared.ui.components.ChipTone
import com.huanfuli.lapsight.shared.ui.components.LapDialog
import com.huanfuli.lapsight.shared.ui.components.StatusChip
import com.huanfuli.lapsight.shared.ui.components.StatusMessage

/**
 * Full-screen detail for one Review entry, pushed from the list.
 *
 * Top bar: back, identity (title + type/date), DEMO provenance, and — for
 * Tracks — an overflow menu carrying the profile lifecycle (rename, duplicate,
 * archive; D-12/D-16) and export (D-40). Lifecycle actions live here so the
 * body stays about the data: course map, timing, telemetry.
 */
@Composable
internal fun ReviewDetailScreen(
    row: ReviewRowViewModel,
    sessionStore: LocalSessionStore,
    displaySettings: DriveDisplaySettings,
    exportShareTarget: ExportShareTarget,
    refreshVersion: Long,
    onDataChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val spacing = LapSightTheme.spacing
    val s = strings
    var actionMessage by remember(row.id) { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = BackIcon,
                    contentDescription = s.close,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.displayTitle(s),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${row.localizedTypeLabel(s)} · ${formatEpochMillis(row.createdAtEpochMillis)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
            if (row.isDemo) {
                StatusChip(text = "DEMO", tone = ChipTone.Demo)
            }
            if (row.isArchived) {
                StatusChip(text = s.archived, tone = ChipTone.Neutral)
            }
            if (row.type == ReviewEntryType.Track) {
                TrackActionsMenu(
                    row = row,
                    sessionStore = sessionStore,
                    exportShareTarget = exportShareTarget,
                    onMessage = { actionMessage = it },
                    onDataChanged = onDataChanged,
                    onDeleted = onBack,
                )
            }
        }
        HorizontalDivider(color = LapSightTheme.colors.cardBorder)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(spacing.md),
        ) {
            actionMessage?.let { msg ->
                StatusMessage(
                    text = msg,
                    tone = if (msg.startsWith("Couldn't") || msg.startsWith("Export failed")) {
                        ChipTone.Error
                    } else {
                        ChipTone.Ready
                    },
                    modifier = Modifier.padding(bottom = spacing.sm),
                )
            }
            when (row.type) {
                ReviewEntryType.TimingSession -> TimingSessionReviewDetail(
                    sessionId = row.id,
                    sessionStore = sessionStore,
                    displaySettings = displaySettings,
                    exportShareTarget = exportShareTarget,
                )
                ReviewEntryType.Track -> TrackDetailBody(
                    row = row,
                    sessionStore = sessionStore,
                    refreshVersion = refreshVersion,
                    onDataChanged = onDataChanged,
                )
                ReviewEntryType.TrackMarking -> RawCaptureDetailBody(
                    row = row,
                    sessionStore = sessionStore,
                    refreshVersion = refreshVersion,
                )
            }
        }
    }
}

/**
 * Track overflow menu: rename (dialog), duplicate, archive (confirm), export.
 * None of these deletes data (D-16); archive clears the current selection when
 * needed (D-01/D-03) and says so in its result message.
 */
@Composable
private fun TrackActionsMenu(
    row: ReviewRowViewModel,
    sessionStore: LocalSessionStore,
    exportShareTarget: ExportShareTarget,
    onMessage: (String?) -> Unit,
    onDataChanged: () -> Unit,
    onDeleted: () -> Unit,
) {
    val s = strings
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var archiveConfirmOpen by remember { mutableStateOf(false) }
    var deleteConfirmOpen by remember { mutableStateOf(false) }
    var renameValue by remember(row.id) { mutableStateOf(row.name) }

    Box {
        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = MoreActionsIcon,
                contentDescription = s.edit,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (row.isArchived) {
                DropdownMenuItem(
                    text = { Text(s.restore) },
                    leadingIcon = { Icon(RestoreActionIcon, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        val msg = restoreTrack(sessionStore, row.id)
                        onMessage(msg)
                        if (!msg.startsWith("Couldn't")) onDataChanged()
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(s.rename) },
                    leadingIcon = { Icon(EditActionIcon, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        renameOpen = true
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(s.duplicate) },
                leadingIcon = { Icon(DuplicateActionIcon, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    val msg = duplicateTrack(sessionStore, row.id)
                    onMessage(msg)
                    if (!msg.startsWith("Couldn't")) onDataChanged()
                },
            )
            if (!row.isArchived) {
                DropdownMenuItem(
                    text = { Text(s.archive) },
                    leadingIcon = { Icon(ArchiveActionIcon, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        archiveConfirmOpen = true
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(s.exportJson) },
                leadingIcon = { Icon(ExportActionIcon, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onMessage(exportTrackJson(row, sessionStore, exportShareTarget))
                },
            )
            DropdownMenuItem(
                text = { Text(s.delete) },
                leadingIcon = { Icon(DeleteActionIcon, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    deleteConfirmOpen = true
                },
            )
        }
    }

    if (renameOpen) {
        LapDialog(
            title = s.rename,
            onDismissRequest = { renameOpen = false },
            confirmText = s.rename,
            confirmIcon = EditActionIcon,
            onConfirm = {
                renameOpen = false
                val msg = renameTrack(sessionStore, row.id, renameValue)
                onMessage(msg)
                if (!msg.startsWith("Couldn't")) onDataChanged()
            },
            dismissText = s.cancel,
            dismissIcon = CloseActionIcon,
            dismissIconOnly = true,
            dismissContentDescription = s.cancel,
            content = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text(s.trackName) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }
    if (archiveConfirmOpen) {
        LapDialog(
            title = s.archive,
            text = "It stays in history with every revision and session, but leaves track selection.",
            onDismissRequest = { archiveConfirmOpen = false },
            confirmText = s.archive,
            confirmIcon = ArchiveActionIcon,
            onConfirm = {
                archiveConfirmOpen = false
                val msg = archiveTrack(sessionStore, row.id)
                onMessage(msg)
                if (!msg.startsWith("Couldn't")) onDataChanged()
            },
            dismissText = s.cancel,
            dismissIcon = CloseActionIcon,
            dismissIconOnly = true,
            dismissContentDescription = s.cancel,
        )
    }
    if (deleteConfirmOpen) {
        LapDialog(
            title = s.delete,
            text = s.deleteReviewItemText,
            onDismissRequest = { deleteConfirmOpen = false },
            confirmText = s.delete,
            destructiveConfirm = true,
            confirmIcon = DeleteActionIcon,
            onConfirm = {
                deleteConfirmOpen = false
                val msg = deleteReviewEntry(sessionStore, row)
                onMessage(msg)
                if (!msg.startsWith("Couldn't")) {
                    onDataChanged()
                    onDeleted()
                }
            },
            dismissText = s.cancel,
            dismissIcon = CloseActionIcon,
            dismissIconOnly = true,
            dismissContentDescription = s.cancel,
        )
    }
}
