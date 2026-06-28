package com.huanfuli.lapsight.shared.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanfuli.lapsight.shared.DriveDisplaySettings
import com.huanfuli.lapsight.shared.SpeedUnit
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
import com.huanfuli.lapsight.shared.review.ReviewTelemetryPoint
import com.huanfuli.lapsight.shared.review.ReviewSummaries
import com.huanfuli.lapsight.shared.review.TimingSessionReviewSummary
import com.huanfuli.lapsight.shared.review.buildTelemetrySeries
import com.huanfuli.lapsight.shared.review.buildTimingTraceLayers
import com.huanfuli.lapsight.shared.review.buildTrackTraceLayers
import com.huanfuli.lapsight.shared.nowEpochMillis
import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.session.LocationSampleDto
import com.huanfuli.lapsight.shared.storage.LoadResult
import com.huanfuli.lapsight.shared.storage.LocalSessionStore
import com.huanfuli.lapsight.shared.track.AppendRevisionResult
import com.huanfuli.lapsight.shared.track.ArchiveProfileResult
import com.huanfuli.lapsight.shared.track.CreateProfileResult
import com.huanfuli.lapsight.shared.track.CurrentProfileResolution
import com.huanfuli.lapsight.shared.track.CurrentTrackSelection
import com.huanfuli.lapsight.shared.track.DuplicateProfileResult
import com.huanfuli.lapsight.shared.track.RenameProfileResult
import com.huanfuli.lapsight.shared.track.ReviewEntryType
import com.huanfuli.lapsight.shared.track.TrackMarkingPayloadV1
import com.huanfuli.lapsight.shared.track.TrackPayloadV1
import com.huanfuli.lapsight.shared.track.TrackProfile
import com.huanfuli.lapsight.shared.track.TrackProfileController
import kotlin.math.max

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
    displaySettings: DriveDisplaySettings,
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

    val sessions = rows.filter { it.type == ReviewEntryType.TimingSession }
    val tracks = rows.filter { it.type == ReviewEntryType.Track }
    val rawCaptures = rows.filter { it.type == ReviewEntryType.TrackMarking }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        reviewSection("Sessions", sessions, selectedId, { selectedId = it }, sessionStore, displaySettings, exportShareTarget)
        reviewSection("Tracks", tracks, selectedId, { selectedId = it }, sessionStore, displaySettings, exportShareTarget)
        reviewSection("Raw captures", rawCaptures, selectedId, { selectedId = it }, sessionStore, displaySettings, exportShareTarget)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.reviewSection(
    title: String,
    rows: List<ReviewRowViewModel>,
    selectedId: String?,
    onSelectedChanged: (String?) -> Unit,
    sessionStore: LocalSessionStore,
    displaySettings: DriveDisplaySettings,
    exportShareTarget: ExportShareTarget,
) {
    if (rows.isEmpty()) return
    item {
        ReviewSectionHeader(title, rows.size)
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
        )
    }
}

@Composable
private fun ReviewSectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = count.toString(),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
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
                color = MaterialTheme.colorScheme.onBackground,
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
    displaySettings: DriveDisplaySettings,
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
                    text = row.displayTitle(),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (row.isDemo) ReviewDemoBadge()
            }
            Text(
                text = "${row.typeLabel} · ${row.sourceLabel}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            if (expanded) {
                if (row.type == ReviewEntryType.TimingSession) {
                    TimingSessionReviewDetail(row.id, sessionStore, displaySettings, exportShareTarget)
                } else {
                    RowDetail(row, sessionStore, exportShareTarget)
                }
            }
        }
    }
}

private fun ReviewRowViewModel.displayTitle(): String = when (type) {
    ReviewEntryType.TimingSession -> "Session ${formatEpochMillis(createdAtEpochMillis)}"
    else -> name.ifBlank { "Untitled ${typeLabel}" }
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
    displaySettings: DriveDisplaySettings,
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
    val sessionPayload = remember(sessionId) {
        (sessionStore.loadTimingSession(sessionId) as? LoadResult.Loaded<com.huanfuli.lapsight.shared.session.TimingSessionPayloadV1>)?.value
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            summary.laps.forEach { lap ->
                Text(
                    text = "Lap ${lap.lapNumber}: ${lap.durationMillis.formatLapTime()}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                )
            }
        }
        if (summary.sectorSplits.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Sector splits",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            summary.sectorSplits.forEach { sector ->
                Text(
                    text = "${sector.sectorName} (L${sector.lapNumber}): ${sector.splitMillis.formatLapTime()}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                )
            }
        }
        DetailLine("GPS samples", summary.gpsQuality.sampleCount.toString())
        DetailLine(
            "Avg accuracy",
            summary.gpsQuality.averageAccuracyMeters?.let { "${it.toInt()} m" } ?: "--",
        )
        TelemetryReplaySection(sessionPayload?.samples ?: emptyList(), displaySettings.speedUnit)
        if (summary.isDemo) {
            Text(
                text = "Simulated data — not live history.",
                color = Color(0xFFFFD166),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (summary.coursePreflight.overrideUsed) {
            Text(
                text = "Far-course override applied at start.",
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
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
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
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

@Composable
private fun TelemetryReplaySection(
    samples: List<LocationSampleDto>,
    speedUnit: SpeedUnit,
) {
    val telemetry = remember(samples) { buildTelemetrySeries(samples) }
    if (telemetry.isEmpty()) {
        Text(
            text = "Telemetry unavailable.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        return
    }

    var selectedIndex by remember(telemetry.size) { mutableStateOf(0) }
    var playing by remember(telemetry.size) { mutableStateOf(false) }
    val lastIndex = telemetry.lastIndex
    val safeSelectedIndex = selectedIndex.coerceIn(0, lastIndex)

    LaunchedEffect(playing, telemetry.size) {
        while (playing && telemetry.size > 1) {
            kotlinx.coroutines.delay(250)
            if (selectedIndex >= lastIndex) {
                playing = false
            } else {
                selectedIndex += 1
            }
        }
    }

    val selected = telemetry[safeSelectedIndex]
    val speedMultiplier = when (speedUnit) {
        SpeedUnit.KilometersPerHour -> 3.6
        SpeedUnit.MilesPerHour -> 2.2369362921
    }
    val speedUnitLabel = when (speedUnit) {
        SpeedUnit.KilometersPerHour -> "km/h"
        SpeedUnit.MilesPerHour -> "mph"
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Telemetry replay",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
    )
    SpeedTelemetryChart(
        points = telemetry,
        selectedIndex = safeSelectedIndex,
        modifier = Modifier.fillMaxWidth().height(120.dp),
    )
    if (lastIndex > 0) {
        Slider(
            value = safeSelectedIndex.toFloat(),
            onValueChange = {
                selectedIndex = it.toInt().coerceIn(0, lastIndex)
                playing = false
            },
            valueRange = 0f..lastIndex.toFloat(),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = { playing = !playing },
            modifier = Modifier.weight(1f),
        ) { Text(if (playing) "Pause" else "Play") }
        OutlinedButton(
            onClick = {
                selectedIndex = 0
                playing = false
            },
            modifier = Modifier.weight(1f),
        ) { Text("Restart") }
    }
    DetailLine("Time", selected.elapsedMillis.formatLapTime())
    DetailLine(
        "Speed",
        "${formatOneDecimalReview((selected.smoothedSpeedMetersPerSecond ?: selected.speedMetersPerSecond ?: 0.0) * speedMultiplier)} $speedUnitLabel",
    )
    DetailLine("Distance", "${formatOneDecimalReview(selected.distanceMeters)} m")
    DetailLine("Accuracy", selected.horizontalAccuracyMeters?.let { "${it.toInt()} m" } ?: "--")
}

@Composable
private fun SpeedTelemetryChart(
    points: List<ReviewTelemetryPoint>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val cursorColor = MaterialTheme.colorScheme.secondary
    val surfaceColor = MaterialTheme.colorScheme.surface

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(surfaceColor)
            .padding(8.dp),
    ) {
        drawLine(
            color = axisColor,
            start = androidx.compose.ui.geometry.Offset(0f, size.height),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height),
            strokeWidth = 1f,
        )
        if (points.size >= 2) {
            val speeds = points.map {
                (it.smoothedSpeedMetersPerSecond ?: it.speedMetersPerSecond ?: 0.0).toFloat()
            }
            val maxSpeed = max(1f, speeds.maxOrNull() ?: 1f)
            val xStep = size.width / (points.size - 1)
            speeds.zipWithNext().forEachIndexed { index, pair ->
                drawLine(
                    color = lineColor,
                    start = androidx.compose.ui.geometry.Offset(
                        x = index * xStep,
                        y = size.height - (pair.first / maxSpeed * size.height),
                    ),
                    end = androidx.compose.ui.geometry.Offset(
                        x = (index + 1) * xStep,
                        y = size.height - (pair.second / maxSpeed * size.height),
                    ),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                )
            }
            val x = selectedIndex.coerceIn(0, points.lastIndex) * xStep
            drawLine(
                color = cursorColor,
                start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x, size.height),
                strokeWidth = 2f,
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
    val startFinish = sessionPayload?.session?.startFinish ?: track?.startFinish
    val sectors = sessionPayload?.session?.sectors?.takeIf { it.isNotEmpty() } ?: track?.sectors ?: emptyList()

    if (samples.isEmpty() && refPoints.isEmpty()) {
        Text(
            text = "Trace data unavailable.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        return
    }

    val bestLap = bestLapMillis?.let { best ->
        sessionPayload?.laps?.firstOrNull { it.durationMillis == best }
    }
    val selectedStart: Long? = bestLap?.startMillis
    val selectedEnd: Long? = bestLap?.endMillis

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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

        // Profile lifecycle (SC-01, D-12/D-16, D-01/D-03): rename, archive, and duplicate
        // a saved Track profile without deleting or rebinding history.
        if (row.type == ReviewEntryType.Track) {
            ProfileLifecycleSection(trackId = row.id, sessionStore = sessionStore)
        }

        // Edit course + revision history (SC-02, D-05, D-12..D-14): offline editing of
        // start/finish and Sector layout, saved as an immutable revision.
        if (row.type == ReviewEntryType.Track) {
            EditCourseSection(trackId = row.id, sessionStore = sessionStore)
        }

        // Export actions (D-40): explicit button tap on Track Review detail only.
        if (row.type == ReviewEntryType.Track) {
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = {
                exportMessage = try {
                    val store = sessionStore
                    val bytes = JsonExportService(store).exportTrack(row.id)
                    val name = ExportFileNames.forTrack(
                        row.name, row.createdAtEpochMillis
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

    val profileResult = remember(rowId) { sessionStore.loadProfile(rowId) }
    val profile = (profileResult as? LoadResult.Loaded<TrackProfile>)?.value

    val markingId = track?.sourceMarkingSessionId ?: profile?.latestRevision?.sourceMarkingSessionId
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

    val referenceLine = track?.referenceLine ?: profile?.latestRevision?.referenceLine

    if (samples.isEmpty() && referenceLine?.points.isNullOrEmpty()) {
        Text(
            text = "Trace data unavailable.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        return
    }

    val startFinish = track?.startFinish ?: profile?.latestRevision?.courseSetup?.startFinish
    val sectors = track?.sectors ?: profile?.latestRevision?.courseSetup?.boundaries?.map { 
        com.huanfuli.lapsight.shared.track.SectorLineDto(id = it.id, name = "Sector ${it.order}", order = it.order, pointA = it.pointA, pointB = it.pointB)
    } ?: emptyList()

    val layers = buildTrackTraceLayers(
        markingSamples = samples,
        referenceLine = referenceLine,
        startFinish = startFinish,
        sectors = sectors,
        outlierSamples = emptyList(),
        viewWidth = 400.0,
        viewHeight = 300.0,
    )

    if (layers.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Trace",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        TraceView(layers = layers, minHeight = 180.dp, maxHeight = 260.dp)
    }
}

/**
 * Profile lifecycle controls on Track detail (SC-01; D-12 rename, D-16 archive/duplicate;
 * D-01/D-03 selection clearing).
 *
 * Rename updates the profile's display name without touching its immutable revisions;
 * Archive removes the profile from active selectors while retaining every revision/session/
 * Ghost and — when it was the current Track — clears the selection so Drive returns to the
 * explicit no-selection state rather than auto-picking another Track; Duplicate forks an
 * independent profile with fresh identities. None of these deletes data. A V1-only Track is
 * promoted to a V2 profile on demand so a real aggregate always backs the action.
 */
@Composable
private fun ProfileLifecycleSection(trackId: String, sessionStore: LocalSessionStore) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Profile",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
    )

    var message by remember(trackId) { mutableStateOf<String?>(null) }
    var renameValue by remember(trackId) { mutableStateOf("") }

    // Rename: a non-geometric metadata change (D-12). Blank/unsafe names are rejected by
    // the controller and never form a storage path.
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = renameValue,
        onValueChange = { renameValue = it },
        label = { Text("Rename profile") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            message = renameTrack(sessionStore, trackId, renameValue)
        }) { Text("Rename") }
        OutlinedButton(onClick = {
            message = duplicateTrack(sessionStore, trackId)
        }) { Text("Duplicate") }
        OutlinedButton(onClick = {
            message = archiveTrack(sessionStore, trackId)
        }) { Text("Archive") }
    }
    message?.let { msg ->
        Text(
            text = msg,
            color = if (msg.startsWith("Couldn't")) Color(0xFFFF6B6B) else Color(0xFF8CFF9B),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Offline course-editing surface on Track detail (SC-02, D-05, D-12..D-14).
 *
 * Shows the profile's immutable revision history and, on demand, the offline
 * [TrackEditorScreen] seeded with the latest revision's course setup. A valid save
 * appends a new immutable revision via [TrackProfileController.appendRevision] and the
 * history updates immediately; prior revisions are never overwritten. A V1-only Track
 * is promoted to a V2 profile before editing so a real aggregate always backs the edit.
 */
@Composable
private fun EditCourseSection(trackId: String, sessionStore: LocalSessionStore) {
    val payload = remember(trackId) {
        (sessionStore.loadTrack(trackId) as? LoadResult.Loaded<TrackPayloadV1>)?.value
    }
    // Resolve (promoting if needed) the V2 aggregate; held in state so a save refreshes it.
    var profile by remember(trackId) { mutableStateOf(ensureProfile(sessionStore, trackId)) }
    var editing by remember(trackId) { mutableStateOf(false) }
    var message by remember(trackId) { mutableStateOf<String?>(null) }

    val current = profile
    if (current == null || payload == null) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Course editing unavailable for this entry.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        return
    }

    Spacer(Modifier.height(8.dp))
    Text(
        text = "Revision history",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
    )
    current.revisions.sortedBy { it.ordinal }.forEach { revision ->
        val ready = revision.courseSetup.startFinish != null
        Text(
            text = "Rev ${revision.ordinal} · ${formatEpochMillis(revision.createdAtEpochMillis)} · " +
                if (ready) "timing-ready" else "needs start/finish",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
        )
    }

    message?.let { msg ->
        Text(
            text = msg,
            color = if (msg.startsWith("Couldn't") || msg.startsWith("Edit")) Color(0xFFFF6B6B) else Color(0xFF8CFF9B),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }

    if (!editing) {
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = { editing = true; message = null }) { Text("Edit course") }
        return
    }

    // Edit over the latest revision's canonical reference line + course setup.
    val latest = current.latestRevision
    val referenceLine = latest?.referenceLine ?: payload.track.referenceLine
    if (referenceLine == null || referenceLine.points.isEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = "This track has no reference line to edit.",
            color = Color(0xFFFFD166),
            fontSize = 13.sp,
        )
        OutlinedButton(onClick = { editing = false }) { Text("Close") }
        return
    }

    Spacer(Modifier.height(8.dp))
    TrackEditorScreen(
        referenceLine = referenceLine,
        initialSetup = latest?.courseSetup,
        onSave = { setup ->
            val controller = TrackProfileController(sessionStore)
            when (val result = controller.appendRevision(
                profileId = current.profileId,
                referenceLine = referenceLine,
                courseSetup = setup,
                app = payload.app,
                sourceMarkingSessionId = latest?.sourceMarkingSessionId,
            )) {
                is AppendRevisionResult.Appended -> {
                    profile = result.profile
                    editing = false
                    message = "Saved revision ${result.revision.ordinal}."
                }
                is AppendRevisionResult.Rejected -> {
                    message = "Couldn't save: ${result.reason}."
                }
            }
        },
        onCancel = { editing = false },
    )
}

/**
 * Resolves the V2 [TrackProfile] for [trackId], promoting a V1-only Track to a V2
 * profile if no aggregate exists yet. Returns null when neither a profile nor a
 * loadable Track payload is available.
 */
private fun ensureProfile(store: LocalSessionStore, trackId: String): TrackProfile? {
    (store.loadProfile(trackId) as? LoadResult.Loaded<TrackProfile>)?.let { return it.value }
    val payload = (store.loadTrack(trackId) as? LoadResult.Loaded<TrackPayloadV1>)?.value ?: return null
    val controller = TrackProfileController(store)
    when (controller.saveProfile(payload.track, payload.track.name, payload.app)) {
        is CreateProfileResult.Created ->
            return (store.loadProfile(trackId) as? LoadResult.Loaded<TrackProfile>)?.value
        is CreateProfileResult.Rejected -> return null
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

/**
 * Renames the profile backing [trackId] (D-12), promoting a V1-only Track first if needed.
 * Returns a short status message. The revision history is preserved unchanged.
 */
internal fun renameTrack(store: LocalSessionStore, trackId: String, newName: String): String {
    val profile = ensureProfile(store, trackId) ?: return "Couldn't load this track."
    val app = trackApp(store, trackId) ?: return "Couldn't load this track."
    return when (val result = TrackProfileController(store).renameProfile(profile.profileId, newName, app)) {
        is RenameProfileResult.Renamed -> "Renamed to \"${result.profile.name}\"."
        is RenameProfileResult.Rejected -> "Couldn't rename: ${result.reason}."
    }
}

/**
 * Archives the profile backing [trackId] (D-16) and, when it was the current Track, clears
 * the selection so Drive returns to the explicit no-selection state (D-01/D-03). Never
 * deletes any revision/session/Ghost and never selects a replacement Track.
 */
internal fun archiveTrack(
    store: LocalSessionStore,
    trackId: String,
    now: () -> Long = ::nowEpochMillisSafeUi,
): String {
    val profile = ensureProfile(store, trackId) ?: return "Couldn't load this track."
    val app = trackApp(store, trackId) ?: return "Couldn't load this track."
    return when (val result = TrackProfileController(store).archiveProfile(profile.profileId, app, now)) {
        is ArchiveProfileResult.Archived ->
            if (result.clearedCurrentSelection) {
                "Archived. Current track cleared — pick a track before timing."
            } else {
                "Archived. It stays in history but leaves track selection."
            }
        is ArchiveProfileResult.Rejected -> "Couldn't archive: ${result.reason}."
    }
}

/**
 * Duplicates the profile backing [trackId] into an independent profile with fresh identities
 * (D-16). Returns a short status message. The source profile is left unchanged.
 */
internal fun duplicateTrack(
    store: LocalSessionStore,
    trackId: String,
    now: () -> Long = ::nowEpochMillisSafeUi,
): String {
    val profile = ensureProfile(store, trackId) ?: return "Couldn't load this track."
    val app = trackApp(store, trackId) ?: return "Couldn't load this track."
    val newProfileId = "${profile.profileId}-copy-${now()}"
    val newName = "${profile.name} copy"
    return when (
        val result = TrackProfileController(store)
            .duplicateProfile(profile.profileId, newName, newProfileId, app, now)
    ) {
        is DuplicateProfileResult.Duplicated -> "Duplicated as \"${result.duplicate.name}\"."
        is DuplicateProfileResult.Rejected -> "Couldn't duplicate: ${result.reason}."
    }
}

/** The app metadata stamped on the V1 Track payload backing [trackId], or null if absent. */
private fun trackApp(store: LocalSessionStore, trackId: String): AppMetadata? =
    (store.loadTrack(trackId) as? LoadResult.Loaded<TrackPayloadV1>)?.value?.app

/** Wall-clock epoch millis that never throws (mirrors the controller guard). */
private fun nowEpochMillisSafeUi(): Long = try {
    nowEpochMillis()
} catch (_: Throwable) {
    0L
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
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

private fun formatOneDecimalReview(value: Double): String {
    val scaled = (value * 10.0).toInt()
    return "${scaled / 10}.${scaled % 10}"
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
