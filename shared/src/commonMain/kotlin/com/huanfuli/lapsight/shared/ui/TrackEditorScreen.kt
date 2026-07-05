package com.huanfuli.lapsight.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huanfuli.lapsight.shared.lap.LocalPoint
import com.huanfuli.lapsight.shared.review.TracePoint
import com.huanfuli.lapsight.shared.review.TraceViewport
import com.huanfuli.lapsight.shared.session.GeoPointDto
import com.huanfuli.lapsight.shared.track.ClosedReferencePath
import com.huanfuli.lapsight.shared.track.CourseGeometryBuilder
import com.huanfuli.lapsight.shared.track.CourseProblem
import com.huanfuli.lapsight.shared.track.CourseProfileEditor
import com.huanfuli.lapsight.shared.track.CourseSetup
import com.huanfuli.lapsight.shared.track.SectorBoundary
import com.huanfuli.lapsight.shared.track.StartFinishLineDto
import com.huanfuli.lapsight.shared.track.TrackReferenceLine
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Shared course map canvas used by Track detail browse and edit modes
 * (Plan 05-06; SC-02, D-05, D-10).
 *
 * Renders the recorded reference loop with no map tiles and no platform
 * geometry. In edit mode it routes handle drags into relative course-progress
 * deltas: every pointer position is converted through the invertible
 * [TraceViewport] into local meters and forwarded to the pure
 * [CourseProfileEditor] as a *candidate*; the editor projects + snaps + clamps
 * and persists progress only — no endpoint or canvas coordinate is ever stored
 * (D-10). Edits never move recorded GPS data.
 *
 * All colors come from the theme canvas palette ([LapSightColors.trace*]) so
 * the editor map matches the Review traces in both themes.
 */
@Composable
internal fun TrackCourseMapCanvas(
    referenceLine: TrackReferenceLine,
    editor: CourseProfileEditor,
    editingEnabled: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 300.dp,
    onPlaceStartFinish: (LocalPoint) -> Unit,
    onDragStartFinishBy: (Double) -> Unit,
    onDragBoundaryBy: (String, Double) -> Unit,
) {
    val padding = 0.1
    val viewport = remember(referenceLine) {
        TraceViewport.fromLayers(listOf(referenceLine.points), 400.0, 300.0, padding)
    }
    // The canvas frame zooms to the static reference loop (never the movable
    // handles) so the map does not re-zoom while a handle is dragged.
    val loopPoints = remember(viewport) {
        viewport?.projectLayer(referenceLine.points) ?: emptyList()
    }
    val latestEditor = rememberUpdatedState(editor)
    var activeHandle by remember { mutableStateOf<String?>(null) }

    // Theme palette resolved outside the DrawScope (not composable inside).
    val mapBackground = MaterialTheme.colorScheme.background
    val haloColor = LapSightTheme.colors.dashBackground
    val loopColor = LapSightTheme.colors.traceReference
    val startFinishColor = LapSightTheme.colors.traceStartFinish
    val sectorColor = LapSightTheme.colors.traceSector
    val handleCoreColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp, max = 360.dp)
            .height(height)
            .clip(MaterialTheme.shapes.medium)
            .background(mapBackground),
    ) {
        if (viewport == null) return@Box

        // Derived geometry (canonical lat/lon), regenerated from progress each frame.
        val startFinishLine: StartFinishLineDto? = editor.buildStartFinishLine()
        val boundaries: List<SectorBoundary> = editor.buildBoundaries()

        var canvasModifier = Modifier
            .fillMaxWidth()
            .height(height)
        if (editingEnabled) {
            canvasModifier = canvasModifier
                // Single gesture model: touch creates the first start/finish only;
                // existing handles move by relative course progress, not repeated taps.
                .pointerInput(viewport) {
                    var draggingBoundaryId: String? = null
                    var draggingStartFinish = false
                    detectDragGestures(
                        onDragStart = { pos ->
                            val current = latestEditor.value
                            val frame = TraceCanvasFrame(loopPoints, size.width.toFloat(), size.height.toFloat())
                            val pick = pickHandle(
                                pos = pos,
                                viewport = viewport,
                                frame = frame,
                                startFinishLine = current.buildStartFinishLine(),
                                boundaries = current.buildBoundaries(),
                            )
                            when {
                                pick == START_FINISH_HANDLE -> {
                                    draggingStartFinish = true
                                    draggingBoundaryId = null
                                    activeHandle = START_FINISH_HANDLE
                                }
                                pick != null -> {
                                    draggingStartFinish = false
                                    draggingBoundaryId = pick
                                    activeHandle = pick
                                }
                                current.startFinishProgress == null -> {
                                    onPlaceStartFinish(
                                        viewport.screenToLocal(pos, frame),
                                    )
                                    draggingStartFinish = true
                                    draggingBoundaryId = null
                                    activeHandle = START_FINISH_HANDLE
                                }
                                else -> {
                                    draggingStartFinish = false
                                    draggingBoundaryId = null
                                    activeHandle = null
                                }
                            }
                        },
                        onDragEnd = {
                            draggingBoundaryId = null
                            draggingStartFinish = false
                            activeHandle = null
                        },
                        onDragCancel = {
                            draggingBoundaryId = null
                            draggingStartFinish = false
                            activeHandle = null
                        },
                        onDrag = { change, dragAmount ->
                            val current = latestEditor.value
                            val id = draggingBoundaryId
                            val progress = when {
                                draggingStartFinish -> current.startFinishProgress
                                id != null -> current.boundaries.firstOrNull { it.id == id }?.progress
                                else -> null
                            }
                            if (progress != null) {
                                val deltaProgress = dragDeltaAlongTrace(
                                    viewport = viewport,
                                    path = current.path,
                                    progress = progress,
                                    previous = change.previousPosition,
                                    current = change.position,
                                    frame = TraceCanvasFrame(loopPoints, size.width.toFloat(), size.height.toFloat()),
                                )
                                if (abs(deltaProgress) >= 0.001 || abs(dragAmount.x) + abs(dragAmount.y) == 0f) {
                                    when {
                                        draggingStartFinish -> onDragStartFinishBy(deltaProgress)
                                        id != null -> onDragBoundaryBy(id, deltaProgress)
                                    }
                                }
                            }
                        },
                    )
                }
        }

        Canvas(
            modifier = canvasModifier,
        ) {
            // Zoom-to-fit on the reference loop: same frame the pointer inverse uses.
            val frame = TraceCanvasFrame(loopPoints, size.width, size.height)

            // Closed reference loop (full interval coverage), accent-colored.
            drawClosedLoop(loopPoints, frame, halo = haloColor, line = loopColor)

            // Start/finish line + handle.
            startFinishLine?.let { line ->
                val a = viewport.geoToNormalized(line.pointA)
                val b = viewport.geoToNormalized(line.pointB)
                drawTraceSegment(a, b, frame, haloColor, 9f)
                drawTraceSegment(a, b, frame, startFinishColor, 5f)
                drawHandle(
                    center = midpoint(a, b),
                    frame = frame,
                    color = startFinishColor,
                    haloColor = haloColor,
                    coreColor = handleCoreColor,
                    active = editingEnabled && activeHandle == START_FINISH_HANDLE,
                )
            }

            // Sector boundaries + handles.
            for (boundary in boundaries) {
                val a = viewport.geoToNormalized(boundary.pointA)
                val b = viewport.geoToNormalized(boundary.pointB)
                drawTraceSegment(a, b, frame, haloColor, 7f)
                drawTraceSegment(a, b, frame, sectorColor, 3.5f)
                drawHandle(
                    center = midpoint(a, b),
                    frame = frame,
                    color = sectorColor,
                    haloColor = haloColor,
                    coreColor = handleCoreColor,
                    active = editingEnabled && activeHandle == boundary.id,
                )
            }
        }
    }
}

// --- pure helpers (no Compose state) -----------------------------------------

private const val START_FINISH_HANDLE = "@start-finish"
private const val HANDLE_HIT_RADIUS_PX = 48f

/** Convert a screen-pixel position to local meters via the invertible viewport. */
private fun TraceViewport.screenToLocal(pos: Offset, frame: TraceCanvasFrame): LocalPoint =
    normalizedToLocal(frame.toNormalizedX(pos.x), frame.toNormalizedY(pos.y))

private fun dragDeltaAlongTrace(
    viewport: TraceViewport,
    path: ClosedReferencePath,
    progress: Double,
    previous: Offset,
    current: Offset,
    frame: TraceCanvasFrame,
): Double {
    val prevLocal = viewport.screenToLocal(previous, frame)
    val currentLocal = viewport.screenToLocal(current, frame)
    val tangent = path.tangentAt(progress)
    return (currentLocal.x - prevLocal.x) * tangent.x + (currentLocal.y - prevLocal.y) * tangent.y
}

/**
 * Pick the nearest editable handle to [pos] within [HANDLE_HIT_RADIUS_PX], returning
 * [START_FINISH_HANDLE], a boundary id, or null. Start/finish wins ties so it stays
 * draggable even when a boundary is generated nearby.
 */
private fun pickHandle(
    pos: Offset,
    viewport: TraceViewport,
    frame: TraceCanvasFrame,
    startFinishLine: StartFinishLineDto?,
    boundaries: List<SectorBoundary>,
): String? {
    var best: String? = null
    var bestDist = HANDLE_HIT_RADIUS_PX.toDouble()

    startFinishLine?.let { line ->
        val mid = midpoint(viewport.geoToNormalized(line.pointA), viewport.geoToNormalized(line.pointB))
        val d = handleDistance(pos, mid, frame)
        if (d <= bestDist) {
            bestDist = d
            best = START_FINISH_HANDLE
        }
    }
    for (boundary in boundaries) {
        val mid = midpoint(viewport.geoToNormalized(boundary.pointA), viewport.geoToNormalized(boundary.pointB))
        val d = handleDistance(pos, mid, frame)
        if (d < bestDist) {
            bestDist = d
            best = boundary.id
        }
    }
    return best
}

private fun handleDistance(pos: Offset, normalized: TracePoint, frame: TraceCanvasFrame): Double {
    val center = frame.toCanvas(normalized)
    return hypot((pos.x - center.x).toDouble(), (pos.y - center.y).toDouble())
}

private fun midpoint(a: TracePoint, b: TracePoint): TracePoint =
    TracePoint((a.x + b.x) / 2.0, (a.y + b.y) / 2.0)

private fun midpointGeo(a: GeoPointDto, b: GeoPointDto): GeoPointDto =
    GeoPointDto((a.latitude + b.latitude) / 2.0, (a.longitude + b.longitude) / 2.0)

/** Draw the closed reference loop, connecting the last vertex back to the first. */
private fun DrawScope.drawClosedLoop(
    points: List<TracePoint>,
    frame: TraceCanvasFrame,
    halo: Color,
    line: Color,
) {
    if (points.size < 2) return
    val outerStroke = 11f
    val innerStroke = 6f
    for (i in 1 until points.size) {
        drawStyledTrackSegment(points[i - 1], points[i], frame, halo, outerStroke)
    }
    drawStyledTrackSegment(points.last(), points.first(), frame, halo, outerStroke)
    for (i in 1 until points.size) {
        drawStyledTrackSegment(points[i - 1], points[i], frame, line, innerStroke)
    }
    drawStyledTrackSegment(points.last(), points.first(), frame, line, innerStroke)
}

private fun DrawScope.drawStyledTrackSegment(
    a: TracePoint,
    b: TracePoint,
    frame: TraceCanvasFrame,
    color: Color,
    strokeWidth: Float,
) {
    drawLine(
        color = color,
        start = frame.toCanvas(a),
        end = frame.toCanvas(b),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
}

/** Draw a single finite boundary segment between two normalized endpoints. */
private fun DrawScope.drawTraceSegment(
    a: TracePoint,
    b: TracePoint,
    frame: TraceCanvasFrame,
    color: Color,
    strokeWidth: Float,
) {
    drawLine(
        color = color,
        start = frame.toCanvas(a),
        end = frame.toCanvas(b),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
}

/** Draw a draggable circular handle at a normalized midpoint. */
private fun DrawScope.drawHandle(
    center: TracePoint,
    frame: TraceCanvasFrame,
    color: Color,
    haloColor: Color,
    coreColor: Color,
    active: Boolean,
) {
    val radius = if (active) 17f else 13f
    val innerRadius = if (active) 9f else 7f
    val offset = frame.toCanvas(center)
    drawCircle(
        color = haloColor,
        radius = radius + 4f,
        center = offset,
    )
    drawCircle(
        color = color,
        radius = radius,
        center = offset,
    )
    drawCircle(
        color = coreColor,
        radius = innerRadius,
        center = offset,
    )
}

internal fun describeCourseProblem(problem: CourseProblem): String = when (problem) {
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
internal fun seedCourseProfileEditor(path: ClosedReferencePath, setup: CourseSetup?): CourseProfileEditor {
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
