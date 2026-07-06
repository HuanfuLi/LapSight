package com.huanfuli.lapsight.shared.ui.drive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.huanfuli.lapsight.shared.DashOrientation
import com.huanfuli.lapsight.shared.DriveDisplaySettings
import com.huanfuli.lapsight.shared.LocationFeedMode
import com.huanfuli.lapsight.shared.LocationSampleProvider
import com.huanfuli.lapsight.shared.OrientationController
import com.huanfuli.lapsight.shared.PhoneGpsPermissionState
import com.huanfuli.lapsight.shared.session.RawRecordingController
import com.huanfuli.lapsight.shared.session.SaveDraftResult
import com.huanfuli.lapsight.shared.session.SessionController
import com.huanfuli.lapsight.shared.session.StartTimingResult
import com.huanfuli.lapsight.shared.session.TimingRunSnapshot
import com.huanfuli.lapsight.shared.storage.LocalSessionStore
import com.huanfuli.lapsight.shared.ui.CheckActionIcon
import com.huanfuli.lapsight.shared.ui.CloseActionIcon
import com.huanfuli.lapsight.shared.ui.DeleteActionIcon
import com.huanfuli.lapsight.shared.ui.DriveMarkingController
import com.huanfuli.lapsight.shared.ui.DriveMarkingPhase
import com.huanfuli.lapsight.shared.ui.LapSightTheme
import com.huanfuli.lapsight.shared.ui.SaveSessionIcon
import com.huanfuli.lapsight.shared.ui.START_TIMING_BLOCKED_COPY
import com.huanfuli.lapsight.shared.ui.components.LapDialog
import com.huanfuli.lapsight.shared.ui.components.LapDialogTextButton
import com.huanfuli.lapsight.shared.ui.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val DISCARD_SESSION_ARMED_TIMEOUT_MILLIS = 4500L

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
    val s = strings
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
            startTimingBlockedMessage = s.phoneGpsPermissionRequired
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

    val phoneGpsPrewarmActive =
        locationFeedMode == LocationFeedMode.PhoneGps &&
            phoneGpsPermission.isGranted &&
            !timingActive &&
            !rawRecordingActive

    LaunchedEffect(phoneGpsPrewarmActive, locationProvider) {
        if (phoneGpsPrewarmActive) {
            if (!locationProvider.isRunning) {
                locationProvider.start()
            }
            snapshot = controller.snapshot()
        }
    }

    LaunchedEffect(showStopSummary, confirmDiscardSession) {
        if (showStopSummary && confirmDiscardSession) {
            delay(DISCARD_SESSION_ARMED_TIMEOUT_MILLIS)
            confirmDiscardSession = false
        }
    }

    LaunchedEffect(startTimingBlockedMessage) {
        if (startTimingBlockedMessage != null) {
            delay(2400L)
            startTimingBlockedMessage = null
        }
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
            val samples = controller.tick()
            snapshot = controller.snapshot()
            if (timingActive) {
                // Production sample pump: feed the active recorder through the
                // controller (never recorderForTest) and read the timing/delta
                // view back for the UI. Ingest every sample drained this tick so a
                // buffered backlog is never dropped.
                if (samples.isNotEmpty()) {
                    withContext(Dispatchers.Default) {
                        recorderMutex.withLock {
                            samples.forEach { sessionController.ingestSample(it) }
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
        LapDialog(
            title = s.checkSelectedTrack,
            text = blocked.message,
            onDismissRequest = { wrongCourseBlock = null },
            confirmText = s.stillUseThisTrack,
            confirmIcon = CheckActionIcon,
            onConfirm = {
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
            dismissText = s.chooseAnotherTrack,
            dismissIcon = CloseActionIcon,
        )
    }

    // Stop summary sheet (D-14): one explicit Save/Discard choice, with the
    // destructive branch armed in place instead of opening a second dialog.
    if (showStopSummary) {
        val laps = timingSnapshot?.activeDraft?.checkpointedLapCount ?: 0
        LapDialog(
            title = s.sessionEnded,
            text = if (confirmDiscardSession) {
                s.lapsRecordedTapDiscard(laps)
            } else {
                s.lapsRecordedSaveOrDiscard(laps)
            },
            onDismissRequest = {
                if (confirmDiscardSession) confirmDiscardSession = false
            },
            buttons = {
                Spacer(Modifier.weight(1f))
                LapDialogTextButton(
                    text = if (confirmDiscardSession) s.tapAgainDiscardSession else s.discard,
                    destructive = confirmDiscardSession,
                    enabled = !saveInProgress,
                    icon = DeleteActionIcon,
                    iconOnly = true,
                    contentDescription = if (confirmDiscardSession) {
                        s.tapAgainDiscardSession
                    } else {
                        s.discardSession
                    },
                    onClick = {
                        if (confirmDiscardSession) {
                            confirmDiscardSession = false
                            showStopSummary = false
                            sessionController.discardDraft()
                            timingActive = false
                            timingSnapshot = null
                            timingRun = TimingRunSnapshot.inactive()
                        } else {
                            confirmDiscardSession = true
                        }
                    },
                )
                Spacer(Modifier.width(LapSightTheme.spacing.sm))
                LapDialogTextButton(
                    text = if (saveInProgress) s.savingSession else s.saveSession,
                    enabled = !saveInProgress,
                    icon = SaveSessionIcon,
                    iconOnly = true,
                    contentDescription = if (saveInProgress) s.savingSession else s.saveSession,
                    onClick = {
                        saveInProgress = true
                        uiScope.launch {
                            val result = withContext(Dispatchers.Default) {
                                recorderMutex.withLock {
                                    sessionController.saveStoppedDraft()
                                }
                            }
                            saveInProgress = false
                            confirmDiscardSession = false
                            showStopSummary = false
                            timingActive = false
                            timingSnapshot = null
                            timingRun = TimingRunSnapshot.inactive()
                            if (result is SaveDraftResult.Saved) {
                                saveToast = s.sessionSaved
                                onSavedSession()
                            }
                        }
                    },
                )
            },
        )
    }

    DriveSurface(
        snapshot = snapshot,
        orientation = orientation,
        displaySettings = displaySettings,
        locationFeedMode = locationFeedMode,
        phoneGpsPermission = phoneGpsPermission,
        sessionStore = sessionStore,
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
                        // Hard Start gate only: low rate/accuracy remain visible warnings.
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
                confirmDiscardSession = false
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

    // Transient save success / blocked-start toast, drawn over the surface so
    // warning copy never reserves Drive layout space or shifts controls.
    val blockedToast = startTimingBlockedMessage.takeIf { wrongCourseBlock == null }
    val toast = blockedToast ?: saveToast
    toast?.let { message ->
        val isWarning = blockedToast != null
        LaunchedEffect(message, isWarning) {
            if (isWarning) return@LaunchedEffect
            delay(2000)
            saveToast = null
        }
        DriveToast(message = message, warning = isWarning)
    }
}

@Composable
private fun DriveToast(
    message: String,
    warning: Boolean,
) {
    val spacing = LapSightTheme.spacing
    val color = if (warning) LapSightTheme.colors.statusCaution else LapSightTheme.colors.statusReady
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeContentPadding()
            .padding(spacing.md),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, color),
        ) {
            Text(
                text = message,
                color = color,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(
                    horizontal = spacing.md,
                    vertical = spacing.sm,
                ),
            )
        }
    }
}
