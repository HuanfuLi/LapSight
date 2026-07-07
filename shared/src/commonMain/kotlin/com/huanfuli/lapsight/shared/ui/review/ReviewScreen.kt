// Directory: ui/review — package intentionally stays `shared.ui` so the pure
// helpers (renameTrack, ReviewListState, …) keep their public coordinates for
// the existing common tests.
package com.huanfuli.lapsight.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huanfuli.lapsight.shared.DriveDisplaySettings
import com.huanfuli.lapsight.shared.export.ExportShareTarget
import com.huanfuli.lapsight.shared.export.NoOpExportShareTarget
import com.huanfuli.lapsight.shared.lap.formatLapTime
import com.huanfuli.lapsight.shared.storage.LocalSessionStore
import com.huanfuli.lapsight.shared.track.ReviewEntryType
import com.huanfuli.lapsight.shared.ui.components.ChipTone
import com.huanfuli.lapsight.shared.ui.components.LapCard
import com.huanfuli.lapsight.shared.ui.components.LapDialog
import com.huanfuli.lapsight.shared.ui.components.SectionHeader
import com.huanfuli.lapsight.shared.ui.components.StatusChip
import com.huanfuli.lapsight.shared.ui.components.TimingText
import kotlinx.coroutines.delay

/**
 * Review tab (D-27, D-28): lists saved Sessions, Tracks, and raw captures from
 * the local-first store with visible `DEMO` provenance (D-42, T-03-10).
 *
 * Navigation: the list shows compact, glanceable cards; tapping one pushes a
 * full detail screen ([ReviewDetailScreen]) with a back affordance. Detail
 * content never expands inline inside the list (that pattern produced the
 * unbounded scrolling cards this screen replaced).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ReviewScreen(
    sessionStore: LocalSessionStore,
    savedVersion: Long,
    displaySettings: DriveDisplaySettings,
    exportShareTarget: ExportShareTarget = NoOpExportShareTarget,
) {
    val s = strings
    var rows by remember { mutableStateOf(ReviewListState.from(sessionStore)) }
    var openedKey by remember { mutableStateOf<String?>(null) }
    var localRefreshVersion by remember { mutableStateOf(0L) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    val refreshRows: () -> Unit = { localRefreshVersion += 1L }

    // Re-read the index when the Drive tab reports a new save (D-27).
    LaunchedEffect(savedVersion, localRefreshVersion) {
        rows = ReviewListState.from(sessionStore)
    }

    val openedRow = openedKey?.let { key -> rows.firstOrNull { it.key() == key } }
    if (openedRow != null) {
        BackHandler(enabled = true) { openedKey = null }
        ReviewDetailScreen(
            row = openedRow,
            sessionStore = sessionStore,
            displaySettings = displaySettings,
            exportShareTarget = exportShareTarget,
            refreshVersion = localRefreshVersion,
            onDataChanged = refreshRows,
            onBack = { openedKey = null },
        )
        return
    }

    if (rows.isEmpty()) {
        EmptyState()
        return
    }

    val sessions = rows.filter { it.type == ReviewEntryType.TimingSession }
    val tracks = rows.filter { it.type == ReviewEntryType.Track && !it.isArchived }
    val archivedTracks = rows.filter { it.type == ReviewEntryType.Track && it.isArchived }
    val rawCaptures = rows.filter { it.type == ReviewEntryType.TrackMarking }

    val spacing = LapSightTheme.spacing
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            reviewSection(
                title = s.sessions,
                rows = sessions,
                sessionStore = sessionStore,
                exportShareTarget = exportShareTarget,
                onOpen = { openedKey = it },
                onMessage = { actionMessage = it },
                onDataChanged = refreshRows,
            )
            reviewSection(
                title = s.tracks,
                rows = tracks,
                sessionStore = sessionStore,
                exportShareTarget = exportShareTarget,
                onOpen = { openedKey = it },
                onMessage = { actionMessage = it },
                onDataChanged = refreshRows,
            )
            reviewSection(
                title = s.archivedTracks,
                rows = archivedTracks,
                sessionStore = sessionStore,
                exportShareTarget = exportShareTarget,
                onOpen = { openedKey = it },
                onMessage = { actionMessage = it },
                onDataChanged = refreshRows,
            )
            reviewSection(
                title = s.rawCaptures,
                rows = rawCaptures,
                sessionStore = sessionStore,
                exportShareTarget = exportShareTarget,
                onOpen = { openedKey = it },
                onMessage = { actionMessage = it },
                onDataChanged = refreshRows,
            )
        }
        actionMessage?.let { msg ->
            LaunchedEffect(msg) {
                delay(2000)
                if (actionMessage == msg) actionMessage = null
            }
            ReviewToast(message = msg, tone = reviewActionTone(msg))
        }
    }
}

internal fun ReviewRowViewModel.key(): String = "${type.name}:$id"

private fun LazyListScope.reviewSection(
    title: String,
    rows: List<ReviewRowViewModel>,
    sessionStore: LocalSessionStore,
    exportShareTarget: ExportShareTarget,
    onOpen: (String) -> Unit,
    onMessage: (String?) -> Unit,
    onDataChanged: () -> Unit,
) {
    if (rows.isEmpty()) return
    item {
        SectionHeader(
            text = title,
            count = rows.size,
            modifier = Modifier.padding(top = LapSightTheme.spacing.xs),
        )
    }
    items(rows) { row ->
        ReviewRow(
            row = row,
            sessionStore = sessionStore,
            exportShareTarget = exportShareTarget,
            onClick = { onOpen(row.key()) },
            onMessage = onMessage,
            onDataChanged = onDataChanged,
        )
    }
}

@Composable
private fun EmptyState() {
    val s = strings
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(LapSightTheme.spacing.lg),
        ) {
            Text(
                text = s.noSavedSessionsYet,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(LapSightTheme.spacing.sm))
            Text(
                text = s.emptyReviewText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/**
 * Compact list card: identity on the left, the row's one headline fact on the
 * right (best lap for sessions), and a chevron that signals navigation. All
 * fields come from the lightweight index — no payload I/O per row.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ReviewRow(
    row: ReviewRowViewModel,
    sessionStore: LocalSessionStore,
    exportShareTarget: ExportShareTarget,
    onClick: () -> Unit,
    onMessage: (String?) -> Unit,
    onDataChanged: () -> Unit,
) {
    val spacing = LapSightTheme.spacing
    val s = strings
    var menuOpen by remember(row.key()) { mutableStateOf(false) }
    var renameOpen by remember(row.id) { mutableStateOf(false) }
    var archiveConfirmOpen by remember(row.id) { mutableStateOf(false) }
    var deleteConfirmOpen by remember(row.id) { mutableStateOf(false) }
    var renameValue by remember(row.id, row.name) { mutableStateOf(row.name) }

    LapCard(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = { menuOpen = true },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Text(
                        text = row.displayTitle(s),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (row.isDemo) StatusChip(text = "DEMO", tone = ChipTone.Demo)
                    if (row.isArchived) StatusChip(text = s.archived, tone = ChipTone.Neutral)
                }
                Text(
                    text = formatEpochMillis(row.createdAtEpochMillis),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (row.type == ReviewEntryType.TimingSession && row.bestLapMillis != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = s.best,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    TimingText(
                        text = row.bestLapMillis.formatLapTime(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = MoreActionsIcon,
                        contentDescription = s.edit,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                ReviewRowActionsMenu(
                    row = row,
                    sessionStore = sessionStore,
                    exportShareTarget = exportShareTarget,
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    onRenameRequested = { renameOpen = true },
                    onArchiveRequested = { archiveConfirmOpen = true },
                    onRestoreRequested = {
                        val msg = restoreTrack(sessionStore, row.id)
                        onMessage(msg)
                        if (!isReviewActionError(msg)) onDataChanged()
                    },
                    onDeleteRequested = { deleteConfirmOpen = true },
                    onMessage = onMessage,
                    onDataChanged = onDataChanged,
                )
            }
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
                if (!isReviewActionError(msg)) onDataChanged()
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
                if (!isReviewActionError(msg)) onDataChanged()
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
                if (!isReviewActionError(msg)) onDataChanged()
            },
            dismissText = s.cancel,
            dismissIcon = CloseActionIcon,
            dismissIconOnly = true,
            dismissContentDescription = s.cancel,
        )
    }
}

@Composable
private fun ReviewRowActionsMenu(
    row: ReviewRowViewModel,
    sessionStore: LocalSessionStore,
    exportShareTarget: ExportShareTarget,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRenameRequested: () -> Unit,
    onArchiveRequested: () -> Unit,
    onRestoreRequested: () -> Unit,
    onDeleteRequested: () -> Unit,
    onMessage: (String?) -> Unit,
    onDataChanged: () -> Unit,
) {
    val s = strings
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        when (row.type) {
            ReviewEntryType.TimingSession -> {
                DropdownMenuItem(
                    text = { Text(s.exportJson) },
                    leadingIcon = { Icon(ExportActionIcon, contentDescription = null) },
                    onClick = {
                        onDismiss()
                        onMessage(exportTimingSessionJson(row, sessionStore, exportShareTarget))
                    },
                )
                DropdownMenuItem(
                    text = { Text(s.exportGpx) },
                    leadingIcon = { Icon(ExportActionIcon, contentDescription = null) },
                    onClick = {
                        onDismiss()
                        onMessage(exportTimingSessionGpx(row, sessionStore, exportShareTarget))
                    },
                )
                DropdownMenuItem(
                    text = { Text(s.delete) },
                    leadingIcon = { Icon(DeleteActionIcon, contentDescription = null) },
                    onClick = {
                        onDismiss()
                        onDeleteRequested()
                    },
                )
            }
            ReviewEntryType.Track -> {
                if (row.isArchived) {
                    DropdownMenuItem(
                        text = { Text(s.restore) },
                        leadingIcon = { Icon(RestoreActionIcon, contentDescription = null) },
                        onClick = {
                            onDismiss()
                            onRestoreRequested()
                        },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(s.rename) },
                        leadingIcon = { Icon(EditActionIcon, contentDescription = null) },
                        onClick = {
                            onDismiss()
                            onRenameRequested()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(s.duplicate) },
                    leadingIcon = { Icon(DuplicateActionIcon, contentDescription = null) },
                    onClick = {
                        onDismiss()
                        val msg = duplicateTrack(sessionStore, row.id)
                        onMessage(msg)
                        if (!isReviewActionError(msg)) onDataChanged()
                    },
                )
                if (!row.isArchived) {
                    DropdownMenuItem(
                        text = { Text(s.archive) },
                        leadingIcon = { Icon(ArchiveActionIcon, contentDescription = null) },
                        onClick = {
                            onDismiss()
                            onArchiveRequested()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(s.exportJson) },
                    leadingIcon = { Icon(ExportActionIcon, contentDescription = null) },
                    onClick = {
                        onDismiss()
                        onMessage(exportTrackJson(row, sessionStore, exportShareTarget))
                    },
                )
                DropdownMenuItem(
                    text = { Text(s.delete) },
                    leadingIcon = { Icon(DeleteActionIcon, contentDescription = null) },
                    onClick = {
                        onDismiss()
                        onDeleteRequested()
                    },
                )
            }
            ReviewEntryType.TrackMarking -> {
                DropdownMenuItem(
                    text = { Text(s.exportJson) },
                    leadingIcon = { Icon(ExportActionIcon, contentDescription = null) },
                    onClick = {
                        onDismiss()
                        onMessage(exportTrackMarkingJson(row, sessionStore, exportShareTarget))
                    },
                )
                DropdownMenuItem(
                    text = { Text(s.delete) },
                    leadingIcon = { Icon(DeleteActionIcon, contentDescription = null) },
                    onClick = {
                        onDismiss()
                        onDeleteRequested()
                    },
                )
            }
        }
    }
}

private fun reviewActionTone(msg: String): ChipTone =
    if (isReviewActionError(msg)) ChipTone.Error else ChipTone.Ready

private fun isReviewActionError(msg: String): Boolean =
    msg.startsWith("Couldn't") || msg.startsWith("Export failed")

@Composable
private fun ReviewToast(
    message: String,
    tone: ChipTone,
) {
    val spacing = LapSightTheme.spacing
    val color = when (tone) {
        ChipTone.Error -> LapSightTheme.colors.statusError
        else -> LapSightTheme.colors.statusReady
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeContentPadding()
            .padding(spacing.md),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, color),
        ) {
            Text(
                text = message,
                color = color,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(
                    horizontal = spacing.md,
                    vertical = spacing.sm,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun ReviewRowViewModel.localizedTypeLabel(s: LocalizedStrings): String = when (type) {
    ReviewEntryType.TimingSession -> s.sessionEntry
    ReviewEntryType.Track -> s.trackEntry
    ReviewEntryType.TrackMarking -> s.rawCaptureEntry
}

internal fun ReviewRowViewModel.displayTitle(s: LocalizedStrings): String = when (type) {
    ReviewEntryType.TimingSession -> name.ifBlank { s.sessionEntry }
    else -> name.ifBlank { "${s.untitled} ${localizedTypeLabel(s)}" }
}
