// Directory: ui/review — package stays `shared.ui` (see ReviewScreen.kt note).
package com.huanfuli.lapsight.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
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
import com.huanfuli.lapsight.shared.review.ReviewSummaries
import com.huanfuli.lapsight.shared.review.ReviewTelemetryPoint
import com.huanfuli.lapsight.shared.review.buildTelemetrySeries
import com.huanfuli.lapsight.shared.review.buildTimingTraceLayers
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
import com.huanfuli.lapsight.shared.ui.components.StatusChip
import com.huanfuli.lapsight.shared.ui.components.StatusMessage
import com.huanfuli.lapsight.shared.ui.components.TimingText
import com.huanfuli.lapsight.shared.DriveDisplaySettings
import kotlin.math.max

/**
 * Timing Session Review detail (SESS-02, D-32): track name, date, total
 * duration, best lap, lap list, sector splits, GPS quality, source/Demo badge,
 * and "New track best" when applicable.
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

    val spacing = LapSightTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        if (summary.isDemo) StatusChip(text = "DEMO", tone = ChipTone.Demo)
        MetricCell(label = "Track", value = summary.trackName, size = MetricCellSize.Row)
        MetricCell(label = "Date", value = formatEpochMillis(summary.createdAtEpochMillis), size = MetricCellSize.Row)
        MetricCell(label = "Duration", value = summary.totalDurationMillis.formatLapTime(), size = MetricCellSize.Row)
        MetricCell(label = "Best lap", value = summary.bestLapMillis?.formatLapTime() ?: "--", size = MetricCellSize.Row)
        MetricCell(label = "Samples", value = summary.sampleCount.toString(), size = MetricCellSize.Row)
        if (summary.newTrackBest) {
            // Motorsport purple: the fastest-ever marker.
            Text(
                text = "New track best",
                color = LapSightTheme.colors.traceBestLap,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (summary.laps.isNotEmpty()) {
            Spacer(Modifier.height(spacing.xs))
            SectionHeader(text = "Laps")
            summary.laps.forEach { lap ->
                val isBest = summary.bestLapMillis != null && lap.durationMillis == summary.bestLapMillis
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(
                        text = "Lap ${lap.lapNumber}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TimingText(
                        text = lap.durationMillis.formatLapTime(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isBest) {
                            LapSightTheme.colors.traceBestLap
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
        if (summary.sectorSplits.isNotEmpty()) {
            Spacer(Modifier.height(spacing.xs))
            SectionHeader(text = "Sector splits")
            summary.sectorSplits.forEach { sector ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(
                        text = "${sector.sectorName} (L${sector.lapNumber})",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TimingText(
                        text = sector.splitMillis.formatLapTime(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        MetricCell(label = "GPS samples", value = summary.gpsQuality.sampleCount.toString(), size = MetricCellSize.Row)
        MetricCell(
            label = "Avg accuracy",
            value = summary.gpsQuality.averageAccuracyMeters?.let { "${it.toInt()} m" } ?: "--",
            size = MetricCellSize.Row,
        )
        TelemetryReplaySection(sessionPayload?.samples ?: emptyList(), displaySettings.speedUnit)
        if (summary.isDemo) {
            StatusMessage(text = "Simulated data — not live history.", tone = ChipTone.Demo)
        }
        if (summary.coursePreflight.overrideUsed) {
            StatusMessage(text = "Far-course override applied at start.", tone = ChipTone.Caution)
        }

        // Trace section (D-36): render timing session trace.
        TimingTraceSection(
            trackId = summary.trackId,
            sessionId = summary.sessionId,
            bestLapMillis = summary.bestLapMillis,
            sessionStore = sessionStore,
        )

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
            style = MaterialTheme.typography.bodySmall,
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
    val spacing = LapSightTheme.spacing
    Spacer(Modifier.height(spacing.sm))
    SectionHeader(text = "Telemetry replay")
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
    MetricCell(label = "Time", value = selected.elapsedMillis.formatLapTime(), size = MetricCellSize.Row)
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
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val axisColor = LapSightTheme.colors.chartGrid
    val cursorColor = LapSightTheme.colors.statusCaution
    val surfaceColor = MaterialTheme.colorScheme.surface
    val shape = MaterialTheme.shapes.small
    val spacing = LapSightTheme.spacing

    Canvas(
        modifier = modifier
            .clip(shape)
            .background(surfaceColor)
            .padding(spacing.sm),
    ) {
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
            val xStep = size.width / (points.size - 1)
            speeds.zipWithNext().forEachIndexed { index, pair ->
                drawLine(
                    color = lineColor,
                    start = Offset(
                        x = index * xStep,
                        y = size.height - (pair.first / maxSpeed * size.height),
                    ),
                    end = Offset(
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
                start = Offset(x, 0f),
                end = Offset(x, size.height),
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
        Spacer(Modifier.height(LapSightTheme.spacing.sm))
        SectionHeader(text = "Trace")
        Spacer(Modifier.height(LapSightTheme.spacing.xs))
        TraceView(layers = layers, minHeight = 180.dp, maxHeight = 260.dp)
    }
}
