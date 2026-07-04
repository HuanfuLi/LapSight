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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huanfuli.lapsight.shared.DashOrientation
import com.huanfuli.lapsight.shared.DriveDisplaySettings
import com.huanfuli.lapsight.shared.LocationFeedMode
import com.huanfuli.lapsight.shared.PhoneGpsPermissionState
import com.huanfuli.lapsight.shared.session.RawRecordingSnapshot
import com.huanfuli.lapsight.shared.session.ReadyState
import com.huanfuli.lapsight.shared.session.SessionControllerSnapshot
import com.huanfuli.lapsight.shared.session.TimingRunSnapshot
import com.huanfuli.lapsight.shared.track.CourseDirection
import com.huanfuli.lapsight.shared.ui.DriveMarkingPhase
import com.huanfuli.lapsight.shared.ui.DriveMarkingSnapshot
import com.huanfuli.lapsight.shared.ui.LapSightTheme
import com.huanfuli.lapsight.shared.ui.PlayActionIcon
import com.huanfuli.lapsight.shared.ui.RotateScreenIcon
import com.huanfuli.lapsight.shared.ui.StopActionIcon
import com.huanfuli.lapsight.shared.ui.components.LapButton
import com.huanfuli.lapsight.shared.ui.components.LapButtonSize
import com.huanfuli.lapsight.shared.ui.components.LapButtonStyle
import com.huanfuli.lapsight.shared.ui.components.LapDialog
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
            // Portrait: no dead space — config fills the height and the primary
            // action row anchors to the bottom, in thumb reach on a mounted phone.
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
 * Pre-timing configuration controls. With [fillHeight] the action row is
 * pushed to the bottom of the available space (portrait mounted use).
 */
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
    fillHeight: Boolean = false,
) {
    val spacing = LapSightTheme.spacing
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        when (snapshot.phase) {
            DriveMarkingPhase.Capturing -> {
                if (fillHeight) Spacer(Modifier.weight(1f))
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
                    )
                }
                if (!snapshot.canStartTiming && snapshot.needsTrackSelection) {
                    Text(
                        text = "Choose a track to start timing.",
                        color = LapSightTheme.colors.statusCaution,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                startTimingBlockedMessage?.let { message ->
                    Text(
                        text = message,
                        color = LapSightTheme.colors.statusCaution,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (fillHeight) Spacer(Modifier.weight(1f))
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
                // expose the raw-recording diagnostic path instead (D-16).
                if (dashReady is ReadyState.NotReady) {
                    LapButton(
                        text = "Record raw GPS (diagnostic)",
                        onClick = onStartRawRecording,
                        style = LapButtonStyle.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
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
 * Compact pre-Timing Track selector (D-01, D-02, D-03).
 *
 * Always states the current Track by name (or clearly states none), and lists the
 * active, latest-revision profiles as direct selection actions so a blocked Timing
 * has an obvious recovery path. Selection is always explicit (D-04). Rendered only
 * on the stationary Drive surface, never on the moving fullscreen dash (safety).
 */
@Composable
private fun TrackSelectorSection(
    snapshot: DriveMarkingSnapshot,
    onSelectProfile: (String) -> Unit,
    onBeginMarking: () -> Unit,
    compact: Boolean = false,
) {
    val spacing = LapSightTheme.spacing
    var showTrackPicker by remember { mutableStateOf(false) }

    if (showTrackPicker) {
        LapDialog(
            title = "Choose track",
            onDismissRequest = { showTrackPicker = false },
            confirmText = "Close",
            onConfirm = { showTrackPicker = false },
            dismissText = "Mark New Track",
            onDismiss = {
                showTrackPicker = false
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
                            val suffix = when {
                                isCurrent -> " (current)"
                                !row.isTimingReady -> " (needs start/finish)"
                                else -> ""
                            }
                            LapButton(
                                text = row.name + suffix,
                                onClick = {
                                    onSelectProfile(row.profileId)
                                    showTrackPicker = false
                                },
                                style = LapButtonStyle.Secondary,
                                enabled = row.isTimingReady && !isCurrent,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
        )
    }

    Surface(
        onClick = { showTrackPicker = true },
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
                        LapSightTheme.colors.statusCaution
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
