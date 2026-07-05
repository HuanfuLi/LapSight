package com.huanfuli.lapsight.shared.ui.drive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.huanfuli.lapsight.shared.review.buildTrackTraceLayers
import com.huanfuli.lapsight.shared.track.TrackReviewState
import com.huanfuli.lapsight.shared.ui.DriveMarkingController
import com.huanfuli.lapsight.shared.ui.DriveMarkingSnapshot
import com.huanfuli.lapsight.shared.ui.LapSightTheme
import com.huanfuli.lapsight.shared.ui.TraceView
import com.huanfuli.lapsight.shared.ui.components.ChipTone
import com.huanfuli.lapsight.shared.ui.components.DisclosureSection
import com.huanfuli.lapsight.shared.ui.components.LapButton
import com.huanfuli.lapsight.shared.ui.components.LapButtonStyle
import com.huanfuli.lapsight.shared.ui.components.LapCard
import com.huanfuli.lapsight.shared.ui.components.LapDialog
import com.huanfuli.lapsight.shared.ui.components.MetricCell
import com.huanfuli.lapsight.shared.ui.components.MetricCellSize
import com.huanfuli.lapsight.shared.ui.components.StatusChip

/**
 * Track Review surface rendered when a marking capture stops (D-31).
 *
 * Leads with the just-captured course map — the one artifact that shows the
 * capture worked. A clean capture offers exactly two actions: Save Track
 * (which places the start/finish at the recorded start when not already set —
 * same call the old explicit button made; adjustable later in the course
 * editor) and Discard. A failed capture leads with Re-record as the recovery
 * path instead of dominant disabled buttons. Track Review never shows lap
 * times for marking samples (D-08); Re-record and Discard require explicit
 * confirmation.
 *
 * Portrait renders a scrolling card with QA numbers behind a Capture-details
 * disclosure. Landscape renders a fixed two-pane spread — map left, actions
 * right, QA numbers as a caption under the map — with no scrolling.
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
        LapDialog(
            title = "Re-record track?",
            text = "Re-record track? The current marking trace and reference line will be replaced.",
            onDismissRequest = { confirmReRecord = false },
            confirmText = "Re-record",
            onConfirm = {
                confirmReRecord = false
                controller.reRecord()
                onChanged()
            },
            dismissText = "Cancel",
        )
    }
    if (confirmDiscard) {
        LapDialog(
            title = "Discard track?",
            text = "Discard this track? You'll lose the marking trace and reference line.",
            onDismissRequest = { confirmDiscard = false },
            confirmText = "Discard",
            destructiveConfirm = true,
            onConfirm = {
                confirmDiscard = false
                controller.discard()
                onChanged()
            },
            dismissText = "Cancel",
        )
    }

    val onNameChange: (String) -> Unit = {
        trackName = it
        controller.setTrackName(it)
    }
    val onSave = {
        controller.setTrackName(trackName)
        controller.saveTrack()
        onChanged()
        onSavedTrack()
    }

    BoxWithConstraints {
        if (maxWidth > maxHeight) {
            TrackReviewLandscape(
                review = review,
                trackName = trackName,
                onNameChange = onNameChange,
                onSave = onSave,
                onReRecordRequest = { confirmReRecord = true },
                onDiscardRequest = { confirmDiscard = true },
            )
        } else {
            TrackReviewPortraitCard(
                review = review,
                trackName = trackName,
                onNameChange = onNameChange,
                onSave = onSave,
                onReRecordRequest = { confirmReRecord = true },
                onDiscardRequest = { confirmDiscard = true },
            )
        }
    }
}

/** One-line status for a clean capture, shared by both orientations. */
private fun cleanCaptureStatus(review: TrackReviewState): String =
    "${review.extraction.acceptedLoopCount} clean loops captured. " +
        if (review.startFinish != null) {
            "Start/finish is set."
        } else {
            "Saving places the start/finish at the recorded start — adjustable later in the course editor."
        }

private const val FAILED_CAPTURE_STATUS =
    "Couldn't build a clean track. Re-record 3 continuous closed-course loops and avoid long stops."

// Marking is continuous capture, NOT lap timing — no lap times shown (D-08).
private const val NO_LAP_TIMES_NOTE = "Marking capture does not produce lap times."

@Composable
private fun TrackReviewPortraitCard(
    review: TrackReviewState,
    trackName: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onReRecordRequest: () -> Unit,
    onDiscardRequest: () -> Unit,
) {
    val spacing = LapSightTheme.spacing
    LapCard {
        Text(
            text = "Track Review",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
        )
        TrackReviewSourceChip(review)

        // The captured course, drawn from the same layers Review uses. Shown for
        // failed captures too — seeing the broken trace explains the failure.
        CapturedCourseMap(review = review)

        if (review.canSave) {
            Text(
                text = cleanCaptureStatus(review),
                color = LapSightTheme.colors.statusReady,
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(spacing.xs))
            // Name this Track before saving (D-02). Blank falls back to the default
            // name in the controller; the name never forms a storage path (T-05-07).
            OutlinedTextField(
                value = trackName,
                onValueChange = onNameChange,
                label = { Text("Track name") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            // Save Track — the one primary action on a clean capture.
            LapButton(
                text = "Save Track",
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            )
            LapButton(
                text = "Discard",
                onClick = onDiscardRequest,
                style = LapButtonStyle.GhostDestructive,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = FAILED_CAPTURE_STATUS,
                color = LapSightTheme.colors.statusCaution,
                style = MaterialTheme.typography.bodyLarge,
            )
            // Failed capture: recovery leads (Re-record), no disabled Save in sight.
            LapButton(
                text = "Re-record",
                onClick = onReRecordRequest,
                modifier = Modifier.fillMaxWidth(),
            )
            LapButton(
                text = "Discard",
                onClick = onDiscardRequest,
                style = LapButtonStyle.GhostDestructive,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text(
            text = NO_LAP_TIMES_NOTE,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        DisclosureSection(title = "Capture details") {
            MetricCell(
                label = "Loops",
                value = "${review.extraction.detectedLoopCount} detected · ${review.extraction.acceptedLoopCount} accepted · ${review.extraction.rejectedLoopCount} rejected",
                size = MetricCellSize.Row,
            )
            MetricCell(
                label = "Samples",
                value = "${review.rawSampleCount} · degraded: ${review.quality.degradedSampleCount}",
                size = MetricCellSize.Row,
            )
        }
    }
}

/**
 * Landscape spread: captured course fills the left pane over a QA caption and
 * the D-08 note; the right rail holds identity, status, and the two actions
 * bottom-anchored. Everything is on screen at once — nothing scrolls.
 */
@Composable
private fun TrackReviewLandscape(
    review: TrackReviewState,
    trackName: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onReRecordRequest: () -> Unit,
    onDiscardRequest: () -> Unit,
) {
    val spacing = LapSightTheme.spacing
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            CapturedCourseMap(
                review = review,
                modifier = Modifier.weight(1f),
                fillParent = true,
            )
            Text(
                text = "${review.extraction.detectedLoopCount} loops detected · " +
                    "${review.extraction.acceptedLoopCount} accepted · " +
                    "${review.rawSampleCount} samples · " +
                    "${review.quality.degradedSampleCount} degraded",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = NO_LAP_TIMES_NOTE,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Column(
            modifier = Modifier.width(320.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = "Track Review",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
            )
            TrackReviewSourceChip(review)
            if (review.canSave) {
                Text(
                    text = cleanCaptureStatus(review),
                    color = LapSightTheme.colors.statusReady,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = trackName,
                    onValueChange = onNameChange,
                    label = { Text("Track name") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.weight(1f))
                LapButton(
                    text = "Save Track",
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                )
                LapButton(
                    text = "Discard",
                    onClick = onDiscardRequest,
                    style = LapButtonStyle.GhostDestructive,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = FAILED_CAPTURE_STATUS,
                    color = LapSightTheme.colors.statusCaution,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.weight(1f))
                LapButton(
                    text = "Re-record",
                    onClick = onReRecordRequest,
                    modifier = Modifier.fillMaxWidth(),
                )
                LapButton(
                    text = "Discard",
                    onClick = onDiscardRequest,
                    style = LapButtonStyle.GhostDestructive,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TrackReviewSourceChip(review: TrackReviewState) {
    if (review.extraction.markingSession.source.isSimulated) {
        StatusChip(text = "DEMO — simulated GPS", tone = ChipTone.Demo)
    } else {
        StatusChip(text = "PHONE GPS — live", tone = ChipTone.Ready)
    }
}

/** The captured marking trace + extracted reference line + start/finish. */
@Composable
private fun CapturedCourseMap(
    review: TrackReviewState,
    modifier: Modifier = Modifier,
    fillParent: Boolean = false,
) {
    val layers = remember(review) {
        buildTrackTraceLayers(
            markingSamples = review.extraction.markingSession.samples,
            referenceLine = review.extraction.referenceLine,
            startFinish = review.startFinish,
            sectors = emptyList(),
            outlierSamples = emptyList(),
            viewWidth = 400.0,
            viewHeight = 300.0,
        )
    }
    if (layers.isNotEmpty()) {
        TraceView(
            layers = layers,
            modifier = modifier,
            minHeight = 200.dp,
            maxHeight = 260.dp,
            fillParent = fillParent,
        )
    }
}
