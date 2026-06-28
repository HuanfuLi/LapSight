package com.huanfuli.lapsight.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanfuli.lapsight.shared.lap.LocalPoint
import com.huanfuli.lapsight.shared.review.TracePoint
import com.huanfuli.lapsight.shared.review.TraceViewport
import com.huanfuli.lapsight.shared.session.GeoPointDto
import com.huanfuli.lapsight.shared.track.ClosedReferencePath
import com.huanfuli.lapsight.shared.track.ClosedReferencePathResult
import com.huanfuli.lapsight.shared.track.CourseGeometryBuilder
import com.huanfuli.lapsight.shared.track.CourseProblem
import com.huanfuli.lapsight.shared.track.CourseProfileEditor
import com.huanfuli.lapsight.shared.track.CourseSetup
import com.huanfuli.lapsight.shared.track.CourseValidation
import com.huanfuli.lapsight.shared.track.SectorBoundary
import com.huanfuli.lapsight.shared.track.StartFinishLineDto
import com.huanfuli.lapsight.shared.track.TrackReferenceLine
import kotlin.math.hypot

/**
 * Offline Track-editor surface (Plan 05-06 Task 1; SC-02, D-05, D-10).
 *
 * Renders the recorded reference loop with no map tiles and no platform geometry,
 * then lets the user place / confirm a start/finish boundary and drag generated
 * Sector boundaries along the trace. Every pointer position is converted through
 * the invertible [TraceViewport] into local meters and forwarded to the pure
 * [CourseProfileEditor] as a *candidate*; the editor projects + snaps + clamps and
 * persists progress only — no endpoint or canvas coordinate is ever stored (D-10).
 * Because rendering derives from canonical geographic / progress data, a rotation
 * or aspect change re-derives every handle from the same canonical state.
 *
 * This is an explicit pre-Timing / Review editing screen; it is deliberately NOT
 * mounted on the moving fullscreen timing dash, keeping editor gestures away from
 * active Timing (UI-SPEC safety). On valid [CourseProfileEditor.canSave] the screen
 * hands the derived [CourseSetup] to [onSave]; the caller appends an immutable
 * revision (Task 2).
 *
 * @param referenceLine the canonical recorded loop to edit over.
 * @param initialSetup  an existing course setup to seed (edit), or null for a fresh edit.
 * @param onSave        invoked with the validated [CourseSetup] when the user saves.
 * @param onCancel      invoked when the user dismisses the editor without saving.
 */
@Composable
fun TrackEditorScreen(
    referenceLine: TrackReferenceLine,
    initialSetup: CourseSetup?,
    onSave: (CourseSetup) -> Unit,
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val pathResult = remember(referenceLine) {
        ClosedReferencePath.fromReferenceLine(referenceLine)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Edit course",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
        )
        // Closed-course / private-track safety language stays visible on the editor.
        Text(
            text = "Closed-course use only. Edits never move recorded GPS data — you set " +
                "start/finish and Sector positions along the saved trace.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )

        if (pathResult !is ClosedReferencePathResult.Loaded) {
            Text(
                text = "This track's reference line can't be edited (it has no usable closed loop).",
                color = Color(0xFFFFD166),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onCancel) { Text("Close") }
            return@Column
        }

        val path = pathResult.path
        var editor by remember(path) { mutableStateOf(seedEditor(path, initialSetup)) }

        TrackEditorCanvas(
            referenceLine = referenceLine,
            editor = editor,
            onPlaceStartFinish = { local -> editor = editor.placeStartFinish(local) },
            onDragBoundary = { id, local -> editor = editor.dragBoundary(id, local) },
        )

        // Start/finish controls (confirmation is mandatory before save, D-05).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val placed = editor.startFinishProgress != null
            OutlinedButton(
                enabled = placed && !editor.startFinishConfirmed,
                onClick = { if (editor.startFinishProgress != null) editor = editor.confirmStartFinish() },
            ) { Text(if (editor.startFinishConfirmed) "Start/finish confirmed" else "Confirm start/finish") }
        }
        if (editor.startFinishProgress == null) {
            Text(
                text = "Tap the trace to place the start/finish line.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }

        // Sector timing toggle + count stepper (2..6, D-07).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Sector timing",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
            Switch(
                checked = editor.sectorsEnabled,
                onCheckedChange = { enabled -> editor = editor.setSectorsEnabled(enabled) },
            )
        }
        if (editor.sectorsEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = editor.sectorCount > CourseGeometryBuilder.MIN_SECTOR_COUNT,
                    onClick = { editor = editor.setSectorCount(editor.sectorCount - 1) },
                ) { Text("-") }
                Text(
                    text = "${editor.sectorCount} Sectors",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
                OutlinedButton(
                    enabled = editor.sectorCount < CourseGeometryBuilder.MAX_SECTOR_COUNT,
                    onClick = { editor = editor.setSectorCount(editor.sectorCount + 1) },
                ) { Text("+") }
            }
            Text(
                text = "Drag a Sector handle to move that boundary along the trace.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }

        // Typed validation feedback (never throws; D-05..D-10).
        when (val validation = editor.validate()) {
            is CourseValidation.Valid -> Text(
                text = "Ready to save.",
                color = Color(0xFF8CFF9B),
                fontSize = 13.sp,
            )
            is CourseValidation.Invalid -> Text(
                text = validation.problems.joinToString("\n") { describeProblem(it) },
                color = Color(0xFFFFD166),
                fontSize = 13.sp,
            )
        }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = editor.canSave,
                onClick = { if (editor.canSave) onSave(editor.toCourseSetup()) },
            ) { Text("Save revision") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

/**
 * The interactive canvas: draws the closed reference loop, the derived start/finish
 * line, and the derived Sector boundaries, and routes pointer gestures into
 * candidate local points. Handle hit-testing and the screen→local inverse both go
 * through the single [TraceViewport].
 */
@Composable
private fun TrackEditorCanvas(
    referenceLine: TrackReferenceLine,
    editor: CourseProfileEditor,
    onPlaceStartFinish: (LocalPoint) -> Unit,
    onDragBoundary: (String, LocalPoint) -> Unit,
) {
    val padding = 0.1
    val viewport = remember(referenceLine) {
        TraceViewport.fromLayers(listOf(referenceLine.points), 400.0, 300.0, padding)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp, max = 360.dp)
            .height(300.dp)
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (viewport == null) return@Box

        // Derived geometry (canonical lat/lon), regenerated from progress each frame.
        val startFinishLine: StartFinishLineDto? = editor.buildStartFinishLine()
        val boundaries: List<SectorBoundary> = editor.buildBoundaries()

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                // Tap to place / replace the start/finish candidate.
                .pointerInput(viewport, editor) {
                    detectTapGestures(onTap = { pos ->
                        val local = viewport.screenToLocal(pos, size.width.toFloat(), size.height.toFloat())
                        onPlaceStartFinish(local)
                    })
                }
                // Drag the nearest handle (start/finish or a Sector boundary).
                .pointerInput(viewport, editor) {
                    var draggingBoundaryId: String? = null
                    var draggingStartFinish = false
                    detectDragGestures(
                        onDragStart = { pos ->
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val pick = pickHandle(
                                pos = pos,
                                viewport = viewport,
                                width = w,
                                height = h,
                                startFinishLine = startFinishLine,
                                boundaries = boundaries,
                            )
                            draggingStartFinish = pick == START_FINISH_HANDLE
                            draggingBoundaryId = if (pick != null && pick != START_FINISH_HANDLE) pick else null
                        },
                        onDragEnd = {
                            draggingBoundaryId = null
                            draggingStartFinish = false
                        },
                        onDrag = { change, _ ->
                            val local = viewport.screenToLocal(
                                change.position,
                                size.width.toFloat(),
                                size.height.toFloat(),
                            )
                            val id = draggingBoundaryId
                            when {
                                draggingStartFinish -> onPlaceStartFinish(local)
                                id != null -> onDragBoundary(id, local)
                            }
                        },
                    )
                },
        ) {
            val w = size.width
            val h = size.height

            // Closed reference loop (full interval coverage), cyan per UI-SPEC.
            val loop = viewport.projectLayer(referenceLine.points)
            drawClosedLoop(loop, w, h)

            // Start/finish line (green) + handle.
            startFinishLine?.let { line ->
                val a = viewport.geoToNormalized(line.pointA)
                val b = viewport.geoToNormalized(line.pointB)
                drawTraceSegment(a, b, w, h, Color(0xFF8CFF9B), 3f)
                drawHandle(midpoint(a, b), w, h, Color(0xFF8CFF9B))
            }

            // Sector boundaries (amber) + handles.
            for (boundary in boundaries) {
                val a = viewport.geoToNormalized(boundary.pointA)
                val b = viewport.geoToNormalized(boundary.pointB)
                drawTraceSegment(a, b, w, h, Color(0xFFFFD166), 2f)
                drawHandle(midpoint(a, b), w, h, Color(0xFFFFD166))
            }
        }
    }
}

// --- pure helpers (no Compose state) -----------------------------------------

private const val START_FINISH_HANDLE = "@start-finish"
private const val HANDLE_HIT_RADIUS_PX = 48f

/** Convert a screen-pixel position to local meters via the invertible viewport. */
private fun TraceViewport.screenToLocal(pos: Offset, width: Float, height: Float): LocalPoint {
    val nx = if (width > 0f) pos.x / width else 0.0f
    val ny = if (height > 0f) pos.y / height else 0.0f
    return normalizedToLocal(nx.toDouble(), ny.toDouble())
}

/**
 * Pick the nearest editable handle to [pos] within [HANDLE_HIT_RADIUS_PX], returning
 * [START_FINISH_HANDLE], a boundary id, or null. Start/finish wins ties so it stays
 * draggable even when a boundary is generated nearby.
 */
private fun pickHandle(
    pos: Offset,
    viewport: TraceViewport,
    width: Float,
    height: Float,
    startFinishLine: StartFinishLineDto?,
    boundaries: List<SectorBoundary>,
): String? {
    var best: String? = null
    var bestDist = HANDLE_HIT_RADIUS_PX.toDouble()

    startFinishLine?.let { line ->
        val mid = midpoint(viewport.geoToNormalized(line.pointA), viewport.geoToNormalized(line.pointB))
        val d = handleDistance(pos, mid, width, height)
        if (d <= bestDist) {
            bestDist = d
            best = START_FINISH_HANDLE
        }
    }
    for (boundary in boundaries) {
        val mid = midpoint(viewport.geoToNormalized(boundary.pointA), viewport.geoToNormalized(boundary.pointB))
        val d = handleDistance(pos, mid, width, height)
        if (d < bestDist) {
            bestDist = d
            best = boundary.id
        }
    }
    return best
}

private fun handleDistance(pos: Offset, normalized: TracePoint, width: Float, height: Float): Double =
    hypot(pos.x - normalized.x * width, pos.y - normalized.y * height)

private fun midpoint(a: TracePoint, b: TracePoint): TracePoint =
    TracePoint((a.x + b.x) / 2.0, (a.y + b.y) / 2.0)

private fun midpointGeo(a: GeoPointDto, b: GeoPointDto): GeoPointDto =
    GeoPointDto((a.latitude + b.latitude) / 2.0, (a.longitude + b.longitude) / 2.0)

/** Draw the closed reference loop (cyan), connecting the last vertex back to the first. */
private fun DrawScope.drawClosedLoop(points: List<TracePoint>, width: Float, height: Float) {
    if (points.size < 2) return
    val color = Color(0xFF62E3FF)
    val stroke = 3f
    for (i in 1 until points.size) {
        drawLine(
            color = color,
            start = Offset((points[i - 1].x * width).toFloat(), (points[i - 1].y * height).toFloat()),
            end = Offset((points[i].x * width).toFloat(), (points[i].y * height).toFloat()),
            strokeWidth = stroke,
        )
    }
    // Closing segment back to the first vertex.
    drawLine(
        color = color,
        start = Offset((points.last().x * width).toFloat(), (points.last().y * height).toFloat()),
        end = Offset((points.first().x * width).toFloat(), (points.first().y * height).toFloat()),
        strokeWidth = stroke,
    )
}

/** Draw a single finite boundary segment between two normalized endpoints. */
private fun DrawScope.drawTraceSegment(
    a: TracePoint,
    b: TracePoint,
    width: Float,
    height: Float,
    color: Color,
    strokeWidth: Float,
) {
    drawLine(
        color = color,
        start = Offset((a.x * width).toFloat(), (a.y * height).toFloat()),
        end = Offset((b.x * width).toFloat(), (b.y * height).toFloat()),
        strokeWidth = strokeWidth,
    )
}

/** Draw a draggable circular handle at a normalized midpoint. */
private fun DrawScope.drawHandle(center: TracePoint, width: Float, height: Float, color: Color) {
    drawCircle(
        color = color,
        radius = 10f,
        center = Offset((center.x * width).toFloat(), (center.y * height).toFloat()),
    )
}

private fun describeProblem(problem: CourseProblem): String = when (problem) {
    CourseProblem.NoStartFinish -> "Place a start/finish line."
    CourseProblem.StartFinishUnconfirmed -> "Confirm the start/finish line."
    CourseProblem.InvalidSectorCount -> "Choose between 2 and 6 Sectors."
    CourseProblem.ImpossibleSpacing -> "The track is too short for this many Sectors."
    CourseProblem.AmbiguousBoundary -> "A Sector line crosses the track twice — move it."
    CourseProblem.BoundarySpacingTooTight -> "Two boundaries are too close together."
}

/**
 * Seed an editor from an existing [CourseSetup] (edit) or a blank one (new). When a
 * setup is present, start/finish and boundary positions are reconstructed from their
 * stored normalized progress so the canonical anchors — not persisted endpoints —
 * drive placement (D-10).
 */
private fun seedEditor(path: ClosedReferencePath, setup: CourseSetup?): CourseProfileEditor {
    var editor = CourseProfileEditor.create(path)
    if (setup == null) return editor

    val sfProgress = setup.startFinishProgress
    val sfLine = setup.startFinish
    when {
        sfProgress != null -> {
            editor = editor.placeStartFinish(path.pointAt(sfProgress * path.perimeter)).confirmStartFinish()
        }
        sfLine != null -> {
            editor = editor.placeStartFinishGeo(midpointGeo(sfLine.pointA, sfLine.pointB)).confirmStartFinish()
        }
    }

    if (setup.sectorsEnabled && setup.sectorCount >= CourseGeometryBuilder.MIN_SECTOR_COUNT) {
        editor = editor.setSectorCount(setup.sectorCount)
        for (boundary in setup.boundaries) {
            val np = boundary.normalizedProgress
            editor = if (np != null) {
                editor.dragBoundary(boundary.id, path.pointAt(np * path.perimeter))
            } else {
                editor.dragBoundaryGeo(boundary.id, midpointGeo(boundary.pointA, boundary.pointB))
            }
        }
    }
    return editor
}
