package com.huanfuli.lapsight.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Presentation state for the Drive demo GPS feed.
 *
 * Mirrors the provider boundary into Compose: only the latest sample, the
 * running count, and a rolled-up [GpsQualitySummary] are surfaced. There is no
 * demo-only timing workflow here — the feed is the normal provider stream that
 * marking/timing flows will consume in later plans (D-03).
 */
data class DemoFeedUiState(
    val isRunning: Boolean = false,
    val latestSample: LocationSample? = null,
    val sampleCount: Int = 0,
    val quality: GpsQualitySummary? = null,
) {
    val speedKmhLabel: String
        get() = latestSample?.speedMetersPerSecond
            ?.let { (it * 3.6).roundToInt().toString() } ?: "--"

    val accuracyLabel: String
        get() = latestSample?.horizontalAccuracyMeters
            ?.let { max(0.0, it).roundToInt().toString() } ?: "--"

    val sampleCountLabel: String get() = sampleCount.toString()

    val updateRateLabel: String
        get() = quality?.averageUpdateRateHz
            ?.takeIf { it > 0.0 }
            ?.let { formatOneDecimal(it) } ?: "--"

    val fixStatus: GpsFixStatus
        get() = if (isRunning) GpsFixStatus.Simulated else GpsFixStatus.Idle
}

private fun formatOneDecimal(value: Double): String {
    val scaled = (value * 10.0).roundToInt()
    return "${scaled / 10}.${scaled % 10}"
}

@Composable
@Preview
fun App(orientationController: OrientationController = NoOpOrientationController) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF05070A),
            surface = Color(0xFF101722),
            primary = Color(0xFF62E3FF),
            secondary = Color(0xFFFFD166),
        )
    ) {
        LapSightApp(orientationController)
    }
}

@Composable
fun LapSightApp(orientationController: OrientationController = NoOpOrientationController) {
    // Phase 3 slice: the Drive surface owns a provider-layer simulated GPS feed.
    // Real Android/iOS providers will replace this SimulatedGpsProvider behind
    // the same LocationSampleProvider boundary without touching this UI.
    val provider = remember { SimulatedGpsProvider() }
    val collected = remember { mutableStateListOf<LocationSample>() }
    var feed by remember { mutableStateOf(DemoFeedUiState()) }

    // Orientation is an explicit user choice, never sensor-driven (mounted-phone
    // racing G-forces make accelerometer rotation unsafe). This drives a hard
    // window lock via the platform controller; the layout follows the locked
    // window, not the device tilt.
    var orientation by remember { mutableStateOf(DashOrientation.Portrait) }
    LaunchedEffect(orientation) {
        orientationController.apply(orientation)
    }

    // While the feed runs, poll the provider on a timer as if the phone were
    // physically moving around the track (D-05).
    LaunchedEffect(feed.isRunning) {
        while (feed.isRunning) {
            delay(700)
            val sample = provider.nextSample() ?: break
            collected.add(sample)
            feed = feed.copy(
                latestSample = sample,
                sampleCount = collected.size,
                quality = GpsQualitySummary.from(collected),
            )
        }
    }

    DriveSurface(
        feed = feed,
        orientation = orientation,
        onToggleOrientation = {
            orientation = if (orientation == DashOrientation.Portrait) {
                DashOrientation.Landscape
            } else {
                DashOrientation.Portrait
            }
        },
        onStartDemo = {
            provider.reset()
            collected.clear()
            provider.start()
            feed = DemoFeedUiState(isRunning = true)
        },
        onStopDemo = {
            provider.stop()
            feed = feed.copy(isRunning = false)
        },
    )
}

@Composable
private fun DriveSurface(
    feed: DemoFeedUiState,
    orientation: DashOrientation,
    onToggleOrientation: () -> Unit,
    onStartDemo: () -> Unit,
    onStopDemo: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeContentPadding()
    ) {
        // Layout follows the actual (deliberately locked) window dimensions. The
        // window is only ever rotated by the explicit user toggle, never by the
        // accelerometer, so this reflects the user's choice — not device tilt.
        val isLandscape = maxWidth > maxHeight
        val isCompactLandscape = isLandscape && maxHeight < 520.dp
        val dashboardPadding = if (isCompactLandscape) 12.dp else 20.dp
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dashboardPadding),
                horizontalArrangement = Arrangement.spacedBy(if (isCompactLandscape) 12.dp else 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderPanel(feed, Modifier.weight(0.9f), compact = isCompactLandscape)
                GpsQualityPanel(feed, Modifier.weight(1.3f), compact = isCompactLandscape)
                ControlPanel(feed, orientation, onToggleOrientation, onStartDemo, onStopDemo, Modifier.weight(0.9f), compact = isCompactLandscape)
            }
        } else {
            // Scrollable so the full control stack (demo feed, blocked timing,
            // orientation toggle) is always reachable even on shorter screens.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dashboardPadding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HeaderPanel(feed, Modifier.fillMaxWidth())
                GpsQualityPanel(feed, Modifier.fillMaxWidth())
                ControlPanel(feed, orientation, onToggleOrientation, onStartDemo, onStopDemo, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun HeaderPanel(
    feed: DemoFeedUiState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(modifier = modifier) {
        Text(
            text = "LapSight",
            color = MaterialTheme.colorScheme.primary,
            fontSize = if (compact) 28.sp else 34.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = "Drive",
            color = Color(0xFF9AA8B8),
            fontSize = if (compact) 14.sp else 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
        Text(
            text = "Closed-course use only. Phone GPS accuracy varies — this is not pro-grade timing. Verify before trusting lap data.",
            color = Color(0xFFCED7E2),
            fontSize = if (compact) 11.sp else 13.sp,
            lineHeight = if (compact) 15.sp else 17.sp,
        )
        Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
        if (feed.isRunning) {
            DemoBadge(compact = compact)
            Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
        }
        Text(
            text = feed.fixStatus.label,
            color = feed.fixStatus.color,
            fontSize = if (compact) 15.sp else 17.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Amber "DEMO — simulated GPS" pill: simulated data must never read as live (D-42). */
@Composable
private fun DemoBadge(compact: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF101722))
            .border(1.dp, Color(0xFFFFD166), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = "DEMO — simulated GPS",
            color = Color(0xFFFFD166),
            fontSize = if (compact) 11.sp else 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun GpsQualityPanel(
    feed: DemoFeedUiState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
    ) {
        MetricCard("Speed", feed.speedKmhLabel, "km/h", emphasized = true, compact = compact)
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            MetricCard("Accuracy", feed.accuracyLabel, "m", Modifier.weight(1f), compact = compact)
            MetricCard("Samples", feed.sampleCountLabel, "", Modifier.weight(1f), compact = compact)
            MetricCard("Rate", feed.updateRateLabel, "Hz", Modifier.weight(1f), compact = compact)
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    compact: Boolean = false,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(if (compact) 12.dp else 16.dp)) {
            Text(
                text = label.uppercase(),
                color = Color(0xFF7E8DA0),
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = when {
                        emphasized && compact -> 40.sp
                        emphasized -> 52.sp
                        compact -> 22.sp
                        else -> 28.sp
                    },
                    fontWeight = FontWeight.Black,
                )
                if (unit.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = unit,
                        color = Color(0xFF9AA8B8),
                        fontSize = when {
                            emphasized && compact -> 16.sp
                            emphasized -> 18.sp
                            compact -> 12.sp
                            else -> 13.sp
                        },
                        modifier = Modifier.padding(
                            bottom = when {
                                emphasized && compact -> 8.dp
                                emphasized -> 10.dp
                                compact -> 4.dp
                                else -> 5.dp
                            }
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlPanel(
    feed: DemoFeedUiState,
    orientation: DashOrientation,
    onToggleOrientation: () -> Unit,
    onStartDemo: () -> Unit,
    onStopDemo: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
    ) {
        // Small, clear demo control (D-44). Detailed scenario selection stays in
        // tests/development, not the main user flow.
        Button(
            onClick = if (feed.isRunning) onStopDemo else onStartDemo,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (feed.isRunning) "Stop Demo Feed" else "Start Demo Feed")
        }
        // Formal timing is blocked until a saved Track exists (D-19). It is a
        // visibly disabled control with an explanatory note — not hidden.
        Button(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
        ) {
            Text("Start Timing")
        }
        Text(
            text = "Mark a track first. Timing needs a saved start/finish line.",
            color = Color(0xFFFFD166),
            fontSize = if (compact) 11.sp else 13.sp,
            lineHeight = if (compact) 15.sp else 17.sp,
        )
        // Manual orientation toggle. Deliberately not sensor-driven: the mounted
        // phone must not rotate on its own under cornering G-forces.
        Button(
            onClick = onToggleOrientation,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (orientation == DashOrientation.Portrait) {
                    "Rotate to Landscape"
                } else {
                    "Rotate to Portrait"
                }
            )
        }
        Text(
            text = "Demo feed replays deterministic simulated GPS through the normal provider layer. Real Android/iOS GPS plugs into the same boundary next.",
            color = Color(0xFF7E8DA0),
            fontSize = if (compact) 10.sp else 12.sp,
            lineHeight = if (compact) 14.sp else 16.sp,
        )
    }
}

private val GpsFixStatus.label: String
    get() = when (this) {
        GpsFixStatus.Idle -> "IDLE"
        GpsFixStatus.Acquiring -> "ACQUIRING"
        GpsFixStatus.Simulated -> "SIMULATED FEED"
        GpsFixStatus.Live -> "LIVE GPS"
        GpsFixStatus.Degraded -> "DEGRADED FIX"
        GpsFixStatus.Unavailable -> "UNAVAILABLE"
    }

private val GpsFixStatus.color: Color
    get() = when (this) {
        GpsFixStatus.Idle -> Color(0xFF9AA8B8)
        GpsFixStatus.Acquiring -> Color(0xFFFFD166)
        GpsFixStatus.Simulated -> Color(0xFF62E3FF)
        GpsFixStatus.Live -> Color(0xFF8CFF9B)
        GpsFixStatus.Degraded -> Color(0xFFFFB84D)
        GpsFixStatus.Unavailable -> Color(0xFFFF6B6B)
    }
