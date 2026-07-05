// Directory: ui/review — package stays `shared.ui` (see ReviewScreen.kt note).
package com.huanfuli.lapsight.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.huanfuli.lapsight.shared.SpeedUnit
import com.huanfuli.lapsight.shared.export.ExportArtifact
import com.huanfuli.lapsight.shared.export.ExportFailedException
import com.huanfuli.lapsight.shared.export.ExportFileNames
import com.huanfuli.lapsight.shared.export.ExportNotFoundException
import com.huanfuli.lapsight.shared.export.ExportShareResult
import com.huanfuli.lapsight.shared.export.ExportShareTarget
import com.huanfuli.lapsight.shared.export.GpxExportService
import com.huanfuli.lapsight.shared.export.JsonExportService
import com.huanfuli.lapsight.shared.lap.formatLapTime
import com.huanfuli.lapsight.shared.review.ReviewCompleteSector
import com.huanfuli.lapsight.shared.review.ReviewSummaries
import com.huanfuli.lapsight.shared.review.ReviewTelemetryPoint
import com.huanfuli.lapsight.shared.review.buildLapSectorTable
import com.huanfuli.lapsight.shared.review.buildTelemetrySeries
import com.huanfuli.lapsight.shared.review.buildTimingTraceLayers
import com.huanfuli.lapsight.shared.session.LapDto
import com.huanfuli.lapsight.shared.session.LocationSampleDto
import com.huanfuli.lapsight.shared.session.TimingSessionPayloadV1
import com.huanfuli.lapsight.shared.storage.LoadResult
import com.huanfuli.lapsight.shared.storage.LocalSessionStore
import com.huanfuli.lapsight.shared.track.TrackPayloadV1
import com.huanfuli.lapsight.shared.ui.components.ChipTone
import com.huanfuli.lapsight.shared.ui.components.LapButton
import com.huanfuli.lapsight.shared.ui.components.LapButtonStyle
import com.huanfuli.lapsight.shared.ui.components.MetricCell
import com.huanfuli.lapsight.shared.ui.components.MetricCellSize
import com.huanfuli.lapsight.shared.ui.components.SectionHeader
import com.huanfuli.lapsight.shared.ui.components.SegmentedControl
import com.huanfuli.lapsight.shared.ui.components.StatusChip
import com.huanfuli.lapsight.shared.ui.components.StatusMessage
import com.huanfuli.lapsight.shared.DriveDisplaySettings
import kotlin.math.max

/**
 * Timing Session Review detail (SESS-02, D-32): compact session header, lap ×
 * sector timing table (with BEST/OPT rows), lap-scoped telemetry replay with
 * sector bands, trace with selected-lap highlight, and export actions. The
 * selected lap in the table drives the replay scope and the trace highlight.
 */
@Composable
internal fun TimingSessionReviewDetail(
    sessionId: String,
    sessionStore: LocalSessionStore,
    displaySettings: DriveDisplaySettings,
    exportShareTarget: ExportShareTarget,
) {
    val summary = remember(sessionId) { ReviewSummaries.fromTimingSession(sessionStore, sessionId) }
        ?: run {
            StatusMessage(text = "Session payload unavailable.", tone = ChipTone.Caution)
            return
        }
    val sessionPayload = remember(sessionId) {
        (sessionStore.loadTimingSession(sessionId) as? LoadResult.Loaded<TimingSessionPayloadV1>)?.value
    }
    val table = remember(sessionId) {
        buildLapSectorTable(summary.laps, summary.completeSectors, summary.sectorSplits)
    }
    val bestLapNumber = remember(sessionId) {
        summary.laps.minByOrNull { it.durationMillis }?.lapNumber
    }
    // The lap under inspection: best lap preselected, table rows change it.
    var selectedLapNumber by remember(sessionId) { mutableStateOf(bestLapNumber) }
    val selectedLap: LapDto? = sessionPayload?.laps?.firstOrNull { it.lapNumber == selectedLapNumber }

    val spacing = LapSightTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        // ── Headline times (identity lives in the detail screen's top bar) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            MetricCell(
                label = "Best lap",
                value = summary.bestLapMillis?.formatLapTime() ?: "--",
                tone = if (summary.newTrackBest) LapSightTheme.colors.traceBestLap else null,
                modifier = Modifier.weight(1f),
            )
            MetricCell(
                label = "Optimal",
                value = table.optimalLapMillis?.formatLapTime() ?: "--",
                modifier = Modifier.weight(1f),
            )
            MetricCell(
                label = "Total",
                value = summary.totalDurationMillis.formatLapTime(),
                modifier = Modifier.weight(1f),
            )
        }
        if (summary.newTrackBest) {
            // Motorsport purple: the fastest-ever marker.
            Text(
                text = "New track best",
                color = LapSightTheme.colors.traceBestLap,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        // ── Lap × sector table ───────────────────────────────────────────────
        if (table.rows.isNotEmpty()) {
            Spacer(Modifier.height(spacing.xs))
            SectionHeader(text = "Laps", count = table.rows.size)
            LapSectorTableView(
                table = table,
                selectedLapNumber = selectedLapNumber,
                onSelectLap = { selectedLapNumber = it },
            )
        }

        TelemetryReplaySection(
            samples = sessionPayload?.samples ?: emptyList(),
            speedUnit = displaySettings.speedUnit,
            selectedLap = selectedLap,
            lapSectors = summary.completeSectors.filter { it.lapNumber == selectedLapNumber },
        )
        if (summary.isDemo) {
            StatusMessage(text = "Simulated data — not live history.", tone = ChipTone.Demo)
        }
        if (summary.coursePreflight.overrideUsed) {
            StatusMessage(text = "Far-course override applied at start.", tone = ChipTone.Caution)
        }

        // Trace section (D-36): highlight follows the selected lap.
        TimingTraceSection(
            trackId = summary.trackId,
            sessionId = summary.sessionId,
            selectedLapStartMillis = selectedLap?.startMillis,
            selectedLapEndMillis = selectedLap?.endMillis,
            sessionStore = sessionStore,
        )

        // ── Session data: sample/accuracy details, collapsed by default ─────
        var showSessionData by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable { showSessionData = !showSessionData }
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(text = "Session data", modifier = Modifier.weight(1f))
            Text(
                text = if (showSessionData) "Hide" else "Show",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (showSessionData) {
            MetricCell(label = "Samples", value = summary.sampleCount.toString(), size = MetricCellSize.Row)
            MetricCell(label = "GPS samples", value = summary.gpsQuality.sampleCount.toString(), size = MetricCellSize.Row)
            MetricCell(
                label = "Avg accuracy",
                value = summary.gpsQuality.averageAccuracyMeters?.let { "${it.toInt()} m" } ?: "--",
                size = MetricCellSize.Row,
            )
        }

        // Export actions (D-40): explicit button taps on Timing Session detail.
        Spacer(Modifier.height(spacing.sm))
        var exportMessage by remember { mutableStateOf<String?>(null) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            LapButton(
                text = "Export JSON",
                style = LapButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
                onClick = {
                    exportMessage = try {
                        val bytes = JsonExportService(sessionStore).exportTimingSession(sessionId)
                        val fileName = ExportFileNames.forTimingSession(
                            summary.trackName, summary.createdAtEpochMillis, "json",
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
                },
            )
            LapButton(
                text = "Export GPX",
                style = LapButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
                onClick = {
                    exportMessage = try {
                        val bytes = GpxExportService(sessionStore).exportTimingSession(sessionId)
                        val fileName = ExportFileNames.forTimingSession(
                            summary.trackName, summary.createdAtEpochMillis, "gpx",
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
                },
            )
        }
        exportMessage?.let { msg ->
            StatusMessage(
                text = msg,
                tone = if (msg.startsWith("Exported")) ChipTone.Ready else ChipTone.Error,
            )
        }
    }
}

/** One sector band on the replay chart, as x-fractions of the visible range. */
private data class ChartSectorBand(
    val startFraction: Float,
    val endFraction: Float,
    val label: String,
)

@Composable
private fun TelemetryReplaySection(
    samples: List<LocationSampleDto>,
    speedUnit: SpeedUnit,
    selectedLap: LapDto?,
    lapSectors: List<ReviewCompleteSector>,
) {
    val telemetry = remember(samples) { buildTelemetrySeries(samples) }
    if (telemetry.isEmpty()) {
        Text(
            text = "Telemetry unavailable.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }

    // Replay scope: the selected lap (default) or the whole session.
    var scopeToLap by remember(selectedLap?.lapNumber) { mutableStateOf(selectedLap != null) }
    val lapPoints = if (selectedLap != null) {
        remember(telemetry, selectedLap) {
            telemetry.filter { it.elapsedMillis in selectedLap.startMillis..selectedLap.endMillis }
        }
    } else {
        emptyList()
    }
    val lapScopeUsable = lapPoints.size >= 2
    val scoped = if (scopeToLap && lapScopeUsable) lapPoints else telemetry
    val rangeStartMillis = if (scopeToLap && lapScopeUsable && selectedLap != null) {
        selectedLap.startMillis
    } else {
        telemetry.first().elapsedMillis
    }
    val rangeEndMillis = if (scopeToLap && lapScopeUsable && selectedLap != null) {
        selectedLap.endMillis
    } else {
        telemetry.last().elapsedMillis
    }

    val orderedSectors = remember(lapSectors) { lapSectors.sortedBy { it.sectorOrder } }
    val bands: List<ChartSectorBand> = if (scopeToLap && lapScopeUsable && selectedLap != null) {
        val lapDuration = max(1L, selectedLap.endMillis - selectedLap.startMillis)
        orderedSectors.mapIndexed { index, sector ->
            ChartSectorBand(
                startFraction = ((sector.startedAtMillis - selectedLap.startMillis).toFloat() / lapDuration)
                    .coerceIn(0f, 1f),
                endFraction = ((sector.endedAtMillis - selectedLap.startMillis).toFloat() / lapDuration)
                    .coerceIn(0f, 1f),
                label = "S${index + 1}",
            )
        }
    } else {
        emptyList()
    }

    val scopeKey = "${selectedLap?.lapNumber}:$scopeToLap"
    var selectedIndex by remember(scopeKey, scoped.size) { mutableStateOf(0) }
    var playing by remember(scopeKey, scoped.size) { mutableStateOf(false) }
    val lastIndex = scoped.lastIndex
    val safeSelectedIndex = selectedIndex.coerceIn(0, lastIndex)

    LaunchedEffect(playing, scopeKey, scoped.size) {
        while (playing && scoped.size > 1) {
            kotlinx.coroutines.delay(250)
            if (selectedIndex >= lastIndex) {
                playing = false
            } else {
                selectedIndex += 1
            }
        }
    }

    val selected = scoped[safeSelectedIndex]
    val speedMultiplier = when (speedUnit) {
        SpeedUnit.KilometersPerHour -> 3.6
        SpeedUnit.MilesPerHour -> 2.2369362921
    }
    val speedUnitLabel = when (speedUnit) {
        SpeedUnit.KilometersPerHour -> "km/h"
        SpeedUnit.MilesPerHour -> "mph"
    }
    val spacing = LapSightTheme.spacing
    Spacer(Modifier.height(spacing.sm))
    SectionHeader(text = "Telemetry replay")
    if (selectedLap != null && lapScopeUsable) {
        SegmentedControl(
            options = listOf("Lap ${selectedLap.lapNumber}", "Session"),
            selectedIndex = if (scopeToLap) 0 else 1,
            onSelect = { scopeToLap = it == 0 },
        )
    }
    SpeedTelemetryChart(
        points = scoped,
        selectedIndex = safeSelectedIndex,
        rangeStartMillis = rangeStartMillis,
        rangeEndMillis = rangeEndMillis,
        sectorBands = bands,
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
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        LapButton(
            text = if (playing) "Pause" else "Play",
            onClick = { playing = !playing },
            modifier = Modifier.weight(1f),
        )
        LapButton(
            text = "Restart",
            style = LapButtonStyle.Secondary,
            onClick = {
                selectedIndex = 0
                playing = false
            },
            modifier = Modifier.weight(1f),
        )
    }
    // Time reads relative to the visible range (time into lap when lap-scoped).
    MetricCell(
        label = "Time",
        value = (selected.elapsedMillis - rangeStartMillis).formatLapTime(),
        size = MetricCellSize.Row,
    )
    val currentSector = orderedSectors.indexOfFirst {
        selected.elapsedMillis in it.startedAtMillis..it.endedAtMillis
    }
    if (scopeToLap && currentSector >= 0) {
        MetricCell(label = "Sector", value = "S${currentSector + 1}", size = MetricCellSize.Row)
    }
    MetricCell(
        label = "Speed",
        value = "${formatOneDecimalReview((selected.smoothedSpeedMetersPerSecond ?: selected.speedMetersPerSecond ?: 0.0) * speedMultiplier)} $speedUnitLabel",
        size = MetricCellSize.Row,
    )
    MetricCell(label = "Distance", value = "${formatOneDecimalReview(selected.distanceMeters)} m", size = MetricCellSize.Row)
    MetricCell(
        label = "Accuracy",
        value = selected.horizontalAccuracyMeters?.let { "${it.toInt()} m" } ?: "--",
        size = MetricCellSize.Row,
    )
}

@Composable
private fun SpeedTelemetryChart(
    points: List<ReviewTelemetryPoint>,
    selectedIndex: Int,
    rangeStartMillis: Long,
    rangeEndMillis: Long,
    sectorBands: List<ChartSectorBand>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val axisColor = LapSightTheme.colors.chartGrid
    val bandFillColor = LapSightTheme.colors.chartGrid.copy(alpha = 0.14f)
    val labelColor = LapSightTheme.colors.chartAxisLabel
    val cursorColor = LapSightTheme.colors.statusCaution
    val surfaceColor = MaterialTheme.colorScheme.surface
    val shape = MaterialTheme.shapes.small
    val spacing = LapSightTheme.spacing
    val textMeasurer = rememberTextMeasurer()
    val bandLabelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)

    val rangeMillis = max(1L, rangeEndMillis - rangeStartMillis)
    fun xFractionOf(elapsedMillis: Long): Float =
        ((elapsedMillis - rangeStartMillis).toFloat() / rangeMillis).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .clip(shape)
            .background(surfaceColor)
            .padding(spacing.sm),
    ) {
        // Sector bands behind everything: alternating fill + boundary ticks.
        sectorBands.forEachIndexed { index, band ->
            val left = band.startFraction * size.width
            val right = band.endFraction * size.width
            if (index % 2 == 1) {
                drawRect(
                    color = bandFillColor,
                    topLeft = Offset(left, 0f),
                    size = Size(right - left, size.height),
                )
            }
            if (index > 0) {
                drawLine(
                    color = axisColor,
                    start = Offset(left, 0f),
                    end = Offset(left, size.height),
                    strokeWidth = 1f,
                )
            }
            drawText(
                textMeasurer = textMeasurer,
                text = band.label,
                topLeft = Offset(left + 6f, 2f),
                style = bandLabelStyle,
            )
        }
        drawLine(
            color = axisColor,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1f,
        )
        if (points.size >= 2) {
            val speeds = points.map {
                (it.smoothedSpeedMetersPerSecond ?: it.speedMetersPerSecond ?: 0.0).toFloat()
            }
            val maxSpeed = max(1f, speeds.maxOrNull() ?: 1f)
            for (i in 1 until points.size) {
                drawLine(
                    color = lineColor,
                    start = Offset(
                        x = xFractionOf(points[i - 1].elapsedMillis) * size.width,
                        y = size.height - (speeds[i - 1] / maxSpeed * size.height),
                    ),
                    end = Offset(
                        x = xFractionOf(points[i].elapsedMillis) * size.width,
                        y = size.height - (speeds[i] / maxSpeed * size.height),
                    ),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                )
            }
            val cursorX = xFractionOf(points[selectedIndex.coerceIn(0, points.lastIndex)].elapsedMillis) * size.width
            drawLine(
                color = cursorColor,
                start = Offset(cursorX, 0f),
                end = Offset(cursorX, size.height),
                strokeWidth = 2f,
            )
        }
    }
}

/**
 * Renders the offline vector trace for a Timing Session entry (D-36).
 * Loads the track reference line and session samples, builds trace layers,
 * and renders them via [TraceView]. The highlighted lap follows the table
 * selection (best lap by default).
 */
@Composable
private fun TimingTraceSection(
    trackId: String,
    sessionId: String,
    selectedLapStartMillis: Long?,
    selectedLapEndMillis: Long?,
    sessionStore: LocalSessionStore,
) {
    val trackResult = remember(trackId) { sessionStore.loadTrack(trackId) }
    val track = (trackResult as? LoadResult.Loaded<TrackPayloadV1>)?.value?.track

    val sessionResult = remember(sessionId) { sessionStore.loadTimingSession(sessionId) }
    val sessionPayload = (sessionResult as? LoadResult.Loaded<TimingSessionPayloadV1>)?.value

    val samples = sessionPayload?.samples ?: emptyList()
    val refPoints = track?.referenceLine?.points ?: emptyList()
    val startFinish = sessionPayload?.session?.startFinish ?: track?.startFinish
    val sectors = sessionPayload?.session?.sectors?.takeIf { it.isNotEmpty() } ?: track?.sectors ?: emptyList()

    if (samples.isEmpty() && refPoints.isEmpty()) {
        Text(
            text = "Trace data unavailable.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }

    val layers = buildTimingTraceLayers(
        referenceLinePoints = refPoints,
        sessionSamples = samples,
        startFinish = startFinish,
        sectors = sectors,
        selectedLapStartMillis = selectedLapStartMillis,
        selectedLapEndMillis = selectedLapEndMillis,
        viewWidth = 400.0,
        viewHeight = 300.0,
    )

    if (layers.isNotEmpty()) {
        Spacer(Modifier.height(LapSightTheme.spacing.sm))
        SectionHeader(text = "Trace")
        Spacer(Modifier.height(LapSightTheme.spacing.xs))
        TraceView(layers = layers, minHeight = 180.dp, maxHeight = 260.dp)
    }
}
