package com.huanfuli.lapsight.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanfuli.lapsight.shared.storage.LocalSessionStore
import com.huanfuli.lapsight.shared.track.ReviewEntryType

/**
 * Review tab (D-27, D-28): lists saved Tracks and marking entries from the
 * local-first store, with visible `DEMO` badges and source metadata (D-42,
 * T-03-10). Re-reads the index whenever the Drive tab saves a track
 * ([savedVersion] changes). Shows the empty state when nothing is saved yet.
 *
 * Row-to-detail: tapping a row expands an inline detail summary for this plan;
 * full detail screens arrive in Plan 03-06/03-07.
 */
@Composable
fun ReviewScreen(
    sessionStore: LocalSessionStore,
    savedVersion: Long,
) {
    var rows by remember { mutableStateOf(ReviewListState.from(sessionStore.readIndex())) }
    var selectedId by remember { mutableStateOf<String?>(null) }

    // Re-read the index when the Drive tab reports a new save (D-27).
    LaunchedEffect(savedVersion) {
        rows = ReviewListState.from(sessionStore.readIndex())
    }

    if (rows.isEmpty()) {
        EmptyState()
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(rows) { row ->
            ReviewRow(
                row = row,
                expanded = selectedId == row.id,
                onClick = {
                    selectedId = if (selectedId == row.id) null else row.id
                },
            )
        }
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
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = "No saved sessions yet",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Mark a track, then start a timing session. Saved tracks and sessions show up here.",
                color = Color(0xFFCED7E2),
                fontSize = 16.sp,
                lineHeight = 22.sp,
            )
        }
    }
}

@Composable
private fun ReviewRow(
    row: ReviewRowViewModel,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.name.ifBlank { "Untitled ${row.typeLabel}" },
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (row.isDemo) ReviewDemoBadge()
            }
            Text(
                text = "${row.typeLabel} · ${row.sourceLabel}",
                color = Color(0xFF9AA8B8),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            if (expanded) {
                RowDetail(row)
            }
        }
    }
}

@Composable
private fun RowDetail(row: ReviewRowViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DetailLine("ID", row.id)
        DetailLine("Type", row.typeLabel)
        DetailLine("Source", row.sourceLabel)
        if (row.sampleCount != null) DetailLine("Samples", row.sampleCount.toString())
        DetailLine("Payload", row.payloadPath)
        Text(
            text = "Full detail arrives in Plan 03-06/03-07.",
            color = Color(0xFF7E8DA0),
            fontSize = 13.sp,
        )
        // DEMO provenance stays visible in the expanded detail too (T-03-10).
        if (row.isDemo) {
            Text(
                text = "Simulated data — not live history.",
                color = Color(0xFFFFD166),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            color = Color(0xFF7E8DA0),
            fontSize = 13.sp,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            color = Color(0xFFCED7E2),
            fontSize = 13.sp,
        )
    }
}

/** Amber "DEMO" pill for saved review rows (D-42, T-03-10). */
@Composable
private fun ReviewDemoBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF101722))
            .border(1.dp, Color(0xFFFFD166), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = "DEMO",
            color = Color(0xFFFFD166),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
