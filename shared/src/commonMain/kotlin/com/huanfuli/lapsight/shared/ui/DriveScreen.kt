package com.huanfuli.lapsight.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.huanfuli.lapsight.shared.DashOrientation
import com.huanfuli.lapsight.shared.OrientationController
import com.huanfuli.lapsight.shared.SimulatedGpsProvider
import com.huanfuli.lapsight.shared.storage.LocalSessionStore
import kotlinx.coroutines.delay

/**
 * The Drive tab: demo GPS feed + Mark New Track capture + (Task 2) Track Review.
 *
 * Owns a [DriveMarkingController] over a [SimulatedGpsProvider] and renders the
 * mounted-phone dash. While capturing, it polls the provider on a timer. The
 * orientation toggle is explicit and user-controlled only — never sensor-driven
 * (mounted-phone safety). Start Timing stays blocked until a saved Track has a
 * confirmed start/finish line (D-19); the timing run itself is Plan 03-06.
 */
@Composable
fun DriveScreen(
    orientationController: OrientationController,
    orientation: DashOrientation,
    onToggleOrientation: () -> Unit,
    onSavedTrack: () -> Unit,
    sessionStore: LocalSessionStore,
) {
    val provider = remember { SimulatedGpsProvider() }
    val controller = remember { DriveMarkingController(provider = provider, store = sessionStore) }
    var snapshot by remember { mutableStateOf(controller.snapshot()) }

    // Apply the chosen orientation through the platform window lock (app-wide).
    LaunchedEffect(orientation) {
        orientationController.apply(orientation)
    }

    // Poll the provider on a timer while the demo feed runs (D-05). The feed
    // flows continuously as if the phone were physically moving around the
    // track, even before/after a marking capture.
    LaunchedEffect(snapshot.isDemoFeedRunning) {
        while (snapshot.isDemoFeedRunning) {
            delay(700)
            controller.tick()
            snapshot = controller.snapshot()
        }
    }

    DriveSurface(
        snapshot = snapshot,
        orientation = orientation,
        onToggleOrientation = onToggleOrientation,
        onToggleDemoFeed = {
            controller.toggleDemoFeed()
            snapshot = controller.snapshot()
        },
        onPrimaryAction = {
            when (snapshot.phase) {
                DriveMarkingPhase.Idle -> {
                    controller.beginMarking()
                    snapshot = controller.snapshot()
                }
                DriveMarkingPhase.Capturing -> {
                    controller.stopMarking()
                    snapshot = controller.snapshot()
                }
                DriveMarkingPhase.Review -> {
                    // Track Review owns its own action buttons (Task 2).
                }
            }
        },
        reviewContent = {
            TrackReviewContent(
                snapshot = snapshot,
                controller = controller,
                onChanged = { snapshot = controller.snapshot() },
                onSavedTrack = onSavedTrack,
            )
        },
    )
}

@Composable
private fun DriveSurface(
    snapshot: DriveMarkingSnapshot,
    orientation: DashOrientation,
    onToggleOrientation: () -> Unit,
    onToggleDemoFeed: () -> Unit,
    onPrimaryAction: () -> Unit,
    reviewContent: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeContentPadding(),
    ) {
        // Layout follows the deliberately locked window, not device tilt.
        val isLandscape = maxWidth > maxHeight
        val isCompactLandscape = isLandscape && maxHeight < 520.dp
        val padding = if (isCompactLandscape) 12.dp else 16.dp

        if (snapshot.phase == DriveMarkingPhase.Review) {
            // Track Review replaces the dash controls (D-31).
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                reviewContent()
            }
            return@BoxWithConstraints
        }

        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalArrangement = Arrangement.spacedBy(if (isCompactLandscape) 12.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderPanel(snapshot, Modifier.weight(0.9f), compact = isCompactLandscape)
                GpsQualityPanel(snapshot, Modifier.weight(1.3f), compact = isCompactLandscape)
                ControlPanel(
                    snapshot = snapshot,
                    orientation = orientation,
                    onToggleOrientation = onToggleOrientation,
                    onToggleDemoFeed = onToggleDemoFeed,
                    onPrimaryAction = onPrimaryAction,
                    modifier = Modifier.weight(0.9f),
                    compact = isCompactLandscape,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HeaderPanel(snapshot, Modifier.fillMaxWidth())
                GpsQualityPanel(snapshot, Modifier.fillMaxWidth())
                ControlPanel(
                    snapshot = snapshot,
                    orientation = orientation,
                    onToggleOrientation = onToggleOrientation,
                    onToggleDemoFeed = onToggleDemoFeed,
                    onPrimaryAction = onPrimaryAction,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HeaderPanel(
    snapshot: DriveMarkingSnapshot,
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
        Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
        if (snapshot.isDemoFeedRunning) {
            DemoBadge(compact = compact)
            Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
        }
        Text(
            text = snapshot.fixStatusLabel(),
            color = snapshot.fixStatusColor(),
            fontSize = if (compact) 15.sp else 17.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Amber "DEMO — simulated GPS" pill: simulated data must never read as live (D-42). */
@Composable
internal fun DemoBadge(compact: Boolean = false) {
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
    snapshot: DriveMarkingSnapshot,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
    ) {
        MetricCard("Speed", snapshot.speedKmhLabel, "km/h", emphasized = true, compact = compact)
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            MetricCard("Accuracy", snapshot.accuracyLabel, "m", Modifier.weight(1f), compact = compact)
            MetricCard("Samples", snapshot.feedSampleCount.toString(), "", Modifier.weight(1f), compact = compact)
            MetricCard(
                "Rate",
                snapshot.feedQuality?.averageUpdateRateHz?.let { formatOneDecimal(it) } ?: "--",
                "Hz",
                Modifier.weight(1f),
                compact = compact,
            )
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
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlPanel(
    snapshot: DriveMarkingSnapshot,
    orientation: DashOrientation,
    onToggleOrientation: () -> Unit,
    onToggleDemoFeed: () -> Unit,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
    ) {
        // Small, clear demo control (D-44).
        Button(
            onClick = onToggleDemoFeed,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Text(if (snapshot.isDemoFeedRunning) "Stop Demo Feed" else "Start Demo Feed")
        }

        // Primary CTA + Start Timing are the same control slot. While Idle with
        // no saved track: Mark New Track. While Capturing: Stop Marking. When a
        // saved track exists (timing ready): Start Timing, blocked until a
        // confirmed start/finish line exists (D-19).
        when (snapshot.phase) {
            DriveMarkingPhase.Capturing -> {
                Button(onClick = onPrimaryAction, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop Marking")
                }
            }
            DriveMarkingPhase.Review -> {
                // Track Review owns its own actions; no primary CTA here.
            }
            DriveMarkingPhase.Idle -> {
                if (snapshot.canStartTiming) {
                    // Timing is Plan 03-06; the button is present but the flow
                    // is not wired in this plan.
                    Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                        Text("Start Timing")
                    }
                } else {
                    Button(onClick = onPrimaryAction, modifier = Modifier.fillMaxWidth()) {
                        Text("Mark New Track")
                    }
                    Text(
                        text = START_TIMING_BLOCKED_COPY,
                        color = Color(0xFFFFD166),
                        fontSize = if (compact) 11.sp else 13.sp,
                        lineHeight = if (compact) 15.sp else 17.sp,
                    )
                }
            }
        }

        // Manual orientation toggle. Deliberately not sensor-driven.
        Button(
            onClick = onToggleOrientation,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Text(
                if (orientation == DashOrientation.Portrait) "Rotate to Landscape" else "Rotate to Portrait",
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

/**
 * Track Review surface rendered when a marking capture stops (Task 2 expands
 * this with Save/Re-record/Discard and confirmations). Task 1 shows the
 * readiness + quality summary so the capture → review transition is visible.
 */
@Composable
internal fun TrackReviewContent(
    snapshot: DriveMarkingSnapshot,
    controller: DriveMarkingController,
    onChanged: () -> Unit,
    onSavedTrack: () -> Unit,
) {
    val review = snapshot.reviewState ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Track Review",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            DemoBadge()
            if (review.canSave) {
                Text(
                    text = "Track ready. Set start/finish, then save.",
                    color = Color(0xFF8CFF9B),
                    fontSize = 16.sp,
                )
            } else {
                Text(
                    text = "Couldn't build a clean track. Re-record 5–10 continuous loops and avoid long stops.",
                    color = Color(0xFFFFD166),
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                )
            }
            Text(
                text = "Loops detected: ${review.extraction.detectedLoopCount} · accepted: ${review.extraction.acceptedLoopCount} · rejected: ${review.extraction.rejectedLoopCount}",
                color = Color(0xFF9AA8B8),
                fontSize = 13.sp,
            )
            Text(
                text = "Samples: ${review.rawSampleCount} · degraded: ${review.quality.degradedSampleCount}",
                color = Color(0xFF9AA8B8),
                fontSize = 13.sp,
            )
            // Marking is continuous capture, NOT lap timing — no lap times shown (D-08).
            Text(
                text = "Marking capture does not produce lap times.",
                color = Color(0xFF7E8DA0),
                fontSize = 13.sp,
            )
        }
    }
}

private fun DriveMarkingSnapshot.fixStatusLabel(): String = when {
    !isDemoFeedRunning && phase == DriveMarkingPhase.Idle && latestSample == null -> "IDLE"
    isDemoFeedRunning -> "SIMULATED FEED"
    else -> "IDLE"
}

private fun DriveMarkingSnapshot.fixStatusColor(): Color = if (isDemoFeedRunning) {
    Color(0xFF62E3FF)
} else {
    Color(0xFF9AA8B8)
}

private fun formatOneDecimal(value: Double): String {
    val scaled = (value * 10.0).toInt()
    return "${scaled / 10}.${scaled % 10}"
}
