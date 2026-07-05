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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import com.huanfuli.lapsight.shared.DriveDisplaySettings
import com.huanfuli.lapsight.shared.export.ExportShareTarget
import com.huanfuli.lapsight.shared.export.NoOpExportShareTarget
import com.huanfuli.lapsight.shared.lap.formatLapTime
import com.huanfuli.lapsight.shared.storage.LocalSessionStore
import com.huanfuli.lapsight.shared.track.ReviewEntryType
import com.huanfuli.lapsight.shared.ui.components.ChipTone
import com.huanfuli.lapsight.shared.ui.components.LapCard
import com.huanfuli.lapsight.shared.ui.components.SectionHeader
import com.huanfuli.lapsight.shared.ui.components.StatusChip
import com.huanfuli.lapsight.shared.ui.components.TimingText

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
    var rows by remember { mutableStateOf(ReviewListState.from(sessionStore.readIndex())) }
    var openedKey by remember { mutableStateOf<String?>(null) }
    var localRefreshVersion by remember { mutableStateOf(0L) }
    val refreshRows: () -> Unit = { localRefreshVersion += 1L }

    // Re-read the index when the Drive tab reports a new save (D-27).
    LaunchedEffect(savedVersion, localRefreshVersion) {
        rows = ReviewListState.from(sessionStore.readIndex())
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
        reviewSection("Sessions", sessions) { openedKey = it }
        reviewSection("Tracks", tracks) { openedKey = it }
        reviewSection("Raw captures", rawCaptures) { openedKey = it }
    }
}

internal fun ReviewRowViewModel.key(): String = "${type.name}:$id"

private fun LazyListScope.reviewSection(
    title: String,
    rows: List<ReviewRowViewModel>,
    onOpen: (String) -> Unit,
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
        ReviewRow(row = row, onClick = { onOpen(row.key()) })
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

/**
 * Compact list card: identity on the left, the row's one headline fact on the
 * right (best lap for sessions), and a chevron that signals navigation. All
 * fields come from the lightweight index — no payload I/O per row.
 */
@Composable
private fun ReviewRow(
    row: ReviewRowViewModel,
    onClick: () -> Unit,
) {
    val spacing = LapSightTheme.spacing
    LapCard(onClick = onClick) {
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
                        text = row.displayTitle(),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (row.isDemo) StatusChip(text = "DEMO", tone = ChipTone.Demo)
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
                        text = "BEST",
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
            Text(
                text = "›",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

internal fun ReviewRowViewModel.displayTitle(): String = when (type) {
    ReviewEntryType.TimingSession -> name.ifBlank { "Session" }
    else -> name.ifBlank { "Untitled $typeLabel" }
}
