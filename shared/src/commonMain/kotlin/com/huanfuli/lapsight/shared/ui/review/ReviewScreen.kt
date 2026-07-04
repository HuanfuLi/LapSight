// Directory: ui/review — package intentionally stays `shared.ui` so the pure
// helpers (renameTrack, ReviewListState, …) keep their public coordinates for
// the existing common tests.
package com.huanfuli.lapsight.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.huanfuli.lapsight.shared.DriveDisplaySettings
import com.huanfuli.lapsight.shared.export.ExportShareTarget
import com.huanfuli.lapsight.shared.export.NoOpExportShareTarget
import com.huanfuli.lapsight.shared.storage.LocalSessionStore
import com.huanfuli.lapsight.shared.track.ReviewEntryType
import com.huanfuli.lapsight.shared.ui.components.ChipTone
import com.huanfuli.lapsight.shared.ui.components.LapCard
import com.huanfuli.lapsight.shared.ui.components.SectionHeader
import com.huanfuli.lapsight.shared.ui.components.StatusChip

/**
 * Review tab (D-27, D-28): lists saved Tracks and marking entries from the
 * local-first store, with visible `DEMO` badges and source metadata (D-42,
 * T-03-10). Re-reads the index whenever the Drive tab saves a track
 * ([savedVersion] changes). Shows the empty state when nothing is saved yet.
 *
 * Row-to-detail: tapping a row expands an inline detail summary.
 */
@Composable
fun ReviewScreen(
    sessionStore: LocalSessionStore,
    savedVersion: Long,
    displaySettings: DriveDisplaySettings,
    exportShareTarget: ExportShareTarget = NoOpExportShareTarget,
) {
    var rows by remember { mutableStateOf(ReviewListState.from(sessionStore.readIndex())) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var localRefreshVersion by remember { mutableStateOf(0L) }
    val refreshRows: () -> Unit = { localRefreshVersion += 1L }

    // Re-read the index when the Drive tab reports a new save (D-27).
    LaunchedEffect(savedVersion, localRefreshVersion) {
        rows = ReviewListState.from(sessionStore.readIndex())
    }

    if (rows.isEmpty()) {
        EmptyState()
        return
    }

    val sessions = rows.filter { it.type == ReviewEntryType.TimingSession }
    val tracks = rows.filter { it.type == ReviewEntryType.Track }
    val rawCaptures = rows.filter { it.type == ReviewEntryType.TrackMarking }

    val spacing = LapSightTheme.spacing
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        reviewSection("Sessions", sessions, selectedId, { selectedId = it }, sessionStore, displaySettings, exportShareTarget, localRefreshVersion, refreshRows)
        reviewSection("Tracks", tracks, selectedId, { selectedId = it }, sessionStore, displaySettings, exportShareTarget, localRefreshVersion, refreshRows)
        reviewSection("Raw captures", rawCaptures, selectedId, { selectedId = it }, sessionStore, displaySettings, exportShareTarget, localRefreshVersion, refreshRows)
    }
}

private fun LazyListScope.reviewSection(
    title: String,
    rows: List<ReviewRowViewModel>,
    selectedId: String?,
    onSelectedChanged: (String?) -> Unit,
    sessionStore: LocalSessionStore,
    displaySettings: DriveDisplaySettings,
    exportShareTarget: ExportShareTarget,
    refreshVersion: Long,
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
        val key = "${row.type.name}:${row.id}"
        ReviewRow(
            row = row,
            expanded = selectedId == key,
            onClick = {
                onSelectedChanged(if (selectedId == key) null else key)
            },
            sessionStore = sessionStore,
            displaySettings = displaySettings,
            exportShareTarget = exportShareTarget,
            refreshVersion = refreshVersion,
            onDataChanged = onDataChanged,
        )
    }
}

@Composable
private fun EmptyState() {
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
                text = "No saved sessions yet",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(LapSightTheme.spacing.sm))
            Text(
                text = "Mark a track, then start a timing session. Saved tracks and sessions show up here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun ReviewRow(
    row: ReviewRowViewModel,
    expanded: Boolean,
    onClick: () -> Unit,
    sessionStore: LocalSessionStore,
    displaySettings: DriveDisplaySettings,
    exportShareTarget: ExportShareTarget,
    refreshVersion: Long,
    onDataChanged: () -> Unit,
) {
    LapCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LapSightTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.displayTitle(),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (row.isDemo) StatusChip(text = "DEMO", tone = ChipTone.Demo)
        }
        Text(
            text = "${row.typeLabel} · ${row.sourceLabel}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        if (expanded) {
            if (row.type == ReviewEntryType.TimingSession) {
                TimingSessionReviewDetail(row.id, sessionStore, displaySettings, exportShareTarget)
            } else {
                RowDetail(row, sessionStore, exportShareTarget, refreshVersion, onDataChanged)
            }
        }
    }
}

internal fun ReviewRowViewModel.displayTitle(): String = when (type) {
    ReviewEntryType.TimingSession -> "Session ${formatEpochMillis(createdAtEpochMillis)}"
    else -> name.ifBlank { "Untitled $typeLabel" }
}
