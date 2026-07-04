package com.huanfuli.lapsight.shared.ui.drive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import com.huanfuli.lapsight.shared.ui.DriveMarkingController
import com.huanfuli.lapsight.shared.ui.DriveMarkingSnapshot
import com.huanfuli.lapsight.shared.ui.LapSightTheme
import com.huanfuli.lapsight.shared.ui.components.ChipTone
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
        if (review.canSave) {
            Text(
                text = "Track ready. Set start/finish, then save.",
                color = LapSightTheme.colors.statusReady,
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            Text(
                text = "Couldn't build a clean track. Re-record 3 continuous closed-course loops and avoid long stops.",
                color = LapSightTheme.colors.statusCaution,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
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
        // Start/finish editing state (D-11, D-19). A confirmed start/finish
        // is required before formal timing (Plan 03-06).
        val startFinishSet = review.startFinish != null
        Text(
            text = if (startFinishSet) {
                "Start/finish: set"
            } else {
                "Start/finish: not set — required before timing"
            },
            color = if (startFinishSet) {
                LapSightTheme.colors.statusReady
            } else {
                LapSightTheme.colors.statusCaution
            },
            style = MaterialTheme.typography.labelLarge,
        )
        // Marking is continuous capture, NOT lap timing — no lap times shown (D-08).
        Text(
            text = "Marking capture does not produce lap times.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
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
        // Save Track — primary CTA; accent-styled, enabled only when ready.
        LapButton(
            text = "Save Track",
            onClick = {
                controller.setTrackName(trackName)
                controller.saveTrack()
                onChanged()
                onSavedTrack()
            },
            enabled = review.canSave,
            modifier = Modifier.fillMaxWidth(),
        )
        // Confirm start/finish from the reference line (convenience; D-11).
        LapButton(
            text = "Set start/finish from reference",
            onClick = {
                controller.confirmStartFinish()
                onChanged()
            },
            style = LapButtonStyle.Secondary,
            enabled = review.canSave && review.startFinish == null,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            LapButton(
                text = "Re-record",
                onClick = { confirmReRecord = true },
                style = LapButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
            )
            LapButton(
                text = "Discard",
                onClick = { confirmDiscard = true },
                style = LapButtonStyle.GhostDestructive,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
