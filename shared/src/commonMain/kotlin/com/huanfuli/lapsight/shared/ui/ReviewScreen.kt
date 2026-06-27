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
import androidx.compose.material3.OutlinedButton
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
import com.huanfuli.lapsight.shared.lap.formatLapTime
import com.huanfuli.lapsight.shared.export.ExportArtifact
import com.huanfuli.lapsight.shared.export.ExportFailedException
import com.huanfuli.lapsight.shared.export.ExportFileNames
import com.huanfuli.lapsight.shared.export.ExportNotFoundException
import com.huanfuli.lapsight.shared.export.ExportShareResult
import com.huanfuli.lapsight.shared.export.ExportShareTarget
import com.huanfuli.lapsight.shared.export.GpxExportService
import com.huanfuli.lapsight.shared.export.JsonExportService
import com.huanfuli.lapsight.shared.export.NoOpExportShareTarget
import com.huanfuli.lapsight.shared.review.ReviewSummaries
import com.huanfuli.lapsight.shared.review.TimingSessionReviewSummary
import com.huanfuli.lapsight.shared.review.buildTimingTraceLayers
import com.huanfuli.lapsight.shared.review.buildTrackTraceLayers
import com.huanfuli.lapsight.shared.storage.LoadResult
import com.huanfuli.lapsight.shared.storage.LocalSessionStore
import com.huanfuli.lapsight.shared.track.CreateProfileResult
import com.huanfuli.lapsight.shared.track.CurrentProfileResolution
import com.huanfuli.lapsight.shared.track.CurrentTrackSelection
import com.huanfuli.lapsight.shared.track.ReviewEntryType
import com.huanfuli.lapsight.shared.track.TrackMarkingPayloadV1
import com.huanfuli.lapsight.shared.track.TrackPayloadV1
import com.huanfuli.lapsight.shared.track.TrackProfileController

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
    exportShareTarget: ExportShareTarget = NoOpExportShareTarget,
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
                sessionStore = sessionStore,
                exportShareTarget = exportShareTarget,
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
    sessionStore: LocalSessionStore,
    exportShareTarget: ExportShareTarget,
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
                if (row.type == ReviewEntryType.TimingSession) {
                    TimingSessionReviewDetail(row.id, sessionStore, exportShareTarget)
                } else {
                    RowDetail(row, sessionStore, exportShareTarget)
                }
            }
        }
    }
}

/**
 * Timing Session Review detail (SESS-02, D-32): track name, date, total
 * duration, best lap, lap list, sector splits, GPS quality, source/Demo badge,
 * and "New track best" when applicable.
 */
@Composable
private fun TimingSessionReviewDetail(
    sessionId: String,
    sessionStore: LocalSessionStore,
    exportShareTarget: ExportShareTarget,
) {
    val summary = remember(sessionId) { ReviewSummaries.fromTimingSession(sessionStore, sessionId) }
        ?: run {
            Text(
                text = "Session payload unavailable.",
                color = Color(0xFFFFD166),
                fontSize = 13.sp,
            )
            return
        }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (summary.isDemo) ReviewDemoBadge()
        DetailLine("Track", summary.trackName)
        DetailLine("Date", formatEpochMillis(summary.createdAtEpochMillis))
        DetailLine("Duration", summary.totalDurationMillis.formatLapTime())
        DetailLine("Best lap", summary.bestLapMillis?.formatLapTime() ?: "--")
        DetailLine("Samples", summary.sampleCount.toString())
        if (summary.newTrackBest) {
            Text(
                text = "New track best",
                color = Color(0xFF8CFF9B),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (summary.laps.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Laps",
                color = Color(0xFF7E8DA0),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            summary.laps.forEach { lap ->
                Text(
                    text = "Lap ${lap.lapNumber}: ${lap.durationMillis.formatLapTime()}",
                    color = Color(0xFFCED7E2),
                    fontSize = 14.sp,
                )
            }
        }
        if (summary.sectorSplits.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Sector splits",
                color = Color(0xFF7E8DA0),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            summary.sectorSplits.forEach { sector ->
                Text(
                    text = "${sector.sectorName} (L${sector.lapNumber}): ${sector.splitMillis.formatLapTime()}",
                    color = Color(0xFFCED7E2),
                    fontSize = 14.sp,
                )
            }
        }
        DetailLine("GPS samples", summary.gpsQuality.sampleCount.toString())
        DetailLine(
            "Avg accuracy",
            summary.gpsQuality.averageAccuracyMeters?.let { "${it.toInt()} m" } ?: "--",
        )
        if (summary.isDemo) {
            Text(
                text = "Simulated data — not live history.",
                color = Color(0xFFFFD166),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // Trace section (D-36): render timing session trace.
        TimingTraceSection(
            trackId = summary.trackId,
            sessionId = summary.sessionId,
            bestLapMillis = summary.bestLapMillis,
            sessionStore = sessionStore,
        )

        // Export actions (D-40): explicit button taps on Timing Session detail.
        Spacer(Modifier.height(8.dp))
        var exportMessage by remember { mutableStateOf<String?>(null) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                exportMessage = try {
                    val bytes = JsonExportService(sessionStore).exportTimingSession(sessionId)
                    val fileName = ExportFileNames.forTimingSession(
                        summary.trackName, summary.createdAtEpochMillis, "json"
                    )
                    val artifact = ExportArtifact(fileName, "application/json", bytes)
                    when (exportShareTarget.share(artifact)) {
                        is ExportShareResult.Shared, is ExportShareResult.Saved ->
                            "Exported $fileName"
                        is ExportShareResult.Cancelled -> null
                        is ExportShareResult.Failed -> "Export failed. Check device storage and try again."
                    }
                } catch (e: ExportNotFoundException) {
                    "Export failed. Check device storage and try again."
                } catch (e: ExportFailedException) {
                    "Export failed. Check device storage and try again."
                }
            }) { Text("Export JSON") }
            OutlinedButton(onClick = {
                exportMessage = try {
                    val bytes = GpxExportService(sessionStore).exportTimingSession(sessionId)
                    val fileName = ExportFileNames.forTimingSession(
                        summary.trackName, summary.createdAtEpochMillis, "gpx"
                    )
                    val artifact = ExportArtifact(fileName, "application/gpx+xml", bytes)
                    when (exportShareTarget.share(artifact)) {
                        is ExportShareResult.Shared, is ExportShareResult.Saved ->
                            "Exported $fileName"
                        is ExportShareResult.Cancelled -> null
                        is ExportShareResult.Failed -> "Export failed. Check device storage and try again."
                    }
                } catch (e: ExportNotFoundException) {
                    "Export failed. Check device storage and try again."
                } catch (e: ExportFailedException) {
                    "Export failed. Check device storage and try again."
                }
            }) { Text("Export GPX") }
        }
        exportMessage?.let { msg ->
            Text(
                text = msg,
                color = if (msg.startsWith("Exported")) Color(0xFF8CFF9B) else Color(0xFFFF6B6B),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Renders the offline vector trace for a Timing Session entry (D-36).
 * Loads the track reference line and session samples, builds trace layers,
 * and renders them via [TraceView].
 */
@Composable
private fun TimingTraceSection(
    trackId: String,
    sessionId: String,
    bestLapMillis: Long?,
    sessionStore: LocalSessionStore,
) {
    val trackResult = remember(trackId) { sessionStore.loadTrack(trackId) }
    val track = (trackResult as? LoadResult.Loaded<TrackPayloadV1>)?.value?.track

    val sessionResult = remember(sessionId) { sessionStore.loadTimingSession(sessionId) }
    val sessionPayload = (sessionResult as? LoadResult.Loaded<com.huanfuli.lapsight.shared.session.TimingSessionPayloadV1>)?.value

    val samples = sessionPayload?.samples ?: emptyList()
    val refPoints = track?.referenceLine?.points ?: emptyList()
    val startFinish = track?.startFinish ?: sessionPayload?.session?.startFinish
    val sectors = track?.sectors ?: sessionPayload?.session?.sectors ?: emptyList()

    if (samples.isEmpty() && refPoints.isEmpty()) {
        Text(
            text = "Trace data unavailable.",
            color = Color(0xFF7E8DA0),
            fontSize = 13.sp,
        )
        return
    }

    // Determine best lap time range for highlight.
    val bestLapStart: Long? = null
    val bestLapEnd: Long? = null
    // For now, highlight is derived from bestLapMillis by finding the matching
    // lap in the payload. We use null to avoid complex time-range calculations.
    val selectedStart: Long? = bestLapStart
    val selectedEnd: Long? = bestLapEnd

    val layers = buildTimingTraceLayers(
        referenceLinePoints = refPoints,
        sessionSamples = samples,
        startFinish = startFinish,
        sectors = sectors,
        selectedLapStartMillis = selectedStart,
        selectedLapEndMillis = selectedEnd,
        viewWidth = 400.0,
        viewHeight = 300.0,
    )

    if (layers.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Trace",
            color = Color(0xFF7E8DA0),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        TraceView(layers = layers, minHeight = 180.dp, maxHeight = 260.dp)
    }
}

@Composable
private fun RowDetail(row: ReviewRowViewModel, sessionStore: LocalSessionStore, exportShareTarget: ExportShareTarget) {
    var exportMessage by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DetailLine("ID", row.id)
        DetailLine("Type", row.typeLabel)
        DetailLine("Source", row.sourceLabel)
        if (row.sampleCount != null) DetailLine("Samples", row.sampleCount.toString())
        DetailLine("Payload", row.payloadPath)

        // Trace section for Track and TrackMarking entries (D-35).
        if (row.type == ReviewEntryType.Track || row.type == ReviewEntryType.TrackMarking) {
            TrackTraceSection(rowId = row.id, type = row.type, sessionStore = sessionStore)
        }

        // DEMO provenance stays visible in the expanded detail too (T-03-10).
        if (row.isDemo) {
            Text(
                text = "Simulated data — not live history.",
                color = Color(0xFFFFD166),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // Set as current track (D-02): make this Track the explicit current selection
        // from detail, WITHOUT starting a session. Older V1-only Tracks are promoted to
        // a V2 profile on demand so selection always resolves a real aggregate.
        if (row.type == ReviewEntryType.Track) {
            Spacer(Modifier.height(6.dp))
            var selectMessage by remember(row.id) { mutableStateOf<String?>(null) }
            OutlinedButton(onClick = {
                selectMessage = setAsCurrentTrack(sessionStore, row.id)
            }) { Text("Set as current track") }
            selectMessage?.let { msg ->
                Text(
                    text = msg,
                    color = if (msg.startsWith("Couldn't")) Color(0xFFFF6B6B) else Color(0xFF8CFF9B),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Export actions (D-40): explicit button tap on Track Review detail only.
        if (row.type == ReviewEntryType.Track) {
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = {
                exportMessage = try {
                    val store = sessionStore
                    val bytes = JsonExportService(store).exportTrack(row.id)
                    val name = ExportFileNames.forTrack(
                        row.name, row.createdAtEpochMillis ?: 0L
                    )
                    val artifact = ExportArtifact(name, "application/json", bytes)
                    when (exportShareTarget.share(artifact)) {
                        is ExportShareResult.Shared, is ExportShareResult.Saved ->
                            "Exported $name"
                        is ExportShareResult.Cancelled -> null
                        is ExportShareResult.Failed -> "Export failed. Check device storage and try again."
                    }
                } catch (e: ExportNotFoundException) {
                    "Export failed. Check device storage and try again."
                } catch (e: ExportFailedException) {
                    "Export failed. Check device storage and try again."
                }
            }) { Text("Export JSON") }
            exportMessage?.let { msg ->
                Text(
                    text = msg,
                    color = if (msg.startsWith("Exported")) Color(0xFF8CFF9B) else Color(0xFFFF6B6B),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Renders the offline vector trace for a Track or TrackMarking entry (D-35).
 * Loads the canonical payload (and marking session if available) from the store,
 * builds trace layers, and renders them via [TraceView].
 */
@Composable
private fun TrackTraceSection(
    rowId: String,
    type: ReviewEntryType,
    sessionStore: LocalSessionStore,
) {
    val trackResult = remember(rowId) { sessionStore.loadTrack(rowId) }
    val track = (trackResult as? LoadResult.Loaded<TrackPayloadV1>)?.value?.track

    val markingId = track?.sourceMarkingSessionId
    val markingResult = remember(markingId) {
        markingId?.let { sessionStore.loadTrackMarking(it) }
    }
    val marking = (markingResult as? LoadResult.Loaded<TrackMarkingPayloadV1>)?.value?.marking

    val samples = when {
        marking != null -> marking.samples
        type == ReviewEntryType.TrackMarking -> {
            // Fallback: load marking directly by row id.
            val directMarking = (sessionStore.loadTrackMarking(rowId) as? LoadResult.Loaded<TrackMarkingPayloadV1>)?.value?.marking
            directMarking?.samples ?: emptyList()
        }
        else -> emptyList()
    }

    if (samples.isEmpty()) {
        Text(
            text = "Trace data unavailable.",
            color = Color(0xFF7E8DA0),
            fontSize = 13.sp,
        )
        return
    }

    val layers = buildTrackTraceLayers(
        markingSamples = samples,
        referenceLine = track?.referenceLine,
        startFinish = track?.startFinish,
        sectors = track?.sectors ?: emptyList(),
        outlierSamples = emptyList(),
        viewWidth = 400.0,
        viewHeight = 300.0,
    )

    if (layers.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Trace",
            color = Color(0xFF7E8DA0),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        TraceView(layers = layers, minHeight = 180.dp, maxHeight = 260.dp)
    }
}

/**
 * Makes the Track identified by [trackId] the explicit current selection (D-02),
 * promoting a V1-only Track to a V2 profile first if needed. Returns a short status
 * message for the detail surface. Never starts a Timing session and never derives a
 * different Track (D-03/D-04).
 */
private fun setAsCurrentTrack(store: LocalSessionStore, trackId: String): String {
    val controller = TrackProfileController(store)
    // Ensure a V2 profile (profileId == trackId) exists before selecting it.
    if (store.loadProfile(trackId) !is LoadResult.Loaded) {
        val payload = (store.loadTrack(trackId) as? LoadResult.Loaded<TrackPayloadV1>)?.value
            ?: return "Couldn't load this track."
        val created = controller.saveProfile(
            track = payload.track,
            name = payload.track.name,
            app = payload.app,
        )
        if (created is CreateProfileResult.Rejected) {
            return "Couldn't set as current: ${created.reason}."
        }
    }
    store.setCurrentSelection(CurrentTrackSelection(profileId = trackId))
    return when (controller.resolveCurrent()) {
        is CurrentProfileResolution.Selected -> "Set as current track."
        CurrentProfileResolution.NotTimingReady ->
            "Set as current. Add a start/finish before timing."
        else -> "Set as current track."
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

/**
 * Formats epoch millis as a simple `YYYY-MM-DD HH:MM` UTC label without
 * platform date dependencies (Kotlin Multiplatform common code).
 */
private fun formatEpochMillis(epochMillis: Long): String {
    val secondsTotal = epochMillis / 1000
    val year = 1970
    // Minimal UTC breakdown; sufficient for review display without java.time.
    val daysTotal = secondsTotal / 86_400
    val (y, m, d) = gregorianFromEpochDays(daysTotal)
    val secsOfDay = secondsTotal % 86_400
    val hh = (secsOfDay / 3600).toString().padStart(2, '0')
    val mm = ((secsOfDay % 3600) / 60).toString().padStart(2, '0')
    val dd = d.toString().padStart(2, '0')
    val mm2 = m.toString().padStart(2, '0')
    return "$y-$mm2-$dd $hh:$mm"
}

private fun gregorianFromEpochDays(daysSinceEpoch: Long): Triple<Int, Int, Int> {
    // Howard Hinnant's civil-from-days algorithm (public domain).
    var z = daysSinceEpoch + 719468
    val era = if (z >= 0) z / 146097 else (z - 146096) / 146097
    val doe = (z - era * 146097).toInt()
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    var y = yoe + (era * 400).toInt()
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    if (m <= 2) y += 1
    return Triple(y, m, d)
}
