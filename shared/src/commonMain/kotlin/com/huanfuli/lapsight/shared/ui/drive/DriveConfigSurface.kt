package com.huanfuli.lapsight.shared.ui.drive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huanfuli.lapsight.shared.DashOrientation
import com.huanfuli.lapsight.shared.DriveDisplaySettings
import com.huanfuli.lapsight.shared.LocationFeedMode
import com.huanfuli.lapsight.shared.PhoneGpsPermissionState
import com.huanfuli.lapsight.shared.lap.formatLapTime
import com.huanfuli.lapsight.shared.review.buildTrackTraceLayers
import com.huanfuli.lapsight.shared.session.RawRecordingSnapshot
import com.huanfuli.lapsight.shared.session.ReadyState
import com.huanfuli.lapsight.shared.session.SessionControllerSnapshot
import com.huanfuli.lapsight.shared.session.TimingRunSnapshot
import com.huanfuli.lapsight.shared.session.toDto
import com.huanfuli.lapsight.shared.storage.LoadResult
import com.huanfuli.lapsight.shared.storage.LocalSessionStore
import com.huanfuli.lapsight.shared.track.CourseDirection
import com.huanfuli.lapsight.shared.track.SectorLineDto
import com.huanfuli.lapsight.shared.track.TrackProfile
import com.huanfuli.lapsight.shared.ui.DriveMarkingPhase
import com.huanfuli.lapsight.shared.ui.DriveMarkingSnapshot
import com.huanfuli.lapsight.shared.ui.LapSightTheme
import com.huanfuli.lapsight.shared.ui.PlayActionIcon
import com.huanfuli.lapsight.shared.ui.RotateScreenIcon
import com.huanfuli.lapsight.shared.ui.StopActionIcon
import com.huanfuli.lapsight.shared.ui.TraceView
import com.huanfuli.lapsight.shared.ui.components.LapButton
import com.huanfuli.lapsight.shared.ui.components.LapButtonSize
import com.huanfuli.lapsight.shared.ui.components.LapButtonStyle
import com.huanfuli.lapsight.shared.ui.components.LapDialog
import com.huanfuli.lapsight.shared.ui.components.MetricCell
import com.huanfuli.lapsight.shared.ui.components.MetricCellSize
import com.huanfuli.lapsight.shared.ui.components.SegmentedControl

/**
 * Stationary Drive surface host: status bar + config controls, or the
 * fullscreen dash / Track Review depending on phase. Layout follows the
 * deliberately locked window, not device tilt.
 */
@Composable
internal fun DriveSurface(
    snapshot: DriveMarkingSnapshot,
    orientation: DashOrientation,
    displaySettings: DriveDisplaySettings,
    locationFeedMode: LocationFeedMode,
    phoneGpsPermission: PhoneGpsPermissionState,
    sessionStore: LocalSessionStore,
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
    timingSnapshot: SessionControllerSnapshot?,
    timingRun: TimingRunSnapshot,
    startTimingBlockedMessage: String?,
    dashReady: ReadyState,
    rawRecordingActive: Boolean,
    rawSnapshot: RawRecordingSnapshot,
    reviewContent: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val isLandscape = maxWidth > maxHeight
        val isCompactLandscape = isLandscape && maxHeight < 520.dp
        val spacing = LapSightTheme.spacing
        val padding = if (isCompactLandscape) spacing.sm else spacing.md

        if (snapshot.phase == DriveMarkingPhase.Review) {
            // Track Review replaces the dash controls (D-31).
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeContentPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                reviewContent()
            }
            return@BoxWithConstraints
        }

        // While formal timing is active, show the fullscreen timing surface with
        // current/last/best/laps/speed/accuracy (D-29, D-42, SESS-01).
        if (timingActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LapSightTheme.colors.dashBackground)
                    .safeContentPadding(),
            ) {
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
            }
            return@BoxWithConstraints
        }

        if (isLandscape) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = spacing.sm, vertical = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
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
                    sessionStore = sessionStore,
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
            // Portrait: the middle of the screen belongs to the course — a live
            // marking trace while capturing, the selected track's map otherwise —
            // and the primary action row anchors to the bottom, in thumb reach.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
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
                    sessionStore = sessionStore,
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
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    startTimingBlockedMessage = startTimingBlockedMessage,
                    fillHeight = true,
                )
            }
        }
    }
}

/**
 * Pre-timing configuration controls. With [fillHeight] the middle section
 * (course preview / empty state / live marking feedback) expands and the
 * action row is pushed to the bottom of the available space.
 */
@Composable
private fun ControlPanel(
    snapshot: DriveMarkingSnapshot,
    orientation: DashOrientation,
    sessionStore: LocalSessionStore,
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
    fillHeight: Boolean = false,
) {
    val spacing = LapSightTheme.spacing
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        when (snapshot.phase) {
            DriveMarkingPhase.Capturing -> {
                // Live capture feedback: the trace being drawn, marking time, and
                // what to do — the screen must confirm the app is recording.
                MarkingLiveSection(
                    snapshot = snapshot,
                    compact = compact,
                    modifier = if (fillHeight) Modifier.weight(1f) else Modifier,
                )
                DriveActionRow(
                    primaryIcon = StopActionIcon,
                    primaryLabel = "Stop marking",
                    primaryDescription = "Stop marking",
                    primaryContainerColor = MaterialTheme.colorScheme.errorContainer,
                    primaryContentColor = MaterialTheme.colorScheme.onErrorContainer,
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
                    color = LapSightTheme.colors.statusCaution,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (fillHeight) Spacer(Modifier.weight(1f))
                LapButton(
                    text = "Stop raw recording",
                    onClick = onStopRawRecording,
                    style = LapButtonStyle.Destructive,
                    size = LapButtonSize.Large,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                var showTrackPicker by remember { mutableStateOf(false) }
                if (showTrackPicker) {
                    TrackPickerDialog(
                        snapshot = snapshot,
                        onSelectProfile = onSelectProfile,
                        onBeginMarking = onBeginMarking,
                        onClose = { showTrackPicker = false },
                    )
                }

                TrackSelectorSection(
                    snapshot = snapshot,
                    onClick = { showTrackPicker = true },
                    compact = compact,
                )

                if (snapshot.currentTrackName != null) {
                    // The selected course fills the middle — the map is the reason
                    // to glance at this screen before starting.
                    SelectedTrackPreview(
                        profileId = snapshot.timingReadyTrackId,
                        sessionStore = sessionStore,
                        compact = compact,
                        modifier = if (fillHeight) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth(),
                    )
                } else {
                    NoTrackState(
                        hasSavedTracks = snapshot.selectableProfiles.isNotEmpty(),
                        onChooseTrack = { showTrackPicker = true },
                        onBeginMarking = onBeginMarking,
                        modifier = if (fillHeight) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth(),
                    )
                }

                if (snapshot.canStartTiming) {
                    DirectionSelectorSection(
                        selected = snapshot.selectedDirection,
                        onSelectDirection = onSelectDirection,
                    )
                }
                startTimingBlockedMessage?.let { message ->
                    Text(
                        text = message,
                        color = LapSightTheme.colors.statusCaution,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                DriveActionRow(
                    primaryIcon = PlayActionIcon,
                    primaryLabel = "Start Timing",
                    primaryDescription = "Start timing",
                    primaryContainerColor = MaterialTheme.colorScheme.primary,
                    primaryContentColor = MaterialTheme.colorScheme.onPrimary,
                    primaryEnabled = snapshot.canStartTiming,
                    onPrimary = onStartTiming,
                    orientation = orientation,
                    onToggleOrientation = onToggleOrientation,
                    compact = compact,
                )
                // When not Ready the user cannot start trustworthy formal timing, so
                // expose the raw-recording diagnostic path — low emphasis, it's a
                // diagnostic tool, not part of the driving flow (D-16).
                if (dashReady is ReadyState.NotReady) {
                    LapButton(
                        text = "Record raw GPS (diagnostic)",
                        onClick = onStartRawRecording,
                        style = LapButtonStyle.Ghost,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * Live feedback while marking (D-06..D-08): marking clock, captured points,
 * the accumulating trace, and the loop guidance. The trace redraws every ~10
 * samples so the canvas work stays off the per-sample hot path.
 */
@Composable
private fun MarkingLiveSection(
    snapshot: DriveMarkingSnapshot,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = LapSightTheme.spacing
    val samples = snapshot.capturedSamples
    val elapsedMillis = if (samples.size >= 2) {
        samples.last().elapsedMillis - samples.first().elapsedMillis
    } else {
        0L
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            MetricCell(
                label = "MARKING TIME",
                value = elapsedMillis.formatLapTime(),
                modifier = Modifier.weight(1f),
                size = MetricCellSize.Compact,
            )
            MetricCell(
                label = "POINTS",
                value = samples.size.toString(),
                modifier = Modifier.weight(1f),
                size = MetricCellSize.Compact,
            )
        }
        // Rebuild the drawn layers in coarse steps, not on every GPS sample.
        val layerStep = samples.size / 10
        val layers = remember(layerStep) {
            buildTrackTraceLayers(
                markingSamples = samples.map { it.toDto() },
                referenceLine = null,
                startFinish = null,
                sectors = emptyList(),
                outlierSamples = emptyList(),
                viewWidth = 400.0,
                viewHeight = 300.0,
            )
        }
        if (layers.isNotEmpty()) {
            TraceView(
                layers = layers,
                minHeight = if (compact) 120.dp else 200.dp,
                maxHeight = if (compact) 170.dp else 340.dp,
            )
        } else {
            Text(
                text = "Waiting for the first GPS fix…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = "Drive at least 3 continuous loops of the course, then stop marking.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Course map of the currently selected Track (latest revision), with its
 * start/finish and sector lines — fills the pre-timing middle so the screen
 * shows what is about to be timed.
 */
@Composable
private fun SelectedTrackPreview(
    profileId: String?,
    sessionStore: LocalSessionStore,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    if (profileId == null) return
    val profile = remember(profileId) {
        (sessionStore.loadProfile(profileId) as? LoadResult.Loaded<TrackProfile>)?.value
    }
    val latest = profile?.latestRevision
    val referenceLine = latest?.referenceLine
    if (referenceLine == null || referenceLine.points.isEmpty()) return

    val layers = remember(profileId, latest.ordinal) {
        buildTrackTraceLayers(
            markingSamples = emptyList(),
            referenceLine = referenceLine,
            startFinish = latest.courseSetup.startFinish,
            sectors = latest.courseSetup.boundaries.map {
                SectorLineDto(id = it.id, name = "Sector ${it.order}", order = it.order, pointA = it.pointA, pointB = it.pointB)
            },
            outlierSamples = emptyList(),
            viewWidth = 400.0,
            viewHeight = 300.0,
        )
    }
    if (layers.isEmpty()) return
    val spacing = LapSightTheme.spacing
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        TraceView(
            layers = layers,
            minHeight = if (compact) 120.dp else 200.dp,
            maxHeight = if (compact) 180.dp else 360.dp,
        )
        val sectorCount = latest.courseSetup.boundaries.size
        Text(
            text = buildString {
                append("Rev ${latest.ordinal}")
                if (sectorCount > 0) append(" · ${sectorCount + 1} sectors")
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Centered empty state when no Track is selected: names the situation once
 * and leads with the action that fits it — marking a first track, or picking
 * a saved one.
 */
@Composable
private fun NoTrackState(
    hasSavedTracks: Boolean,
    onChooseTrack: () -> Unit,
    onBeginMarking: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LapSightTheme.spacing
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No track selected",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = if (hasSavedTracks) {
                "Pick a saved track to start timing."
            } else {
                "Mark your first track by driving loops of the course."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.md))
        if (hasSavedTracks) {
            LapButton(
                text = "Choose track",
                onClick = onChooseTrack,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(spacing.sm))
            LapButton(
                text = "Mark New Track",
                onClick = onBeginMarking,
                style = LapButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LapButton(
                text = "Mark New Track",
                onClick = onBeginMarking,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DriveActionRow(
    primaryIcon: ImageVector,
    primaryLabel: String,
    primaryDescription: String,
    primaryContainerColor: Color,
    primaryContentColor: Color,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    orientation: DashOrientation,
    onToggleOrientation: () -> Unit,
    compact: Boolean = false,
) {
    val spacing = LapSightTheme.spacing
    val actionHeight = if (compact) 48.dp else 56.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Button(
            onClick = onPrimary,
            enabled = primaryEnabled,
            modifier = Modifier.weight(1f).height(actionHeight),
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryContainerColor,
                contentColor = primaryContentColor,
                disabledContainerColor = LapSightTheme.colors.disabledContainer,
                disabledContentColor = LapSightTheme.colors.disabledContent,
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Icon(
                    imageVector = primaryIcon,
                    contentDescription = primaryDescription,
                    modifier = Modifier.size(if (compact) 22.dp else 24.dp),
                )
                if (!compact) {
                    Text(
                        text = primaryLabel,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
        }
        Button(
            onClick = onToggleOrientation,
            modifier = Modifier.weight(0.34f).height(actionHeight),
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
                modifier = Modifier.size(if (compact) 24.dp else 28.dp),
            )
        }
    }
}

/**
 * Track picker (D-01, D-02, D-03): active latest-revision profiles as explicit
 * selection actions. The current Track renders as the highlighted selection —
 * not disabled — and not-timing-ready tracks explain themselves.
 */
@Composable
private fun TrackPickerDialog(
    snapshot: DriveMarkingSnapshot,
    onSelectProfile: (String) -> Unit,
    onBeginMarking: () -> Unit,
    onClose: () -> Unit,
) {
    val spacing = LapSightTheme.spacing
    LapDialog(
        title = "Choose track",
        onDismissRequest = onClose,
        confirmText = "Close",
        onConfirm = onClose,
        dismissText = "Mark New Track",
        onDismiss = {
            onClose()
            onBeginMarking()
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                if (snapshot.selectableProfiles.isEmpty()) {
                    Text(
                        text = "No saved tracks yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    snapshot.selectableProfiles.forEach { row ->
                        val isCurrent = row.profileId == snapshot.timingReadyTrackId
                        TrackPickerRow(
                            name = row.name,
                            isCurrent = isCurrent,
                            isTimingReady = row.isTimingReady,
                            onClick = {
                                if (row.isTimingReady && !isCurrent) {
                                    onSelectProfile(row.profileId)
                                }
                                onClose()
                            },
                        )
                    }
                }
            }
        },
    )
}

/** One selectable track row: selected = accent border, not faded-out disabled. */
@Composable
private fun TrackPickerRow(
    name: String,
    isCurrent: Boolean,
    isTimingReady: Boolean,
    onClick: () -> Unit,
) {
    val spacing = LapSightTheme.spacing
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (isCurrent) MaterialTheme.colorScheme.primary else LapSightTheme.colors.cardBorder,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = spacing.md, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = if (isTimingReady) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        LapSightTheme.colors.disabledContent
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!isTimingReady) {
                    Text(
                        text = "Needs start/finish before timing",
                        color = LapSightTheme.colors.statusCaution,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (isCurrent) {
                Text(
                    text = "Current",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/**
 * Compact pre-Timing Track selector (D-01, D-02, D-03): always states the
 * current Track by name or clearly states none. Tapping opens the picker.
 * Rendered only on the stationary Drive surface, never the moving dash.
 */
@Composable
private fun TrackSelectorSection(
    snapshot: DriveMarkingSnapshot,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val spacing = LapSightTheme.spacing
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, LapSightTheme.colors.cardBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TRACK",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = snapshot.currentTrackName ?: "No track selected",
                    color = if (snapshot.currentTrackName != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = if (compact) {
                        MaterialTheme.typography.titleSmall
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "›",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

/**
 * Pre-Timing Course Direction selector (D-18): run the SAME saved revision in
 * the Recorded direction or its Reverse without re-marking. Explicit choice,
 * no automatic recommendation; stationary surface only (safety).
 */
@Composable
private fun DirectionSelectorSection(
    selected: CourseDirection,
    onSelectDirection: (CourseDirection) -> Unit,
) {
    val spacing = LapSightTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            text = "DIRECTION",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(0.6f),
        )
        SegmentedControl(
            options = listOf("Recorded", "Reverse"),
            selectedIndex = if (selected == CourseDirection.Recorded) 0 else 1,
            onSelect = { index ->
                onSelectDirection(
                    if (index == 0) CourseDirection.Recorded else CourseDirection.Reverse,
                )
            },
            modifier = Modifier.weight(1.4f),
        )
    }
}
