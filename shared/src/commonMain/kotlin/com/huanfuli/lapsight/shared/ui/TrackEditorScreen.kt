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
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val pick = pickHandle(
                                pos = pos,
                                viewport = viewport,
                                width = w,
                                height = h,
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
                                        viewport.screenToLocal(pos, size.width.toFloat(), size.height.toFloat()),
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
                                    width = size.width.toFloat(),
                                    height = size.height.toFloat(),
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
            val w = size.width
            val h = size.height

            // Closed reference loop (full interval coverage), accent-colored.
            val loop = viewport.projectLayer(referenceLine.points)
            drawClosedLoop(loop, w, h, halo = haloColor, line = loopColor)

            // Start/finish line + handle.
            startFinishLine?.let { line ->
                val a = viewport.geoToNormalized(line.pointA)
                val b = viewport.geoToNormalized(line.pointB)
                drawTraceSegment(a, b, w, h, haloColor, 9f)
                drawTraceSegment(a, b, w, h, startFinishColor, 5f)
                drawHandle(
                    center = midpoint(a, b),
                    width = w,
                    height = h,
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
                drawTraceSegment(a, b, w, h, haloColor, 7f)
                drawTraceSegment(a, b, w, h, sectorColor, 3.5f)
                drawHandle(
                    center = midpoint(a, b),
                    width = w,
                    height = h,
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
private fun TraceViewport.screenToLocal(pos: Offset, width: Float, height: Float): LocalPoint {
    val nx = if (width > 0f) pos.x / width else 0.0f
    val ny = if (height > 0f) pos.y / height else 0.0f
    return normalizedToLocal(nx.toDouble(), ny.toDouble())
}

private fun dragDeltaAlongTrace(
    viewport: TraceViewport,
    path: ClosedReferencePath,
    progress: Double,
    previous: Offset,
    current: Offset,
    width: Float,
    height: Float,
): Double {
    val prevLocal = viewport.screenToLocal(previous, width, height)
    val currentLocal = viewport.screenToLocal(current, width, height)
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

/** Draw the closed reference loop, connecting the last vertex back to the first. */
private fun DrawScope.drawClosedLoop(
    points: List<TracePoint>,
    width: Float,
    height: Float,
    halo: Color,
    line: Color,
) {
    if (points.size < 2) return
    val outerStroke = 11f
    val innerStroke = 6f
    for (i in 1 until points.size) {
        drawStyledTrackSegment(points[i - 1], points[i], width, height, halo, outerStroke)
    }
    drawStyledTrackSegment(points.last(), points.first(), width, height, halo, outerStroke)
    for (i in 1 until points.size) {
        drawStyledTrackSegment(points[i - 1], points[i], width, height, line, innerStroke)
    }
    drawStyledTrackSegment(points.last(), points.first(), width, height, line, innerStroke)
}

private fun DrawScope.drawStyledTrackSegment(
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
        cap = StrokeCap.Round,
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
        cap = StrokeCap.Round,
    )
}

/** Draw a draggable circular handle at a normalized midpoint. */
private fun DrawScope.drawHandle(
    center: TracePoint,
    width: Float,
    height: Float,
    color: Color,
    haloColor: Color,
    coreColor: Color,
    active: Boolean,
) {
    val radius = if (active) 17f else 13f
    val innerRadius = if (active) 9f else 7f
    val offset = Offset((center.x * width).toFloat(), (center.y * height).toFloat())
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
