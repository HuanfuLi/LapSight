package com.huanfuli.lapsight.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanfuli.lapsight.shared.DashOrientation
import com.huanfuli.lapsight.shared.DriveDisplaySettings
import com.huanfuli.lapsight.shared.LocationFeedMode
import com.huanfuli.lapsight.shared.LocationSampleProvider
import com.huanfuli.lapsight.shared.OrientationController
import com.huanfuli.lapsight.shared.PhoneGpsPermissionState
import com.huanfuli.lapsight.shared.SpeedUnit
import com.huanfuli.lapsight.shared.ghost.DeltaTone
import com.huanfuli.lapsight.shared.lap.formatLapTime
import com.huanfuli.lapsight.shared.session.RawRecordingController
import com.huanfuli.lapsight.shared.session.ReadyBlocker
import com.huanfuli.lapsight.shared.session.ReadyState
import com.huanfuli.lapsight.shared.session.ReadyThresholds
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

private const val PHONE_GPS_PERMISSION_COPY =
    "Allow location permission to use Phone GPS."

/**
 * The Drive tab: selected GPS feed + Mark New Track capture + Track Review.
 *
 * Owns a [DriveMarkingController] over the injected [LocationSampleProvider] and
 * renders the mounted-phone dash. Phone GPS and simulated replay therefore feed
 * the same `LocationSample` stream into marking, timing, storage, and review.
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
    locationFeedMode: LocationFeedMode,
    locationProvider: LocationSampleProvider,
    phoneGpsPermission: PhoneGpsPermissionState,
    sessionStore: LocalSessionStore,
    sessionController: SessionController,
) {
    val controller = remember(locationProvider, sessionStore) {
        DriveMarkingController(provider = locationProvider, store = sessionStore)
    }
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
    // Diagnostic raw-recording seam used when the app is not Ready (D-16, D-17). It
    // shares the live feed but constructs no lap/ghost timing state.
    val rawController = remember(locationProvider) {
        RawRecordingController(provider = locationProvider)
    }
    var rawRecordingActive by remember { mutableStateOf(false) }
    var rawSnapshot by remember { mutableStateOf(rawController.snapshot()) }
    // Conservative Ready preview for the dash (D-13/D-14/D-32). The authoritative
    // gate runs in SessionController.startTiming; this mirrors its thresholds over
    // the inputs the stationary dash can see so the user knows before tapping Start.
    val dashReady = dashReadyState(snapshot)
    val ensureSelectedLocationFeedReady: () -> Boolean = {
        if (locationFeedMode == LocationFeedMode.PhoneGps && !phoneGpsPermission.isGranted) {
            startTimingBlockedMessage = PHONE_GPS_PERMISSION_COPY
            phoneGpsPermission.requestPermission()
            snapshot = controller.snapshot()
            false
        } else {
            true
        }
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
            if (!locationProvider.isRunning) locationProvider.start()
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
        if (timingActive && !locationProvider.isRunning) locationProvider.start()
        snapshot = controller.snapshot()
    }

    // Poll the provider on a timer while the demo feed runs (D-05). The feed
    // flows continuously as if the phone were physically moving around the
    // track, even before/after a marking capture or timing run.
    LaunchedEffect(snapshot.isDemoFeedRunning, timingActive, rawRecordingActive) {
        while (snapshot.isDemoFeedRunning || timingActive || rawRecordingActive) {
            delay(100L)
            if (rawRecordingActive) {
                // Raw recording owns the feed exclusively: pull through the raw seam
                // (no lap/ghost state) and read its diagnostic snapshot back.
                rawController.tick()
                rawSnapshot = rawController.snapshot()
                continue
            }
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
                            is StartTimingResult.NotReady -> {
                                // The override path intentionally bypasses the Ready
                                // gate (D-18 evidence), so this is unreachable; close
                                // the dialog and surface the reason defensively.
                                wrongCourseBlock = null
                                startTimingBlockedMessage = result.message
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
        locationFeedMode = locationFeedMode,
        phoneGpsPermission = phoneGpsPermission,
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
        onStartTiming = action@{
            if (!ensureSelectedLocationFeedReady()) return@action
            if (!snapshot.canStartTiming || timingActive) {
                startTimingBlockedMessage = snapshot.startTimingBlockedReason
                snapshot = controller.snapshot()
                return@action
            }
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
                        recentRateHz = snapshot.feedQuality?.averageUpdateRateHz,
                        // D-13: real runs only begin when the conservative Ready gate passes.
                        requireReady = true,
                    )
                ) {
                    is StartTimingResult.NotReady -> {
                        // Not Ready: do not start formal timing. The dash already
                        // offers the raw-recording path instead (D-16).
                        startTimingBlockedMessage = result.message
                        snapshot = controller.snapshot()
                    }
                    is StartTimingResult.Started -> {
                        // Timing starts from a clean feed session. For simulated
                        // data this rewinds the replay; for phone GPS it clears
                        // queued fixes and resets session-relative elapsed time.
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
        },
        onBeginMarking = action@{
            if (!ensureSelectedLocationFeedReady()) return@action
            startTimingBlockedMessage = null
            controller.beginMarking()
            snapshot = controller.snapshot()
        },
        onStopMarking = {
            when (snapshot.phase) {
                DriveMarkingPhase.Capturing -> {
                    controller.stopMarking()
                    snapshot = controller.snapshot()
                }
                DriveMarkingPhase.Idle,
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
        onStartRawRecording = action@{
            if (!ensureSelectedLocationFeedReady()) return@action
            if (timingActive || rawRecordingActive) return@action
            startTimingBlockedMessage = null
            rawController.start()
            rawRecordingActive = true
            rawSnapshot = rawController.snapshot()
            snapshot = controller.snapshot()
        },
        onStopRawRecording = {
            if (rawRecordingActive) {
                rawController.stop()
                rawRecordingActive = false
                rawSnapshot = rawController.snapshot()
                snapshot = controller.snapshot()
            }
        },
        timingActive = timingActive,
        timingSnapshot = timingSnapshot,
        timingRun = timingRun,
        startTimingBlockedMessage = startTimingBlockedMessage,
        dashReady = dashReady,
        rawRecordingActive = rawRecordingActive,
        rawSnapshot = rawSnapshot,
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
    locationFeedMode: LocationFeedMode,
    phoneGpsPermission: PhoneGpsPermissionState,
    onToggleOrientation: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onSelectDirection: (CourseDirection) -> Unit,
    onStartTiming: () -> Unit,
    onBeginMarking: () -> Unit,
    onStopMarking: () -> Unit,
    onStopTiming: () -> Unit,
    onStartRawRecording: () -> Unit,
    onStopRawRecording: () -> Unit,
    timingActive: Boolean,
    timingSnapshot: com.huanfuli.lapsight.shared.session.SessionControllerSnapshot?,
    timingRun: TimingRunSnapshot,
    startTimingBlockedMessage: String?,
    dashReady: ReadyState,
    rawRecordingActive: Boolean,
    rawSnapshot: com.huanfuli.lapsight.shared.session.RawRecordingSnapshot,
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
                locationFeedMode = locationFeedMode,
                onToggleOrientation = onToggleOrientation,
                onStopTiming = onStopTiming,
                isCompactLandscape = isCompactLandscape,
                padding = padding,
            )
            return@BoxWithConstraints
        }

        if (isLandscape) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(if (isCompactLandscape) 8.dp else 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DriveStatusBar(
                    snapshot = snapshot,
                    displaySettings = displaySettings,
                    locationFeedMode = locationFeedMode,
                    phoneGpsPermission = phoneGpsPermission,
                    dashReady = dashReady,
                    rawRecordingActive = rawRecordingActive,
                    rawSnapshot = rawSnapshot,
                    modifier = Modifier.fillMaxWidth(),
                    compact = isCompactLandscape,
                )
                ControlPanel(
                    snapshot = snapshot,
                    orientation = orientation,
                    onToggleOrientation = onToggleOrientation,
                    onSelectProfile = onSelectProfile,
                    onSelectDirection = onSelectDirection,
                    onStartTiming = onStartTiming,
                    onBeginMarking = onBeginMarking,
                    onStopMarking = onStopMarking,
                    onStartRawRecording = onStartRawRecording,
                    onStopRawRecording = onStopRawRecording,
                    dashReady = dashReady,
                    rawRecordingActive = rawRecordingActive,
                    modifier = Modifier.fillMaxWidth(),
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DriveStatusBar(
                    snapshot = snapshot,
                    displaySettings = displaySettings,
                    locationFeedMode = locationFeedMode,
                    phoneGpsPermission = phoneGpsPermission,
                    dashReady = dashReady,
                    rawRecordingActive = rawRecordingActive,
                    rawSnapshot = rawSnapshot,
                    modifier = Modifier.fillMaxWidth(),
                )
                ControlPanel(
                    snapshot = snapshot,
                    orientation = orientation,
                    onToggleOrientation = onToggleOrientation,
                    onSelectProfile = onSelectProfile,
                    onSelectDirection = onSelectDirection,
                    onStartTiming = onStartTiming,
                    onBeginMarking = onBeginMarking,
                    onStopMarking = onStopMarking,
                    onStartRawRecording = onStartRawRecording,
                    onStopRawRecording = onStopRawRecording,
                    dashReady = dashReady,
                    rawRecordingActive = rawRecordingActive,
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
    locationFeedMode: LocationFeedMode,
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
            modifier = Modifier.weight(1f).height(52.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB8343A)),
        ) {
            Icon(
                imageVector = StopActionIcon,
                contentDescription = "Stop timing",
                modifier = Modifier.size(28.dp),
            )
        }
        Button(
            onClick = onToggleOrientation,
            modifier = Modifier.weight(1f).height(52.dp),
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
private fun DriveStatusBar(
    snapshot: DriveMarkingSnapshot,
    displaySettings: DriveDisplaySettings,
    locationFeedMode: LocationFeedMode,
    phoneGpsPermission: PhoneGpsPermissionState,
    dashReady: ReadyState,
    rawRecordingActive: Boolean,
    rawSnapshot: com.huanfuli.lapsight.shared.session.RawRecordingSnapshot,
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
    val sourceLabel = when {
        locationFeedMode == LocationFeedMode.PhoneGps && phoneGpsPermission.isGranted -> "GPS OK"
        locationFeedMode == LocationFeedMode.PhoneGps -> "GPS NEEDED"
        else -> "SIM"
    }
    val sourceColor = when {
        locationFeedMode == LocationFeedMode.PhoneGps && phoneGpsPermission.isGranted -> Color(0xFF1F8F4D)
        locationFeedMode == LocationFeedMode.PhoneGps -> Color(0xFFB26A00)
        else -> Color(0xFFB26A00)
    }
    val rateLabel = snapshot.feedQuality?.averageUpdateRateHz?.let { formatOneDecimal(it) } ?: "--"
    // Ready / not-Ready glance state (D-13/D-14/D-32). Reuse the same green/amber
    // semantic branches as the source label so the dash reads consistently.
    val readyLabel: String
    val readyColor: Color
    when {
        rawRecordingActive -> {
            readyLabel = "RAW REC · ${rawSnapshot.sampleCount} pts"
            readyColor = Color(0xFFB26A00)
        }
        dashReady is ReadyState.Ready -> {
            readyLabel = "READY"
            readyColor = Color(0xFF1F8F4D)
        }
        else -> {
            val primary = (dashReady as ReadyState.NotReady).reasons.firstOrNull()
            readyLabel = "NOT READY · ${primary?.dashLabel() ?: "checking"}"
            readyColor = Color(0xFFB26A00)
        }
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 7.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = sourceLabel,
                color = sourceColor,
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Text(
                text = "$speedLabel $speedUnit · ${snapshot.accuracyLabel}m · ${snapshot.feedSampleCount} pts · ${rateLabel}Hz",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = if (compact) 10.sp else 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = readyLabel,
            color = readyColor,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Short glance label for a not-Ready primary reason shown on the dash (D-32). */
private fun ReadyBlocker.dashLabel(): String = when (this) {
    ReadyBlocker.MissingFix -> "no GPS fix"
    ReadyBlocker.NonFiniteFix -> "bad GPS fix"
    ReadyBlocker.PoorAccuracy -> "GPS accuracy"
    ReadyBlocker.StaleFix -> "stale GPS"
    ReadyBlocker.LowSampleRate -> "low GPS rate"
    ReadyBlocker.NoCourseSelected -> "no track"
    ReadyBlocker.StartFinishUnconfirmed -> "no start/finish"
    ReadyBlocker.DirectionIncompatible -> "direction"
    ReadyBlocker.WrongCourseBlocked -> "wrong course"
    ReadyBlocker.PreflightUnavailable -> "course check"
}

/**
 * Conservative Ready preview computed from the stationary dash inputs
 * (D-13/D-14/D-32). It mirrors the authoritative [com.huanfuli.lapsight.shared.session.aggregateReady]
 * thresholds over the inputs the dash can see — GPS fix presence/validity,
 * horizontal accuracy, recent sample rate, and current-track selection. Freshness,
 * direction compatibility, and wrong-course preflight are enforced by the
 * authoritative gate in `SessionController.startTiming`; this preview never starts
 * timing on its own.
 */
private fun dashReadyState(snapshot: DriveMarkingSnapshot): ReadyState {
    val thresholds = ReadyThresholds.Default
    val reasons = mutableListOf<ReadyBlocker>()
    val fix = snapshot.latestSample
    if (fix == null) {
        reasons += ReadyBlocker.MissingFix
    } else if (!fix.latitude.isFinite() || !fix.longitude.isFinite() ||
        fix.latitude !in -90.0..90.0 || fix.longitude !in -180.0..180.0
    ) {
        reasons += ReadyBlocker.NonFiniteFix
    } else {
        val accuracy = fix.horizontalAccuracyMeters
        if (accuracy == null || !accuracy.isFinite() || accuracy < 0.0 ||
            accuracy > thresholds.maxHorizontalAccuracyMeters
        ) {
            reasons += ReadyBlocker.PoorAccuracy
        }
    }
    val rate = snapshot.feedQuality?.averageUpdateRateHz
    if (rate == null || !rate.isFinite() || rate < thresholds.minSampleRateHz) {
        reasons += ReadyBlocker.LowSampleRate
    }
    if (!snapshot.canStartTiming) {
        reasons += ReadyBlocker.NoCourseSelected
    }
    return if (reasons.isEmpty()) ReadyState.Ready else ReadyState.NotReady(reasons)
}

@Composable
private fun ControlPanel(
    snapshot: DriveMarkingSnapshot,
    orientation: DashOrientation,
    onToggleOrientation: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onSelectDirection: (CourseDirection) -> Unit,
    onStartTiming: () -> Unit,
    onBeginMarking: () -> Unit,
    onStopMarking: () -> Unit,
    onStartRawRecording: () -> Unit,
    onStopRawRecording: () -> Unit,
    dashReady: ReadyState,
    rawRecordingActive: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    startTimingBlockedMessage: String? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
    ) {
        when (snapshot.phase) {
            DriveMarkingPhase.Capturing -> {
                DriveActionRow(
                    primaryIcon = StopActionIcon,
                    primaryDescription = "Stop marking",
                    primaryContainerColor = Color(0xFFB8343A),
                    primaryEnabled = true,
                    onPrimary = onStopMarking,
                    orientation = orientation,
                    onToggleOrientation = onToggleOrientation,
                    compact = compact,
                )
            }
            DriveMarkingPhase.Review -> {
                // Track Review owns its own actions; no primary CTA here.
            }
            DriveMarkingPhase.Idle -> if (rawRecordingActive) {
                // Diagnostic raw recording in progress (D-16): samples are captured
                // for replay/diagnosis but NO lap/ghost timing has started (D-17).
                Text(
                    text = "Recording raw GPS for diagnosis. No lap timing is running.",
                    color = Color(0xFFFFD166),
                    fontSize = if (compact) 11.sp else 13.sp,
                    lineHeight = if (compact) 15.sp else 17.sp,
                )
                Button(
                    onClick = onStopRawRecording,
                    modifier = Modifier.fillMaxWidth().height(if (compact) 48.dp else 54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB8343A)),
                ) {
                    Text("Stop raw recording")
                }
            } else {
                TrackSelectorSection(
                    snapshot = snapshot,
                    onSelectProfile = onSelectProfile,
                    onBeginMarking = onBeginMarking,
                    compact = compact,
                )
                if (snapshot.canStartTiming) {
                    DirectionSelectorSection(
                        selected = snapshot.selectedDirection,
                        onSelectDirection = onSelectDirection,
                        compact = compact,
                    )
                }
                DriveActionRow(
                    primaryIcon = PlayActionIcon,
                    primaryDescription = "Start timing",
                    primaryContainerColor = MaterialTheme.colorScheme.primary,
                    primaryEnabled = snapshot.canStartTiming,
                    onPrimary = onStartTiming,
                    orientation = orientation,
                    onToggleOrientation = onToggleOrientation,
                    compact = compact,
                )
                if (!snapshot.canStartTiming && snapshot.needsTrackSelection) {
                    Text(
                        text = "Choose a track to start timing.",
                        color = Color(0xFFFFD166),
                        fontSize = if (compact) 11.sp else 13.sp,
                        lineHeight = if (compact) 15.sp else 17.sp,
                    )
                }
                startTimingBlockedMessage?.let { message ->
                    Text(
                        text = message,
                        color = Color(0xFFFFD166),
                        fontSize = if (compact) 11.sp else 13.sp,
                        lineHeight = if (compact) 15.sp else 17.sp,
                    )
                }
                // When not Ready the user cannot start trustworthy formal timing, so
                // expose the raw-recording diagnostic path instead (D-16).
                if (dashReady is ReadyState.NotReady) {
                    OutlinedButton(
                        onClick = onStartRawRecording,
                        modifier = Modifier.fillMaxWidth().height(if (compact) 44.dp else 48.dp),
                    ) {
                        Text("Record raw GPS (diagnostic)")
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveActionRow(
    primaryIcon: androidx.compose.ui.graphics.vector.ImageVector,
    primaryDescription: String,
    primaryContainerColor: Color,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    orientation: DashOrientation,
    onToggleOrientation: () -> Unit,
    compact: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onPrimary,
            enabled = primaryEnabled,
            modifier = Modifier.weight(1f).height(if (compact) 48.dp else 54.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryContainerColor),
        ) {
            Icon(
                imageVector = primaryIcon,
                contentDescription = primaryDescription,
                modifier = Modifier.size(if (compact) 24.dp else 28.dp),
            )
        }
        Button(
            onClick = onToggleOrientation,
            modifier = Modifier.weight(1f).height(if (compact) 48.dp else 54.dp),
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
                modifier = Modifier.size(if (compact) 24.dp else 28.dp),
            )
        }
    }
}

@Composable
private fun PhoneGpsBadge(compact: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Color(0xFF8CFF9B), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = "PHONE GPS — live",
            color = Color(0xFF8CFF9B),
            fontSize = if (compact) 11.sp else 13.sp,
            fontWeight = FontWeight.Bold,
        )
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
    onBeginMarking: () -> Unit,
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
                    if (snapshot.selectableProfiles.isEmpty()) {
                        Text(
                            text = "No saved tracks yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    } else {
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
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTrackPicker = false
                        onBeginMarking()
                    },
                ) {
                    Text("Mark New Track")
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrackPicker = false }) {
                    Text("Close")
                }
            },
        )
    }

    OutlinedButton(
        onClick = { showTrackPicker = true },
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = if (compact) 12.dp else 14.dp, vertical = if (compact) 9.dp else 11.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TRACK",
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
                    fontSize = if (compact) 15.sp else 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = ">",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = if (compact) 18.sp else 20.sp,
                fontWeight = FontWeight.Bold,
            )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "DIRECTION",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.9f),
        )
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

@Composable
private fun DirectionChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (active) {
        Button(
            onClick = onClick,
            modifier = modifier.height(if (compact) 36.dp else 40.dp),
            contentPadding = PaddingValues(horizontal = if (compact) 8.dp else 10.dp, vertical = 0.dp),
        ) {
            Text((if (active) "✓ " else "") + label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(if (compact) 36.dp else 40.dp),
            contentPadding = PaddingValues(horizontal = if (compact) 8.dp else 10.dp, vertical = 0.dp),
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
            if (review.extraction.markingSession.source.isSimulated) {
                DemoBadge()
            } else {
                PhoneGpsBadge()
            }
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
