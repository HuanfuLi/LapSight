package com.huanfuli.lapsight.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanfuli.lapsight.shared.DashOrientation
import com.huanfuli.lapsight.shared.DriveDisplaySettings
import com.huanfuli.lapsight.shared.OrientationController
import com.huanfuli.lapsight.shared.SimulatedGpsProvider
import com.huanfuli.lapsight.shared.SpeedUnit
import com.huanfuli.lapsight.shared.ghost.DeltaTone
import com.huanfuli.lapsight.shared.lap.formatLapTime
import com.huanfuli.lapsight.shared.session.SaveDraftResult
import com.huanfuli.lapsight.shared.session.SessionController
import com.huanfuli.lapsight.shared.session.StartTimingResult
import com.huanfuli.lapsight.shared.session.STILL_USE_THIS_TRACK_ACTION
import com.huanfuli.lapsight.shared.session.TimingRunSnapshot
import com.huanfuli.lapsight.shared.storage.LocalSessionStore
import com.huanfuli.lapsight.shared.track.CourseDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    onSavedSession: () -> Unit,
    onTimingActiveChanged: (Boolean) -> Unit,
    requestedTimingActive: Boolean,
    displaySettings: DriveDisplaySettings,
    sessionStore: LocalSessionStore,
    sessionController: SessionController,
) {
    val provider = remember { SimulatedGpsProvider(scenarioId = com.huanfuli.lapsight.shared.fixtures.GpsFixtureLibrary.VARIABLE_PACE_GHOST_UAT) }
    val controller = remember { DriveMarkingController(provider = provider, store = sessionStore) }
    val uiScope = rememberCoroutineScope()
    val recorderMutex = remember { Mutex() }
    var snapshot by remember { mutableStateOf(controller.snapshot()) }
    val restoredTimingRun = remember { sessionController.timingRunSnapshot() }
    var timingActive by remember { mutableStateOf(restoredTimingRun.isActive) }
    var timingSnapshot by remember {
        mutableStateOf(
            sessionController.snapshot().takeIf { restoredTimingRun.isActive },
        )
    }
    var timingRun by remember { mutableStateOf(restoredTimingRun) }
    var showStopSummary by remember { mutableStateOf(false) }
    var confirmDiscardSession by remember { mutableStateOf(false) }
    var saveToast by remember { mutableStateOf<String?>(null) }
    var saveInProgress by remember { mutableStateOf(false) }
    var startTimingBlockedMessage by remember { mutableStateOf<String?>(null) }
    var wrongCourseBlock by remember {
        mutableStateOf<StartTimingResult.WrongCourseBlocked?>(null)
    }

    // Apply the chosen orientation through the platform window lock (app-wide).
    LaunchedEffect(orientation) {
        orientationController.apply(orientation)
    }

    LaunchedEffect(timingActive) {
        onTimingActiveChanged(timingActive)
    }

    LaunchedEffect(requestedTimingActive) {
        val restored = sessionController.timingRunSnapshot()
        if (requestedTimingActive && restored.isActive) {
            if (!provider.isRunning) provider.start()
            timingActive = true
            timingSnapshot = sessionController.snapshot()
            timingRun = restored
            snapshot = controller.snapshot()
        }
    }

    // Drive may be re-entered after Review navigation or cold start. Hydrate the
    // persisted saved Track list so Start Timing does not depend on in-memory
    // state left over from the Track Review save action.
    LaunchedEffect(Unit) {
        controller.refreshSavedTracks()
        if (timingActive && !provider.isRunning) provider.start()
        snapshot = controller.snapshot()
    }

    // Poll the provider on a timer while the demo feed runs (D-05). The feed
    // flows continuously as if the phone were physically moving around the
    // track, even before/after a marking capture or timing run.
    LaunchedEffect(snapshot.isDemoFeedRunning, timingActive) {
        while (snapshot.isDemoFeedRunning || timingActive) {
            delay(100L)
            val sample = controller.tick()
            snapshot = controller.snapshot()
            if (timingActive) {
                // Production sample pump: feed the active recorder through the
                // controller (never recorderForTest) and read the timing/delta
                // view back for the UI.
                if (sample != null) {
                    withContext(Dispatchers.Default) {
                        recorderMutex.withLock {
                            sessionController.ingestSample(sample)
                        }
                    }
                }
                timingSnapshot = sessionController.snapshot()
                timingRun = sessionController.timingRunSnapshot()
            }
        }
    }

    // Clearly-far preflight is a stationary, pre-Timing decision. The override
    // never appears on the passive moving fullscreen timing surface.
    wrongCourseBlock?.let { blocked ->
        AlertDialog(
            onDismissRequest = { wrongCourseBlock = null },
            title = { Text("Check selected track") },
            text = {
                Text(blocked.message)
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (val result = sessionController.overrideWrongCourseAndStart()) {
                            is StartTimingResult.Started -> {
                                controller.restartFeedForTiming()
                                wrongCourseBlock = null
                                startTimingBlockedMessage = null
                                timingActive = true
                                timingSnapshot = sessionController.snapshot()
                                timingRun = sessionController.timingRunSnapshot()
                                snapshot = controller.snapshot()
                            }
                            is StartTimingResult.Blocked -> {
                                wrongCourseBlock = null
                                startTimingBlockedMessage = result.message
                            }
                            is StartTimingResult.WrongCourseBlocked -> {
                                wrongCourseBlock = result
                            }
                        }
                    },
                ) {
                    Text(STILL_USE_THIS_TRACK_ACTION)
                }
            },
            dismissButton = {
                TextButton(onClick = { wrongCourseBlock = null }) {
                    Text("Choose another track")
                }
            },
        )
    }

    // Stop summary sheet (D-14): Save Session / Discard with exact UI-SPEC copy.
    if (showStopSummary) {
        if (confirmDiscardSession) {
            AlertDialog(
                onDismissRequest = { confirmDiscardSession = false },
                title = { Text("Discard this session?") },
                text = { Text("Discard this session? Recorded laps will be lost. This can't be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmDiscardSession = false
                            showStopSummary = false
                            sessionController.discardDraft()
                            timingActive = false
                            timingSnapshot = null
                            timingRun = TimingRunSnapshot.inactive()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF6B6B)),
                    ) { Text("Discard") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDiscardSession = false }) { Text("Keep") }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = { /* require an explicit choice */ },
                title = { Text("Session ended") },
                text = {
                    val laps = timingSnapshot?.activeDraft?.checkpointedLapCount ?: 0
                    Text("Laps recorded: $laps. Save to Review, or Discard to discard this session.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                        saveInProgress = true
                        uiScope.launch {
                            val result = withContext(Dispatchers.Default) {
                                recorderMutex.withLock {
                                    sessionController.saveStoppedDraft()
                                }
                            }
                            saveInProgress = false
                            showStopSummary = false
                            timingActive = false
                            timingSnapshot = null
                            timingRun = TimingRunSnapshot.inactive()
                            if (result is SaveDraftResult.Saved) {
                                saveToast = "Session saved"
                                onSavedSession()
                            }
                        }
                        },
                        enabled = !saveInProgress,
                    ) { Text(if (saveInProgress) "Saving..." else "Save Session") }
                },
                dismissButton = {
                    TextButton(
                        onClick = { confirmDiscardSession = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF6B6B)),
                    ) { Text("Discard") }
                },
            )
        }
    }

    // Transient save success toast (D-32).
    saveToast?.let { toast ->
        LaunchedEffect(toast) {
            delay(2000)
            saveToast = null
        }
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                text = toast,
                color = Color(0xFF8CFF9B),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF101722))
                    .border(1.dp, Color(0xFF8CFF9B), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }

    DriveSurface(
        snapshot = snapshot,
        orientation = orientation,
        displaySettings = displaySettings,
        onToggleOrientation = onToggleOrientation,
        onSelectProfile = { profileId ->
            // Explicit user selection only (D-02/D-03); the controller never auto-derives.
            controller.selectTrack(profileId)
            startTimingBlockedMessage = null
            snapshot = controller.snapshot()
        },
        onSelectDirection = { direction ->
            // Pre-Timing Recorded/Reverse choice over the current Track (D-18).
            controller.selectDirection(direction)
            snapshot = controller.snapshot()
        },
        onPrimaryAction = {
            when (snapshot.phase) {
                DriveMarkingPhase.Idle -> {
                    if (snapshot.canStartTiming && !timingActive) {
                        // Start formal timing against the saved track (D-19).
                        val trackId = snapshot.timingReadyTrackId
                        if (trackId == null) {
                            startTimingBlockedMessage = START_TIMING_BLOCKED_COPY
                        } else {
                            val latestGps = snapshot.latestSample
                            when (
                                val result = sessionController.startTiming(
                                    trackId = trackId,
                                    latestGps = latestGps,
                                    preflightNowElapsedMillis = latestGps?.elapsedMillis ?: 0L,
                                )
                            ) {
                                is StartTimingResult.Started -> {
                                    // The demo provider should start a timing run
                                    // from a clean replay phase, not from whatever
                                    // marking/review sample index was left behind.
                                    controller.restartFeedForTiming()
                                    startTimingBlockedMessage = null
                                    timingActive = true
                                    timingSnapshot = sessionController.snapshot()
                                    timingRun = sessionController.timingRunSnapshot()
                                    snapshot = controller.snapshot()
                                }
                                is StartTimingResult.Blocked -> {
                                    startTimingBlockedMessage = result.message
                                    snapshot = controller.snapshot()
                                }
                                is StartTimingResult.WrongCourseBlocked -> {
                                    wrongCourseBlock = result
                                    startTimingBlockedMessage = result.message
                                    snapshot = controller.snapshot()
                                }
                            }
                        }
                    } else {
                        startTimingBlockedMessage = null
                        controller.beginMarking()
                        snapshot = controller.snapshot()
                    }
                }
                DriveMarkingPhase.Capturing -> {
                    controller.stopMarking()
                    snapshot = controller.snapshot()
                }
                DriveMarkingPhase.Review -> {
                    // Track Review owns its own action buttons.
                }
            }
        },
        onStopTiming = {
            if (timingActive) {
                timingActive = false
                uiScope.launch {
                    withContext(Dispatchers.Default) {
                        recorderMutex.withLock {
                            sessionController.stop()
                        }
                    }
                    showStopSummary = true
                }
            }
        },
        timingActive = timingActive,
        timingSnapshot = timingSnapshot,
        timingRun = timingRun,
        startTimingBlockedMessage = startTimingBlockedMessage,
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
    displaySettings: DriveDisplaySettings,
    onToggleOrientation: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onSelectDirection: (CourseDirection) -> Unit,
    onPrimaryAction: () -> Unit,
    onStopTiming: () -> Unit,
    timingActive: Boolean,
    timingSnapshot: com.huanfuli.lapsight.shared.session.SessionControllerSnapshot?,
    timingRun: TimingRunSnapshot,
    startTimingBlockedMessage: String?,
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

        // While formal timing is active, show the fullscreen timing surface with
        // current/last/best/laps/speed/accuracy and a DEMO badge for simulated
        // source (D-29, D-42, SESS-01).
        if (timingActive) {
            TimingRunSurface(
                timingRun = timingRun,
                orientation = orientation,
                displaySettings = displaySettings,
                onToggleOrientation = onToggleOrientation,
                onStopTiming = onStopTiming,
                isCompactLandscape = isCompactLandscape,
                padding = padding,
            )
            return@BoxWithConstraints
        }

        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalArrangement = Arrangement.spacedBy(if (isCompactLandscape) 12.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GpsQualityPanel(
                    snapshot = snapshot,
                    displaySettings = displaySettings,
                    modifier = Modifier.weight(1f),
                    compact = isCompactLandscape,
                )
                ControlPanel(
                    snapshot = snapshot,
                    orientation = orientation,
                    onToggleOrientation = onToggleOrientation,
                    onSelectProfile = onSelectProfile,
                    onSelectDirection = onSelectDirection,
                    onPrimaryAction = onPrimaryAction,
                    modifier = Modifier.weight(1.15f),
                    compact = isCompactLandscape,
                    startTimingBlockedMessage = startTimingBlockedMessage,
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
                GpsQualityPanel(
                    snapshot = snapshot,
                    displaySettings = displaySettings,
                    modifier = Modifier.fillMaxWidth(),
                )
                ControlPanel(
                    snapshot = snapshot,
                    orientation = orientation,
                    onToggleOrientation = onToggleOrientation,
                    onSelectProfile = onSelectProfile,
                    onSelectDirection = onSelectDirection,
                    onPrimaryAction = onPrimaryAction,
                    modifier = Modifier.fillMaxWidth(),
                    startTimingBlockedMessage = startTimingBlockedMessage,
                )
            }
        }
    }
}

/**
 * Fullscreen timing surface shown while a formal timing session is running (D-29).
 *
 * Priority order while moving (UI-SPEC Timing Surface Layout): current lap time is
 * the primary display, the live delta is the second core readout and value-only
 * (`--` / `+0.421s` / `-0.218s`, D-13/D-14), and last/best/laps/speed/accuracy are
 * compact secondary metrics that keep working even when the delta is `--`
 * (D-19/T-04-11). Numerals use tabular figures so digits do not horizontal-jitter
 * as values tick (UI-SPEC Display role). A DEMO badge is shown when the source is
 * simulated (D-42). The surface stays passive while moving (no charts/maps, D-16).
 */
@Composable
private fun TimingRunSurface(
    timingRun: TimingRunSnapshot,
    orientation: DashOrientation,
    displaySettings: DriveDisplaySettings,
    onToggleOrientation: () -> Unit,
    onStopTiming: () -> Unit,
    isCompactLandscape: Boolean,
    padding: androidx.compose.ui.unit.Dp,
) {
    var displayMillis by remember(timingRun.isActive) { mutableStateOf(0L) }
    var lastLapMillisSeen by remember(timingRun.isActive) { mutableStateOf<Long?>(null) }
    var lastUpdateEpoch by remember(timingRun.isActive) { mutableStateOf(com.huanfuli.lapsight.shared.nowEpochMillis()) }
    var speedHistory by remember(timingRun.isActive) { mutableStateOf(emptyList<Float>()) }

    LaunchedEffect(timingRun.currentLapMillis) {
        if (timingRun.isActive) {
            val base = timingRun.currentLapMillis ?: 0L
            displayMillis = base
            lastLapMillisSeen = base
            lastUpdateEpoch = com.huanfuli.lapsight.shared.nowEpochMillis()
        } else {
            displayMillis = 0L
            lastLapMillisSeen = null
        }
    }

    LaunchedEffect(timingRun.isActive) {
        while (timingRun.isActive) {
            kotlinx.coroutines.delay(16)
            if (lastLapMillisSeen != null) {
                val now = com.huanfuli.lapsight.shared.nowEpochMillis()
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
    val tnum = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
    val sectorValue = if (timingRun.sectorCount > 0) {
        "${timingRun.currentSectorNumber ?: "--"}/${timingRun.sectorCount}"
    } else {
        "--"
    }
    val metrics = buildList {
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

    if (orientation == DashOrientation.Landscape) {
        Row(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PrimaryTimingReadouts(
                    displayMillis = displayMillis,
                    timingRun = timingRun,
                    speedLabel = speedLabel,
                    speedUnit = speedUnit,
                    compact = true,
                    tnum = tnum,
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
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrimaryTimingReadouts(
                displayMillis = displayMillis,
                timingRun = timingRun,
                speedLabel = speedLabel,
                speedUnit = speedUnit,
                compact = false,
                tnum = tnum,
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
    tnum: androidx.compose.ui.text.TextStyle,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "CURRENT LAP",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = displayMillis.formatLapTime(),
            color = MaterialTheme.colorScheme.primary,
            fontSize = if (compact) 42.sp else 54.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            style = tnum,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                DeltaReadout(
                    display = timingRun.deltaDisplay,
                    compact = true,
                    tnum = tnum,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SPEED",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = speedLabel,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = if (compact) 34.sp else 40.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        style = tnum,
                    )
                    Text(
                        text = speedUnit,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
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
    val rows = metrics.chunked(3)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(if (compact) 8.dp else 10.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = metric.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = if (compact) 9.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = metric.value,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = if (compact) 15.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
                )
                if (metric.unit.isNotEmpty()) {
                    Text(
                        text = metric.unit,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(start = 3.dp, bottom = 2.dp),
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
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
    ) {
        drawLine(
            color = axisColor,
            start = androidx.compose.ui.geometry.Offset(0f, size.height),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height),
            strokeWidth = 1f,
        )
        if (samples.size < 2) return@Canvas
        val maxSpeed = samples.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val xStep = size.width / (samples.size - 1)
        samples.zipWithNext().forEachIndexed { index, pair ->
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
    }
}

@Composable
private fun TimingControls(
    orientation: DashOrientation,
    onToggleOrientation: () -> Unit,
    onStopTiming: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onStopTiming,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB8343A)),
        ) {
            Text("STOP", fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = onToggleOrientation,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Text(
                if (orientation == DashOrientation.Portrait) "LANDSCAPE" else "PORTRAIT",
                fontWeight = FontWeight.Bold,
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
    display: com.huanfuli.lapsight.shared.ghost.DeltaDisplayState,
    compact: Boolean,
    tnum: androidx.compose.ui.text.TextStyle,
) {
    Text(
        text = "DELTA",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = if (compact) 10.sp else 11.sp,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = display.text,
        color = display.tone.toDeltaColor(),
        fontSize = if (compact) 32.sp else 44.sp,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Start,
        style = tnum,
    )
}

/** Maps the platform-free [DeltaTone] to UI-SPEC semantic colors. */
private fun DeltaTone.toDeltaColor(): Color = when (this) {
    DeltaTone.Faster -> Color(0xFF8CFF9B)
    DeltaTone.Slower -> Color(0xFFFF9F43)
    DeltaTone.Neutral -> Color(0xFF9AA8B8)
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
    displaySettings: DriveDisplaySettings,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val speedMultiplier = when (displaySettings.speedUnit) {
        SpeedUnit.KilometersPerHour -> 3.6
        SpeedUnit.MilesPerHour -> 2.2369362921
    }
    val speedUnit = when (displaySettings.speedUnit) {
        SpeedUnit.KilometersPerHour -> "km/h"
        SpeedUnit.MilesPerHour -> "mph"
    }
    val speedLabel = snapshot.latestSample?.speedMetersPerSecond
        ?.let { (it * speedMultiplier).toInt().toString() }
        ?: "--"
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
    ) {
        MetricCard("Speed", speedLabel, speedUnit, emphasized = true, compact = compact)
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = MaterialTheme.colorScheme.onSurface,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onSelectProfile: (String) -> Unit,
    onSelectDirection: (CourseDirection) -> Unit,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    startTimingBlockedMessage: String? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
    ) {
        // Primary CTA + Start Timing are the same control slot. While Idle with
        // no saved track: Mark New Track. While Capturing: Stop Marking. When a
        // saved track exists (timing ready): Start Timing, blocked until a
        // confirmed start/finish line exists (D-19).
        when (snapshot.phase) {
            DriveMarkingPhase.Capturing -> {
                if (compact) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = onPrimaryAction, modifier = Modifier.weight(1f)) {
                            Text("Stop Marking")
                        }
                        OrientationButton(
                            orientation = orientation,
                            onClick = onToggleOrientation,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Button(onClick = onPrimaryAction, modifier = Modifier.fillMaxWidth()) {
                        Text("Stop Marking")
                    }
                }
            }
            DriveMarkingPhase.Review -> {
                // Track Review owns its own actions; no primary CTA here.
            }
            DriveMarkingPhase.Idle -> {
                // Compact current-Track selector (D-01, D-02): always states the
                // selected Track or clearly states none, and offers a direct selection
                // action so a blocked Timing routes to picking a Track (D-03). This is a
                // pre-Timing control only; it is never shown on the moving fullscreen
                // timing surface. No automatic newest/nearby recommendation (D-04).
                TrackSelectorSection(
                    snapshot = snapshot,
                    onSelectProfile = onSelectProfile,
                    compact = compact,
                )
                if (snapshot.canStartTiming) {
                    // Pre-Timing Recorded/Reverse selector over the SAME revision (D-18).
                    // It is shown only on the stationary Drive surface, never the moving
                    // fullscreen timing dash (safety), and never auto-recommends a direction.
                    DirectionSelectorSection(
                        selected = snapshot.selectedDirection,
                        onSelectDirection = onSelectDirection,
                        compact = compact,
                    )
                }
                if (snapshot.canStartTiming) {
                    // Saved track with confirmed start/finish exists: Start Timing
                    // drives the formal session lifecycle (D-19, SESS-01).
                    if (compact) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(onClick = onPrimaryAction, modifier = Modifier.weight(1f)) {
                                Text("Start Timing")
                            }
                            OrientationButton(
                                orientation = orientation,
                                onClick = onToggleOrientation,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        Button(onClick = onPrimaryAction, modifier = Modifier.fillMaxWidth()) {
                            Text("Start Timing")
                        }
                    }
                    startTimingBlockedMessage?.let { message ->
                        Text(
                            text = message,
                            color = Color(0xFFFFD166),
                            fontSize = if (compact) 11.sp else 13.sp,
                            lineHeight = if (compact) 15.sp else 17.sp,
                        )
                    }
                } else {
                    if (compact) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(onClick = onPrimaryAction, modifier = Modifier.weight(1f)) {
                                Text("Mark New Track")
                            }
                            OrientationButton(
                                orientation = orientation,
                                onClick = onToggleOrientation,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        Button(onClick = onPrimaryAction, modifier = Modifier.fillMaxWidth()) {
                            Text("Mark New Track")
                        }
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
        if (!compact) {
            OrientationButton(
                orientation = orientation,
                onClick = onToggleOrientation,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun OrientationButton(
    orientation: DashOrientation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(if (orientation == DashOrientation.Portrait) "Landscape" else "Portrait")
    }
}

/**
 * Compact pre-Timing Track selector (D-01, D-02, D-03).
 *
 * Always states the current Track by name (or clearly states none), and lists the
 * active, latest-revision profiles as direct selection actions so a blocked Timing
 * has an obvious recovery path. Selection is always explicit: tapping a row sets it
 * as the current Track. There is deliberately no automatic newest/nearby
 * recommendation (D-04). This control is rendered only on the stationary Drive
 * surface, never on the moving fullscreen timing dash (safety).
 */
@Composable
private fun TrackSelectorSection(
    snapshot: DriveMarkingSnapshot,
    onSelectProfile: (String) -> Unit,
    compact: Boolean = false,
) {
    var showTrackPicker by remember { mutableStateOf(false) }

    if (showTrackPicker) {
        AlertDialog(
            onDismissRequest = { showTrackPicker = false },
            title = { Text("Choose track") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    snapshot.selectableProfiles.forEach { row ->
                        val isCurrent = row.profileId == snapshot.timingReadyTrackId
                        OutlinedButton(
                            onClick = {
                                onSelectProfile(row.profileId)
                                showTrackPicker = false
                            },
                            enabled = row.isTimingReady && !isCurrent,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val suffix = when {
                                isCurrent -> " (current)"
                                !row.isTimingReady -> " (needs start/finish)"
                                else -> ""
                            }
                            Text(row.name + suffix)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrackPicker = false }) {
                    Text("Close")
                }
            },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.padding(if (compact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
        ) {
            Text(
                text = "CURRENT TRACK",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = snapshot.currentTrackName ?: "No track selected",
                color = if (snapshot.currentTrackName != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    Color(0xFFFFD166)
                },
                fontSize = if (compact) 16.sp else 18.sp,
                fontWeight = FontWeight.Bold,
            )
            if (snapshot.needsTrackSelection) {
                Text(
                    text = "Select a track to start timing.",
                    color = Color(0xFFFFD166),
                    fontSize = if (compact) 11.sp else 13.sp,
                    lineHeight = if (compact) 15.sp else 17.sp,
                )
            }
            if (snapshot.selectableProfiles.isEmpty()) {
                Text(
                    text = "No saved tracks yet. Mark a track to create your first one.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = if (compact) 11.sp else 13.sp,
                    lineHeight = if (compact) 15.sp else 17.sp,
                )
            } else {
                OutlinedButton(
                    onClick = { showTrackPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Choose track")
                }
            }
        }
    }
}

/**
 * Compact pre-Timing Course Direction selector (D-18).
 *
 * Lets the user run the SAME saved revision either in the Recorded direction or its
 * Reverse without re-marking the Track. The choice is explicit (two segmented
 * buttons, the active one highlighted) with no automatic recommendation, and it is
 * rendered only on the stationary Drive surface — never on the moving fullscreen
 * timing dash (safety).
 */
@Composable
private fun DirectionSelectorSection(
    selected: CourseDirection,
    onSelectDirection: (CourseDirection) -> Unit,
    compact: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.padding(if (compact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
        ) {
            Text(
                text = "COURSE DIRECTION",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
                DirectionChip(
                    label = "Recorded",
                    active = selected == CourseDirection.Recorded,
                    onClick = { onSelectDirection(CourseDirection.Recorded) },
                    modifier = Modifier.weight(1f),
                    compact = compact,
                )
                DirectionChip(
                    label = "Reverse",
                    active = selected == CourseDirection.Reverse,
                    onClick = { onSelectDirection(CourseDirection.Reverse) },
                    modifier = Modifier.weight(1f),
                    compact = compact,
                )
            }
        }
    }
}

@Composable
private fun DirectionChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (active) {
        Button(onClick = onClick, modifier = modifier) {
            Text((if (active) "✓ " else "") + label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        ) {
            Text(label)
        }
    }
}

/**
 * Track Review surface rendered when a marking capture stops (D-31).
 *
 * Shows reference readiness, GPS/capture quality summary, start/finish status,
 * and Save / Re-record / Discard actions using the exact 03-UI-SPEC copy.
 * Re-record and Discard require explicit confirmation. Track Review never shows
 * lap times for marking samples (D-08). Save writes the Track + source marking
 * through the store; Discard keeps the marking out of Review history (D-16).
 */
@Composable
internal fun TrackReviewContent(
    snapshot: DriveMarkingSnapshot,
    controller: DriveMarkingController,
    onChanged: () -> Unit,
    onSavedTrack: () -> Unit,
) {
    val review = snapshot.reviewState ?: return
    var confirmReRecord by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    // Name-on-create affordance (D-02, SC-01): the user names the Track before saving.
    // The controller validates/falls back to a default if left blank.
    var trackName by remember { mutableStateOf("") }

    if (confirmReRecord) {
        AlertDialog(
            onDismissRequest = { confirmReRecord = false },
            title = { Text("Re-record track?") },
            text = { Text("Re-record track? The current marking trace and reference line will be replaced.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReRecord = false
                    controller.reRecord()
                    onChanged()
                }) { Text("Re-record") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReRecord = false }) { Text("Cancel") }
            },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard track?") },
            text = { Text("Discard this track? You'll lose the marking trace and reference line.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        controller.discard()
                        onChanged()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF6B6B)),
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Cancel") }
            },
        )
    }

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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            Text(
                text = "Samples: ${review.rawSampleCount} · degraded: ${review.quality.degradedSampleCount}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            // Start/finish editing state (D-11, D-19). A confirmed start/finish
            // is required before formal timing (Plan 03-06).
            val startFinishSet = review.startFinish != null
            Text(
                text = if (startFinishSet) {
                    "Start/finish: set"
                } else {
                    "Start/finish: not set — required before timing"
                },
                color = if (startFinishSet) Color(0xFF8CFF9B) else Color(0xFFFFD166),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            // Marking is continuous capture, NOT lap timing — no lap times shown (D-08).
            Text(
                text = "Marking capture does not produce lap times.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )

            Spacer(Modifier.height(4.dp))
            // Name this Track before saving (D-02). Blank falls back to the default
            // name in the controller; the name never forms a storage path (T-05-07).
            OutlinedTextField(
                value = trackName,
                onValueChange = {
                    trackName = it
                    controller.setTrackName(it)
                },
                label = { Text("Track name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // Save Track — primary CTA; accent-styled, enabled only when ready.
            Button(
                onClick = {
                    controller.setTrackName(trackName)
                    controller.saveTrack()
                    onChanged()
                    onSavedTrack()
                },
                enabled = review.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save Track") }
            // Confirm start/finish from the reference line (convenience; D-11).
            OutlinedButton(
                onClick = {
                    controller.confirmStartFinish()
                    onChanged()
                },
                enabled = review.canSave && review.startFinish == null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            ) { Text("Set start/finish from reference") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { confirmReRecord = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) { Text("Re-record") }
                OutlinedButton(
                    onClick = { confirmDiscard = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B6B)),
                ) { Text("Discard") }
            }
        }
    }
}

private fun formatOneDecimal(value: Double): String {
    val scaled = (value * 10.0).toInt()
    return "${scaled / 10}.${scaled % 10}"
}
