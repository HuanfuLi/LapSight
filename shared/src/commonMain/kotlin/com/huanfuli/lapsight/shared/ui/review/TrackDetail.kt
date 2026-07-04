// Directory: ui/review — package stays `shared.ui` (see ReviewScreen.kt note).
package com.huanfuli.lapsight.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import com.huanfuli.lapsight.shared.export.ExportArtifact
import com.huanfuli.lapsight.shared.export.ExportFailedException
import com.huanfuli.lapsight.shared.export.ExportFileNames
import com.huanfuli.lapsight.shared.export.ExportNotFoundException
import com.huanfuli.lapsight.shared.export.ExportShareResult
import com.huanfuli.lapsight.shared.export.ExportShareTarget
import com.huanfuli.lapsight.shared.export.JsonExportService
import com.huanfuli.lapsight.shared.nowEpochMillis
import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.storage.LoadResult
import com.huanfuli.lapsight.shared.storage.LocalSessionStore
import com.huanfuli.lapsight.shared.track.AppendRevisionResult
import com.huanfuli.lapsight.shared.track.ArchiveProfileResult
import com.huanfuli.lapsight.shared.track.ClosedReferencePath
import com.huanfuli.lapsight.shared.track.ClosedReferencePathResult
import com.huanfuli.lapsight.shared.track.CourseGeometryBuilder
import com.huanfuli.lapsight.shared.track.CourseProfileEditor
import com.huanfuli.lapsight.shared.track.CourseSetup
import com.huanfuli.lapsight.shared.track.CourseValidation
import com.huanfuli.lapsight.shared.track.CreateProfileResult
import com.huanfuli.lapsight.shared.track.CurrentProfileResolution
import com.huanfuli.lapsight.shared.track.CurrentTrackSelection
import com.huanfuli.lapsight.shared.track.DuplicateProfileResult
import com.huanfuli.lapsight.shared.track.RenameProfileResult
import com.huanfuli.lapsight.shared.track.ReviewEntryType
import com.huanfuli.lapsight.shared.track.SectorBoundary
import com.huanfuli.lapsight.shared.track.SectorLineDto
import com.huanfuli.lapsight.shared.track.Track
import com.huanfuli.lapsight.shared.track.TrackMarkingPayloadV1
import com.huanfuli.lapsight.shared.track.TrackPayloadV1
import com.huanfuli.lapsight.shared.track.TrackProfile
import com.huanfuli.lapsight.shared.track.TrackProfileController
import com.huanfuli.lapsight.shared.ui.components.ChipTone
import com.huanfuli.lapsight.shared.ui.components.LapButton
import com.huanfuli.lapsight.shared.ui.components.LapButtonStyle
import com.huanfuli.lapsight.shared.ui.components.LapSwitchRow
import com.huanfuli.lapsight.shared.ui.components.MetricCell
import com.huanfuli.lapsight.shared.ui.components.MetricCellSize
import com.huanfuli.lapsight.shared.ui.components.SectionHeader
import com.huanfuli.lapsight.shared.ui.components.StatusMessage

/** Detail body for Track / Raw-capture rows. */
@Composable
internal fun RowDetail(
    row: ReviewRowViewModel,
    sessionStore: LocalSessionStore,
    exportShareTarget: ExportShareTarget,
    refreshVersion: Long,
    onDataChanged: () -> Unit,
) {
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val spacing = LapSightTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        MetricCell(label = "ID", value = row.id, size = MetricCellSize.Row)
        MetricCell(label = "Type", value = row.typeLabel, size = MetricCellSize.Row)
        MetricCell(label = "Source", value = row.sourceLabel, size = MetricCellSize.Row)
        if (row.sampleCount != null) {
            MetricCell(label = "Samples", value = row.sampleCount.toString(), size = MetricCellSize.Row)
        }
        MetricCell(label = "Payload", value = row.payloadPath, size = MetricCellSize.Row)

        // Track detail uses a single beautified course map. Edit mode switches this
        // map in place instead of rendering a second identical editor map.
        if (row.type == ReviewEntryType.Track) {
            TrackCourseDetailSection(
                trackId = row.id,
                sessionStore = sessionStore,
                refreshVersion = refreshVersion,
                onDataChanged = onDataChanged,
            )
        } else if (row.type == ReviewEntryType.TrackMarking) {
            TrackTraceSection(
                rowId = row.id,
                type = row.type,
                sessionStore = sessionStore,
                refreshVersion = refreshVersion,
            )
        }

        // DEMO provenance stays visible in the expanded detail too (T-03-10).
        if (row.isDemo) {
            StatusMessage(text = "Simulated data — not live history.", tone = ChipTone.Demo)
        }

        // Set as current track (D-02): make this Track the explicit current selection
        // from detail, WITHOUT starting a session. Older V1-only Tracks are promoted to
        // a V2 profile on demand so selection always resolves a real aggregate.
        if (row.type == ReviewEntryType.Track) {
            Spacer(Modifier.height(spacing.xs))
            var selectMessage by remember(row.id) { mutableStateOf<String?>(null) }
            LapButton(
                text = "Set as current track",
                style = LapButtonStyle.Secondary,
                onClick = { selectMessage = setAsCurrentTrack(sessionStore, row.id) },
            )
            selectMessage?.let { msg ->
                StatusMessage(
                    text = msg,
                    tone = if (msg.startsWith("Couldn't")) ChipTone.Error else ChipTone.Ready,
                )
            }
        }

        // Profile lifecycle (SC-01, D-12/D-16, D-01/D-03): rename, archive, and duplicate
        // a saved Track profile without deleting or rebinding history.
        if (row.type == ReviewEntryType.Track) {
            ProfileLifecycleSection(
                trackId = row.id,
                sessionStore = sessionStore,
                onDataChanged = onDataChanged,
            )
        }

        // Export actions (D-40): explicit button tap on Track Review detail only.
        if (row.type == ReviewEntryType.Track) {
            Spacer(Modifier.height(spacing.xs))
            LapButton(
                text = "Export JSON",
                style = LapButtonStyle.Secondary,
                onClick = {
                    exportMessage = try {
                        val bytes = JsonExportService(sessionStore).exportTrack(row.id)
                        val name = ExportFileNames.forTrack(row.name, row.createdAtEpochMillis)
                        val artifact = ExportArtifact(name, "application/json", bytes)
                        when (exportShareTarget.share(artifact)) {
                            is ExportShareResult.Shared, is ExportShareResult.Saved ->
                                "Exported $name"
                            is ExportShareResult.Cancelled -> null
                            is ExportShareResult.Failed -> "Export failed. Check device storage and try again."
                        }
                    } catch (e: ExportNotFoundException) {
                        "Export failed. Check device storage and try again."
                    } catch (e: ExportFailedException) {
                        "Export failed. Check device storage and try again."
                    }
                },
            )
            exportMessage?.let { msg ->
                StatusMessage(
                    text = msg,
                    tone = if (msg.startsWith("Exported")) ChipTone.Ready else ChipTone.Error,
                )
            }
        }
    }
}

/**
 * Single Track course surface for Review detail.
 *
 * This replaces the previous split between a read-only `TraceView` and a second
 * `TrackEditorScreen`. The same beautified map is used for browse and edit mode.
 */
@Composable
private fun TrackCourseDetailSection(
    trackId: String,
    sessionStore: LocalSessionStore,
    refreshVersion: Long,
    onDataChanged: () -> Unit,
) {
    val payload = remember(trackId, refreshVersion) {
        (sessionStore.loadTrack(trackId) as? LoadResult.Loaded<TrackPayloadV1>)?.value
    }
    var profile by remember(trackId, refreshVersion) {
        mutableStateOf(ensureProfile(sessionStore, trackId))
    }
    var editing by remember(trackId) { mutableStateOf(false) }
    var message by remember(trackId) { mutableStateOf<String?>(null) }

    val current = profile
    val latest = current?.latestRevision
    val referenceLine = latest?.referenceLine ?: payload?.track?.referenceLine
    val initialSetup = latest?.courseSetup ?: legacyCourseSetup(payload?.track)

    val spacing = LapSightTheme.spacing
    Spacer(Modifier.height(spacing.sm))
    SectionHeader(text = "Trace")
    Spacer(Modifier.height(spacing.xs))

    val pathResult = remember(referenceLine) {
        referenceLine?.let { ClosedReferencePath.fromReferenceLine(it) }
    }
    if (referenceLine == null || referenceLine.points.isEmpty() || pathResult !is ClosedReferencePathResult.Loaded) {
        Text(
            text = "Trace data unavailable.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        val path = pathResult.path
        var editor by remember(trackId, refreshVersion, editing, path) {
            mutableStateOf(seedCourseProfileEditor(path, initialSetup))
        }
        TrackCourseMapCanvas(
            referenceLine = referenceLine,
            editor = editor,
            editingEnabled = editing,
            height = 260.dp,
            onPlaceStartFinish = { local -> editor = editor.placeStartFinish(local) },
            onDragStartFinishBy = { deltaMeters -> editor = editor.dragStartFinishBy(deltaMeters) },
            onDragBoundaryBy = { id, deltaMeters -> editor = editor.dragBoundaryBy(id, deltaMeters) },
        )

        if (current == null) {
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = "Course editing unavailable for this entry.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            CourseRevisionAndEditControls(
                profile = current,
                editing = editing,
                editor = editor,
                message = message,
                onStartEditing = {
                    editing = true
                    message = null
                },
                onEditorChanged = { editor = it },
                onCancelEditing = {
                    editing = false
                    message = null
                },
                onSave = {
                    val controller = TrackProfileController(sessionStore)
                    when (val result = controller.appendRevision(
                        profileId = current.profileId,
                        referenceLine = referenceLine,
                        courseSetup = editor.toCourseSetup(),
                        app = trackApp(sessionStore, trackId) ?: uiFallbackAppMetadata(),
                        sourceMarkingSessionId = latest?.sourceMarkingSessionId,
                    )) {
                        is AppendRevisionResult.Appended -> {
                            profile = result.profile
                            editing = false
                            message = "Saved revision ${result.revision.ordinal}."
                            onDataChanged()
                        }
                        is AppendRevisionResult.Rejected -> {
                            message = "Couldn't save: ${result.reason}."
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun CourseRevisionAndEditControls(
    profile: TrackProfile,
    editing: Boolean,
    editor: CourseProfileEditor,
    message: String?,
    onStartEditing: () -> Unit,
    onEditorChanged: (CourseProfileEditor) -> Unit,
    onCancelEditing: () -> Unit,
    onSave: () -> Unit,
) {
    val spacing = LapSightTheme.spacing
    Spacer(Modifier.height(spacing.sm))
    SectionHeader(text = "Revision history")
    profile.revisions.sortedBy { it.ordinal }.forEach { revision ->
        val ready = revision.courseSetup.startFinish != null
        Text(
            text = "Rev ${revision.ordinal} · ${formatEpochMillis(revision.createdAtEpochMillis)} · " +
                if (ready) "timing-ready" else "needs start/finish",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    message?.let { msg ->
        StatusMessage(
            text = msg,
            tone = if (msg.startsWith("Couldn't") || msg.startsWith("Edit")) {
                ChipTone.Error
            } else {
                ChipTone.Ready
            },
        )
    }

    if (!editing) {
        Spacer(Modifier.height(spacing.xs))
        LapButton(
            text = "Edit course",
            style = LapButtonStyle.Secondary,
            onClick = onStartEditing,
        )
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        val placed = editor.startFinishProgress != null
        LapButton(
            text = if (editor.startFinishConfirmed) "Start/finish confirmed" else "Confirm start/finish",
            style = LapButtonStyle.Secondary,
            enabled = placed && !editor.startFinishConfirmed,
            onClick = { if (editor.startFinishProgress != null) onEditorChanged(editor.confirmStartFinish()) },
        )
    }
    if (editor.startFinishProgress == null) {
        Text(
            text = "Tap the trace to place the start/finish line.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    LapSwitchRow(
        label = "Sector timing",
        checked = editor.sectorsEnabled,
        onCheckedChange = { enabled -> onEditorChanged(editor.setSectorsEnabled(enabled)) },
    )
    if (editor.sectorsEnabled) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            LapButton(
                text = "-",
                style = LapButtonStyle.Secondary,
                enabled = editor.sectorCount > CourseGeometryBuilder.MIN_SECTOR_COUNT,
                onClick = { onEditorChanged(editor.setSectorCount(editor.sectorCount - 1)) },
            )
            Text(
                text = "${editor.sectorCount} Sectors",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
            LapButton(
                text = "+",
                style = LapButtonStyle.Secondary,
                enabled = editor.sectorCount < CourseGeometryBuilder.MAX_SECTOR_COUNT,
                onClick = { onEditorChanged(editor.setSectorCount(editor.sectorCount + 1)) },
            )
        }
        Text(
            text = editor.boundaries.joinToString(
                prefix = "Boundaries: ",
                separator = "  ",
            ) { "S${it.order}" },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    when (val validation = editor.validate()) {
        is CourseValidation.Valid -> StatusMessage(text = "Ready to save.", tone = ChipTone.Ready)
        is CourseValidation.Invalid -> Text(
            text = validation.problems.joinToString("\n") { describeCourseProblem(it) },
            color = LapSightTheme.colors.statusCaution,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Spacer(Modifier.height(spacing.xs))
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        LapButton(
            text = "Save revision",
            enabled = editor.canSave,
            onClick = { if (editor.canSave) onSave() },
        )
        LapButton(
            text = "Cancel",
            style = LapButtonStyle.Ghost,
            onClick = onCancelEditing,
        )
    }
}

private fun legacyCourseSetup(track: Track?): CourseSetup? {
    if (track == null) return null
    val boundaries = track.sectors.map { sector ->
        SectorBoundary(
            id = sector.id,
            order = sector.order,
            pointA = sector.pointA,
            pointB = sector.pointB,
            normalizedProgress = null,
        )
    }
    return CourseSetup(
        startFinish = track.startFinish,
        sectorsEnabled = boundaries.isNotEmpty(),
        sectorCount = if (boundaries.isNotEmpty()) boundaries.size + 1 else 0,
        boundaries = boundaries,
    )
}

/**
 * Renders the offline vector trace for a Track or TrackMarking entry (D-35).
 * Loads the canonical payload (and marking session if available) from the store,
 * builds trace layers, and renders them via [TraceView].
 */
@Composable
internal fun TrackTraceSection(
    rowId: String,
    type: ReviewEntryType,
    sessionStore: LocalSessionStore,
    refreshVersion: Long,
) {
    val trackResult = remember(rowId, refreshVersion) { sessionStore.loadTrack(rowId) }
    val track = (trackResult as? LoadResult.Loaded<TrackPayloadV1>)?.value?.track

    val profileResult = remember(rowId, refreshVersion) { sessionStore.loadProfile(rowId) }
    val profile = (profileResult as? LoadResult.Loaded<TrackProfile>)?.value

    val markingId = track?.sourceMarkingSessionId ?: profile?.latestRevision?.sourceMarkingSessionId
    val markingResult = remember(markingId, refreshVersion) {
        markingId?.let { sessionStore.loadTrackMarking(it) }
    }
    val marking = (markingResult as? LoadResult.Loaded<TrackMarkingPayloadV1>)?.value?.marking

    val samples = when {
        marking != null -> marking.samples
        type == ReviewEntryType.TrackMarking -> {
            // Fallback: load marking directly by row id.
            val directMarking = (sessionStore.loadTrackMarking(rowId) as? LoadResult.Loaded<TrackMarkingPayloadV1>)?.value?.marking
            directMarking?.samples ?: emptyList()
        }
        else -> emptyList()
    }

    val referenceLine = track?.referenceLine ?: profile?.latestRevision?.referenceLine

    if (samples.isEmpty() && referenceLine?.points.isNullOrEmpty()) {
        Text(
            text = "Trace data unavailable.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }

    val startFinish = track?.startFinish ?: profile?.latestRevision?.courseSetup?.startFinish
    val sectors = track?.sectors ?: profile?.latestRevision?.courseSetup?.boundaries?.map {
        SectorLineDto(id = it.id, name = "Sector ${it.order}", order = it.order, pointA = it.pointA, pointB = it.pointB)
    } ?: emptyList()

    val layers = com.huanfuli.lapsight.shared.review.buildTrackTraceLayers(
        markingSamples = samples,
        referenceLine = referenceLine,
        startFinish = startFinish,
        sectors = sectors,
        outlierSamples = emptyList(),
        viewWidth = 400.0,
        viewHeight = 300.0,
    )

    if (layers.isNotEmpty()) {
        Spacer(Modifier.height(LapSightTheme.spacing.sm))
        SectionHeader(text = "Trace")
        Spacer(Modifier.height(LapSightTheme.spacing.xs))
        TraceView(layers = layers, minHeight = 180.dp, maxHeight = 260.dp)
    }
}

/**
 * Profile lifecycle controls on Track detail (SC-01; D-12 rename, D-16 archive/duplicate;
 * D-01/D-03 selection clearing).
 *
 * Rename updates the profile's display name without touching its immutable revisions;
 * Archive removes the profile from active selectors while retaining every revision/session/
 * Ghost and — when it was the current Track — clears the selection so Drive returns to the
 * explicit no-selection state rather than auto-picking another Track; Duplicate forks an
 * independent profile with fresh identities. None of these deletes data. A V1-only Track is
 * promoted to a V2 profile on demand so a real aggregate always backs the action.
 */
@Composable
private fun ProfileLifecycleSection(
    trackId: String,
    sessionStore: LocalSessionStore,
    onDataChanged: () -> Unit,
) {
    val spacing = LapSightTheme.spacing
    Spacer(Modifier.height(spacing.sm))
    SectionHeader(text = "Profile")

    var message by remember(trackId) { mutableStateOf<String?>(null) }
    var renameValue by remember(trackId) { mutableStateOf("") }

    // Rename: a non-geometric metadata change (D-12). Blank/unsafe names are rejected by
    // the controller and never form a storage path.
    Spacer(Modifier.height(spacing.xs))
    OutlinedTextField(
        value = renameValue,
        onValueChange = { renameValue = it },
        label = { Text("Rename profile") },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(spacing.xs))
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        LapButton(
            text = "Rename",
            style = LapButtonStyle.Secondary,
            onClick = {
                val msg = renameTrack(sessionStore, trackId, renameValue)
                message = msg
                if (!msg.startsWith("Couldn't")) onDataChanged()
            },
        )
        LapButton(
            text = "Duplicate",
            style = LapButtonStyle.Secondary,
            onClick = {
                val msg = duplicateTrack(sessionStore, trackId)
                message = msg
                if (!msg.startsWith("Couldn't")) onDataChanged()
            },
        )
        LapButton(
            text = "Archive",
            style = LapButtonStyle.Secondary,
            onClick = {
                val msg = archiveTrack(sessionStore, trackId)
                message = msg
                if (!msg.startsWith("Couldn't")) onDataChanged()
            },
        )
    }
    message?.let { msg ->
        StatusMessage(
            text = msg,
            tone = if (msg.startsWith("Couldn't")) ChipTone.Error else ChipTone.Ready,
        )
    }
}

/**
 * Resolves the V2 [TrackProfile] for [trackId], promoting a V1-only Track to a V2
 * profile if no aggregate exists yet. Returns null when neither a profile nor a
 * loadable Track payload is available.
 */
private fun ensureProfile(store: LocalSessionStore, trackId: String): TrackProfile? {
    (store.loadProfile(trackId) as? LoadResult.Loaded<TrackProfile>)?.let { return it.value }
    val payload = (store.loadTrack(trackId) as? LoadResult.Loaded<TrackPayloadV1>)?.value ?: return null
    val controller = TrackProfileController(store)
    when (controller.saveProfile(payload.track, payload.track.name, payload.app)) {
        is CreateProfileResult.Created ->
            return (store.loadProfile(trackId) as? LoadResult.Loaded<TrackProfile>)?.value
        is CreateProfileResult.Rejected -> return null
    }
}

/**
 * Makes the Track identified by [trackId] the explicit current selection (D-02),
 * promoting a V1-only Track to a V2 profile first if needed. Returns a short status
 * message for the detail surface. Never starts a Timing session and never derives a
 * different Track (D-03/D-04).
 */
private fun setAsCurrentTrack(store: LocalSessionStore, trackId: String): String {
    val controller = TrackProfileController(store)
    // Ensure a V2 profile (profileId == trackId) exists before selecting it.
    if (store.loadProfile(trackId) !is LoadResult.Loaded) {
        val payload = (store.loadTrack(trackId) as? LoadResult.Loaded<TrackPayloadV1>)?.value
            ?: return "Couldn't load this track."
        val created = controller.saveProfile(
            track = payload.track,
            name = payload.track.name,
            app = payload.app,
        )
        if (created is CreateProfileResult.Rejected) {
            return "Couldn't set as current: ${created.reason}."
        }
    }
    store.setCurrentSelection(CurrentTrackSelection(profileId = trackId))
    return when (controller.resolveCurrent()) {
        is CurrentProfileResolution.Selected -> "Set as current track."
        CurrentProfileResolution.NotTimingReady ->
            "Set as current. Add a start/finish before timing."
        else -> "Set as current track."
    }
}

/**
 * Renames the profile backing [trackId] (D-12), promoting a V1-only Track first if needed.
 * Returns a short status message. The revision history is preserved unchanged.
 */
internal fun renameTrack(store: LocalSessionStore, trackId: String, newName: String): String {
    val profile = ensureProfile(store, trackId) ?: return "Couldn't load this track."
    val app = trackApp(store, trackId) ?: return "Couldn't load this track."
    return when (val result = TrackProfileController(store).renameProfile(profile.profileId, newName, app)) {
        is RenameProfileResult.Renamed -> "Renamed to \"${result.profile.name}\"."
        is RenameProfileResult.Rejected -> "Couldn't rename: ${result.reason}."
    }
}

/**
 * Archives the profile backing [trackId] (D-16) and, when it was the current Track, clears
 * the selection so Drive returns to the explicit no-selection state (D-01/D-03). Never
 * deletes any revision/session/Ghost and never selects a replacement Track.
 */
internal fun archiveTrack(
    store: LocalSessionStore,
    trackId: String,
    now: () -> Long = ::nowEpochMillisSafeUi,
): String {
    val profile = ensureProfile(store, trackId) ?: return "Couldn't load this track."
    val app = trackApp(store, trackId) ?: return "Couldn't load this track."
    return when (val result = TrackProfileController(store).archiveProfile(profile.profileId, app, now)) {
        is ArchiveProfileResult.Archived ->
            if (result.clearedCurrentSelection) {
                "Archived. Current track cleared — pick a track before timing."
            } else {
                "Archived. It stays in history but leaves track selection."
            }
        is ArchiveProfileResult.Rejected -> "Couldn't archive: ${result.reason}."
    }
}

/**
 * Duplicates the profile backing [trackId] into an independent profile with fresh identities
 * (D-16). Returns a short status message. The source profile is left unchanged.
 */
internal fun duplicateTrack(
    store: LocalSessionStore,
    trackId: String,
    now: () -> Long = ::nowEpochMillisSafeUi,
): String {
    val profile = ensureProfile(store, trackId) ?: return "Couldn't load this track."
    val app = trackApp(store, trackId) ?: return "Couldn't load this track."
    val newProfileId = "${profile.profileId}-copy-${now()}"
    val newName = "${profile.name} copy"
    return when (
        val result = TrackProfileController(store)
            .duplicateProfile(profile.profileId, newName, newProfileId, app, now)
    ) {
        is DuplicateProfileResult.Duplicated -> "Duplicated as \"${result.duplicate.name}\"."
        is DuplicateProfileResult.Rejected -> "Couldn't duplicate: ${result.reason}."
    }
}

/** The app metadata stamped on the V1 Track payload backing [trackId], or null if absent. */
private fun trackApp(store: LocalSessionStore, trackId: String): AppMetadata? =
    (store.loadTrack(trackId) as? LoadResult.Loaded<TrackPayloadV1>)?.value?.app
        ?: (store.loadProfile(trackId) as? LoadResult.Loaded<TrackProfile>)?.let {
            uiFallbackAppMetadata()
        }

internal fun uiFallbackAppMetadata(): AppMetadata = AppMetadata(
    appVersion = "0.5.0",
    platform = "shared-ui",
)

/** Wall-clock epoch millis that never throws (mirrors the controller guard). */
private fun nowEpochMillisSafeUi(): Long = try {
    nowEpochMillis()
} catch (_: Throwable) {
    0L
}
