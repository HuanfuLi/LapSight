package com.huanfuli.lapsight.shared.ui.drive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huanfuli.lapsight.shared.ui.LapSightAutoSize
import com.huanfuli.lapsight.shared.DashOrientation
import com.huanfuli.lapsight.shared.DriveDisplaySettings
import com.huanfuli.lapsight.shared.LocationFeedMode
import com.huanfuli.lapsight.shared.SpeedUnit
import com.huanfuli.lapsight.shared.ghost.DeltaDisplayState
import com.huanfuli.lapsight.shared.ghost.DeltaTone
import com.huanfuli.lapsight.shared.lap.formatLapTime
import com.huanfuli.lapsight.shared.nowEpochMillis
import com.huanfuli.lapsight.shared.session.TimingRunSnapshot
import com.huanfuli.lapsight.shared.ui.LapSightTheme
import com.huanfuli.lapsight.shared.ui.RotateScreenIcon
import com.huanfuli.lapsight.shared.ui.StopActionIcon
import com.huanfuli.lapsight.shared.ui.components.TimingText
import kotlinx.coroutines.delay

/**
 * Fullscreen timing surface shown while a formal timing session is running (D-29).
 *
 * Priority order while moving (UI-SPEC Timing Surface Layout): current lap time is
 * the primary display, the live delta is the second core readout and value-only
 * (`--` / `+0.421s` / `-0.218s`, D-13/D-14), and last/best/laps/speed/accuracy are
 * compact secondary metrics that keep working even when the delta is `--`
 * (D-19/T-04-11). All numerals render in the mono timing face — tabular digits
 * never horizontal-jitter as values tick (UI-SPEC Display role). The surface stays
 * passive while moving (no charts/maps beyond the optional speed trace, D-16).
 */
@Composable
internal fun TimingRunSurface(
    timingRun: TimingRunSnapshot,
    orientation: DashOrientation,
    isLandscapeWindow: Boolean,
    displaySettings: DriveDisplaySettings,
    locationFeedMode: LocationFeedMode,
    onToggleOrientation: () -> Unit,
    onStopTiming: () -> Unit,
    isCompactLandscape: Boolean,
    padding: Dp,
) {
    var displayMillis by remember(timingRun.isActive) { mutableStateOf(0L) }
    var lastLapMillisSeen by remember(timingRun.isActive) { mutableStateOf<Long?>(null) }
    var lastUpdateEpoch by remember(timingRun.isActive) { mutableStateOf(nowEpochMillis()) }
    var speedHistory by remember(timingRun.isActive) { mutableStateOf(emptyList<Float>()) }

    LaunchedEffect(timingRun.currentLapMillis) {
        if (timingRun.isActive) {
            val base = timingRun.currentLapMillis ?: 0L
            displayMillis = base
            lastLapMillisSeen = base
            lastUpdateEpoch = nowEpochMillis()
        } else {
            displayMillis = 0L
            lastLapMillisSeen = null
        }
    }

    LaunchedEffect(timingRun.isActive) {
        while (timingRun.isActive) {
            delay(16)
            if (lastLapMillisSeen != null) {
                val now = nowEpochMillis()
                val delta = now - lastUpdateEpoch
                if (delta >= 0) {
                    displayMillis = lastLapMillisSeen!! + delta
                }
            }
        }
    }

    LaunchedEffect(timingRun.checkpointedSampleCount) {
        timingRun.speedMetersPerSecond?.let { speed ->
            speedHistory = (speedHistory + speed.toFloat()).takeLast(90)
        }
    }

    val speedMultiplier = when (displaySettings.speedUnit) {
        SpeedUnit.KilometersPerHour -> 3.6
        SpeedUnit.MilesPerHour -> 2.2369362921
    }
    val speedUnit = when (displaySettings.speedUnit) {
        SpeedUnit.KilometersPerHour -> "km/h"
        SpeedUnit.MilesPerHour -> "mph"
    }
    val speedLabel = timingRun.speedMetersPerSecond
        ?.let { (it * speedMultiplier).toInt().toString() } ?: "--"
    val accuracyLabel = timingRun.accuracyMeters
        ?.let { (if (it < 0) 0.0 else it).toInt().toString() } ?: "--"
    val sectorValue = if (timingRun.sectorCount > 0) {
        "${timingRun.currentSectorNumber ?: "--"}/${timingRun.sectorCount}"
    } else {
        "--"
    }
    val sourceLabel = when (locationFeedMode) {
        LocationFeedMode.PhoneGps -> "PHONE"
        LocationFeedMode.Simulated -> "SIM"
    }
    val metrics = buildList {
        add(TelemetryMetric("SOURCE", sourceLabel))
        add(TelemetryMetric("LAST", timingRun.lastLapMillis.formatLapTime()))
        add(TelemetryMetric("BEST", timingRun.bestLapMillis.formatLapTime()))
        add(TelemetryMetric("REFERENCE", timingRun.referenceLapMillis.formatLapTime()))
        add(TelemetryMetric("LAP", timingRun.currentLapNumber?.toString() ?: "--"))
        add(TelemetryMetric("COMPLETED", timingRun.lapCount.toString()))
        add(TelemetryMetric("SECTOR", sectorValue))
        add(
            TelemetryMetric(
                timingRun.latestSectorName?.uppercase() ?: "LAST SPLIT",
                timingRun.latestSectorSplitMillis.formatLapTime(),
            ),
        )
        add(TelemetryMetric("SESSION", timingRun.sessionElapsedMillis.formatLapTime()))
        if (displaySettings.showGpsDiagnostics) {
            add(TelemetryMetric("GPS ACCURACY", accuracyLabel, "m"))
            add(
                TelemetryMetric(
                    "GPS RATE",
                    timingRun.sampleRateHz?.let(::formatOneDecimal) ?: "--",
                    "Hz",
                ),
            )
            add(
                TelemetryMetric(
                    "HEADING",
                    timingRun.headingDegrees?.toInt()?.toString() ?: "--",
                    "deg",
                ),
            )
            add(
                TelemetryMetric(
                    "ALTITUDE",
                    timingRun.altitudeMeters?.let(::formatOneDecimal) ?: "--",
                    "m",
                ),
            )
        }
    }

    val spacing = LapSightTheme.spacing
    // The dash surface drops to the deepest background: instrument mode.
    val dashModifier = Modifier
        .fillMaxSize()
        .background(LapSightTheme.colors.dashBackground)
        .padding(padding)
    // Layout follows the ACTUAL window shape, not the requested lock: on
    // platforms where the lock is a no-op (iOS NoOpOrientationController) the
    // toggle state and the real window can disagree, and a portrait column
    // painted into a landscape window would clip the controls.
    if (isLandscapeWindow) {
        Row(
            modifier = dashModifier,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                PrimaryTimingReadouts(
                    displayMillis = displayMillis,
                    timingRun = timingRun,
                    speedLabel = speedLabel,
                    speedUnit = speedUnit,
                    compact = true,
                )
                if (displaySettings.showSpeedTrace) {
                    SpeedTrace(
                        samples = speedHistory,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                TimingControls(
                    orientation = orientation,
                    onToggleOrientation = onToggleOrientation,
                    onStopTiming = onStopTiming,
                )
            }
            TelemetryGrid(
                metrics = metrics,
                compact = true,
                modifier = Modifier.weight(1.15f).fillMaxHeight(),
            )
        }
    } else {
        Column(
            modifier = dashModifier,
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            PrimaryTimingReadouts(
                displayMillis = displayMillis,
                timingRun = timingRun,
                speedLabel = speedLabel,
                speedUnit = speedUnit,
                compact = false,
            )
            if (displaySettings.showSpeedTrace) {
                SpeedTrace(
                    samples = speedHistory,
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                )
            }
            TelemetryGrid(
                metrics = metrics,
                compact = true,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            TimingControls(
                orientation = orientation,
                onToggleOrientation = onToggleOrientation,
                onStopTiming = onStopTiming,
            )
        }
    }
}

private data class TelemetryMetric(
    val label: String,
    val value: String,
    val unit: String = "",
)

@Composable
private fun PrimaryTimingReadouts(
    displayMillis: Long,
    timingRun: TimingRunSnapshot,
    speedLabel: String,
    speedUnit: String,
    compact: Boolean,
) {
    val spacing = LapSightTheme.spacing
    val mono = LapSightTheme.monoFamily
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "CURRENT LAP",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
        // Glance-safe hero readout (D-31): the display role anchors the type and
        // autoSize shrinks to fit the viewport so long lap times never clip.
        Text(
            text = displayMillis.formatLapTime(),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(
                minFontSize = LapSightAutoSize.heroMin,
                maxFontSize = if (compact) LapSightAutoSize.heroMaxCompact else LapSightAutoSize.heroMax,
                stepSize = LapSightAutoSize.step,
            ),
            style = MaterialTheme.typography.displayLarge.copy(fontFamily = mono),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                DeltaReadout(
                    display = timingRun.deltaDisplay,
                    compact = true,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SPEED",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = speedLabel,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = LapSightAutoSize.speedMin,
                            maxFontSize = if (compact) LapSightAutoSize.speedMaxCompact else LapSightAutoSize.speedMax,
                            stepSize = LapSightAutoSize.step,
                        ),
                        style = MaterialTheme.typography.displaySmall.copy(fontFamily = mono),
                    )
                    Text(
                        text = speedUnit,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        modifier = Modifier.padding(start = spacing.xs, bottom = spacing.xs),
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryGrid(
    metrics: List<TelemetryMetric>,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = LapSightTheme.spacing
    val rows = metrics.chunked(3)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                row.forEach { metric ->
                    TelemetryCell(
                        metric = metric,
                        compact = compact,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TelemetryCell(
    metric: TelemetryMetric,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = LapSightTheme.spacing
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .padding(spacing.sm),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = metric.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
            Spacer(Modifier.height(spacing.xs))
            Row(verticalAlignment = Alignment.Bottom) {
                TimingText(
                    text = metric.value,
                    style = if (compact) {
                        MaterialTheme.typography.titleSmall
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                )
                if (metric.unit.isNotEmpty()) {
                    Text(
                        text = metric.unit,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = spacing.xs, bottom = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedTrace(
    samples: List<Float>,
    modifier: Modifier = Modifier,
) {
    val axisColor = LapSightTheme.colors.chartGrid
    val lineColor = MaterialTheme.colorScheme.primary
    val spacing = LapSightTheme.spacing
    Canvas(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .padding(spacing.sm),
    ) {
        drawLine(
            color = axisColor,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1f,
        )
        if (samples.size < 2) return@Canvas
        val maxSpeed = samples.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val xStep = size.width / (samples.size - 1)
        samples.zipWithNext().forEachIndexed { index, pair ->
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
    }
}

@Composable
private fun TimingControls(
    orientation: DashOrientation,
    onToggleOrientation: () -> Unit,
    onStopTiming: () -> Unit,
) {
    val spacing = LapSightTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        // Timing-active controls keep the 56dp floor (04-UI-SPEC).
        Button(
            onClick = onStopTiming,
            modifier = Modifier.weight(1f).height(56.dp),
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Icon(
                imageVector = StopActionIcon,
                contentDescription = "Stop timing",
                modifier = Modifier.size(28.dp),
            )
        }
        Button(
            onClick = onToggleOrientation,
            modifier = Modifier.weight(1f).height(56.dp),
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(
                imageVector = RotateScreenIcon,
                contentDescription =
                    if (orientation == DashOrientation.Portrait) "Switch to landscape" else "Switch to portrait",
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * Value-only live-delta readout (D-13, D-14, D-15, D-18).
 *
 * Renders exactly the display text (`--`, `+0.421s`, `-0.218s`) in the semantic
 * tone color. No words, no explanatory copy, no stale value — the
 * [DeltaDisplayState] already collapsed unavailable states to `--`/neutral.
 */
@Composable
private fun DeltaReadout(
    display: DeltaDisplayState,
    compact: Boolean,
) {
    Text(
        text = "DELTA",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall,
    )
    Text(
        text = display.text,
        color = display.tone.toDeltaColor(),
        textAlign = TextAlign.Start,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(
            minFontSize = LapSightAutoSize.deltaMin,
            maxFontSize = if (compact) LapSightAutoSize.deltaMaxCompact else LapSightAutoSize.deltaMax,
            stepSize = LapSightAutoSize.step,
        ),
        style = MaterialTheme.typography.displayMedium.copy(fontFamily = LapSightTheme.monoFamily),
    )
}

/**
 * Racing-convention delta colors (LapSightColors semantic layer): green =
 * faster than reference, red = slower, gray = neutral / unavailable.
 */
@Composable
internal fun DeltaTone.toDeltaColor(): Color = when (this) {
    DeltaTone.Faster -> LapSightTheme.colors.deltaFaster
    DeltaTone.Slower -> LapSightTheme.colors.deltaSlower
    DeltaTone.Neutral -> LapSightTheme.colors.deltaNeutral
}
