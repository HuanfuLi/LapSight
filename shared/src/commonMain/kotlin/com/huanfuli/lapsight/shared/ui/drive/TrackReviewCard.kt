package com.huanfuli.lapsight.shared.ui.drive

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
 * path instead of dominant disabled buttons. Raw loop/sample QA numbers live
 * behind a Capture-details disclosure. Track Review never shows lap times for
 * marking samples (D-08); Re-record and Discard require explicit confirmation.
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
    val spacing = LapSightTheme.spacing

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

    LapCard {
        Text(
            text = "Track Review",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
        )
        if (review.extraction.markingSession.source.isSimulated) {
            StatusChip(text = "DEMO — simulated GPS", tone = ChipTone.Demo)
        } else {
            StatusChip(text = "PHONE GPS — live", tone = ChipTone.Ready)
        }

        // The captured course, drawn from the same layers Review uses. Shown for
        // failed captures too — seeing the broken trace explains the failure.
        CapturedCourseMap(review = review)

        if (review.canSave) {
            Text(
                text = "${review.extraction.acceptedLoopCount} clean loops captured. " +
                    if (review.startFinish != null) {
                        "Start/finish is set."
                    } else {
                        "Saving places the start/finish at the recorded start — adjustable later in the course editor."
                    },
                color = LapSightTheme.colors.statusReady,
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(spacing.xs))
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
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            // Save Track — the one primary action on a clean capture.
            LapButton(
                text = "Save Track",
                onClick = {
                    controller.setTrackName(trackName)
                    controller.saveTrack()
                    onChanged()
                    onSavedTrack()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            LapButton(
                text = "Discard",
                onClick = { confirmDiscard = true },
                style = LapButtonStyle.GhostDestructive,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = "Couldn't build a clean track. Re-record 3 continuous closed-course loops and avoid long stops.",
                color = LapSightTheme.colors.statusCaution,
                style = MaterialTheme.typography.bodyLarge,
            )
            // Failed capture: recovery leads (Re-record), no disabled Save in sight.
            LapButton(
                text = "Re-record",
                onClick = { confirmReRecord = true },
                modifier = Modifier.fillMaxWidth(),
            )
            LapButton(
                text = "Discard",
                onClick = { confirmDiscard = true },
                style = LapButtonStyle.GhostDestructive,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Marking is continuous capture, NOT lap timing — no lap times shown (D-08).
        Text(
            text = "Marking capture does not produce lap times.",
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

/** The captured marking trace + extracted reference line + start/finish. */
@Composable
private fun CapturedCourseMap(review: com.huanfuli.lapsight.shared.track.TrackReviewState) {
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
        TraceView(layers = layers, minHeight = 200.dp, maxHeight = 260.dp)
    }
}
