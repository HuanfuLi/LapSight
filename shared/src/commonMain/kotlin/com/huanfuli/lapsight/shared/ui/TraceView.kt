package com.huanfuli.lapsight.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huanfuli.lapsight.shared.review.TraceLayer
import com.huanfuli.lapsight.shared.review.TracePoint
import com.huanfuli.lapsight.shared.review.TraceRole
import com.huanfuli.lapsight.shared.review.TraceViewport
import com.huanfuli.lapsight.shared.session.GeoPointDto
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * Offline vector trace renderer (SESS-03, D-33 through D-36).
 *
 * Renders a list of [TraceLayer]s onto a Compose [Canvas]. No map tiles, no
 * external map SDKs, no geocoding. Canonical lat/lon stays in saved data; only
 * projected render points enter the Canvas (D-34). Each layer carries a
 * semantic [TraceRole] which resolves to the active theme's canvas palette
 * here — this is the single place trace roles become colors, shared by
 * Review traces and the course editor.
 *
 * @param layers     trace layers to render (background to foreground).
 * @param modifier   optional Compose modifier.
 * @param minHeight  the minimum height of the trace area in dp.
 * @param maxHeight  the maximum height of the trace area in dp.
 * @param fillParent when true, fill all space the parent grants (e.g. a
 *                   `weight(1f)` pane) instead of the fixed height chain.
 * @param positionMarker optional live "you are here" indicator drawn on top of
 *                   the trace (marking/timing). Its points are in the same
 *                   normalized space as [layers].
 */
@Composable
fun TraceView(
    layers: List<TraceLayer>,
    modifier: Modifier = Modifier,
    minHeight: Dp = 200.dp,
    maxHeight: Dp = 320.dp,
    fillParent: Boolean = false,
    positionMarker: TracePositionMarker? = null,
) {
    if (layers.isEmpty()) return

    val density = LocalDensity.current
    val height = if (maxHeight < minHeight) minHeight else maxHeight

    // Live-position marker resources resolved up front — DrawScope is not composable.
    val markerPainter = rememberVectorPainter(LocationMarkerIcon)
    val markerDiscColor = LapSightTheme.colors.statusReady
    val markerRingColor = LapSightTheme.colors.dashBackground
    val markerArrowTint = LapSightTheme.colors.dashBackground
    val markerDiscRadiusPx = with(density) { 9.dp.toPx() }
    val markerRingWidthPx = with(density) { 1.5.dp.toPx() }
    val markerArrowSizePx = with(density) { 15.dp.toPx() }

    BoxWithConstraints(
        modifier = if (fillParent) {
            modifier.fillMaxSize()
        } else {
            modifier
                .fillMaxWidth()
                .height(height)
        }.clipToBounds(),
    ) {
        val canvasWidth = this.maxWidth
        val boundedHeight = this.maxHeight
        val canvasHeight = if (fillParent && boundedHeight.value.isFinite()) boundedHeight else height

        if (canvasWidth.value <= 0f || canvasHeight.value <= 0f) return@BoxWithConstraints

        val w = with(density) { canvasWidth.toPx() }
        val h = with(density) { canvasHeight.toPx() }
        // Zoom-to-fit: one uniform scale that fills the pane with the drawn
        // content while keeping course geometry true (D-34).
        val frame = TraceCanvasFrame(layers.flatMap { it.points }, w, h)

        // Resolve every role up front — DrawScope is not composable.
        val roleColors = TraceRole.entries.associateWith { it.traceColor() }
        val traceHaloColor = LapSightTheme.colors.dashBackground

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
        ) {
            for (layer in layers) {
                if (layer.points.size < 2) continue

                val color = roleColors.getValue(layer.role)
                val strokePx = with(density) { layer.strokeWidth.dp.toPx() }

                val canvasPoints = layer.points.map { frame.toCanvas(it) }

                when (layer.role) {
                    TraceRole.Reference -> {
                        drawTracePath(
                            points = canvasPoints,
                            color = traceHaloColor,
                            strokeWidth = (strokePx * 2.7f).coerceAtLeast(with(density) { 11.dp.toPx() }),
                            closed = layer.closed,
                            smooth = true,
                        )
                        drawTracePath(
                            points = canvasPoints,
                            color = color,
                            strokeWidth = (strokePx * 1.75f).coerceAtLeast(with(density) { 7.dp.toPx() }),
                            closed = layer.closed,
                            smooth = true,
                        )
                    }
                    TraceRole.StartFinish -> {
                        drawShortTimingLine(
                            points = canvasPoints,
                            halo = traceHaloColor,
                            color = color,
                            outerWidth = (strokePx * 2.0f).coerceAtLeast(with(density) { 7.dp.toPx() }),
                            innerWidth = (strokePx * 1.15f).coerceAtLeast(with(density) { 4.dp.toPx() }),
                        )
                    }
                    TraceRole.Sector -> {
                        drawTraceLines(
                            points = canvasPoints,
                            color = traceHaloColor,
                            strokeWidth = (strokePx * 2.0f).coerceAtLeast(with(density) { 5.dp.toPx() }),
                        )
                        drawTraceLines(points = canvasPoints, color = color, strokeWidth = strokePx)
                    }
                    else -> if (layer.dashed) {
                        drawTracePath(
                            points = canvasPoints,
                            color = color,
                            strokeWidth = strokePx,
                            closed = layer.closed,
                            smooth = false,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
                        )
                    } else {
                        drawTracePath(
                            points = canvasPoints,
                            color = color,
                            strokeWidth = strokePx,
                            closed = layer.closed,
                            smooth = layer.points.size >= 5,
                        )
                    }
                }
            }

            positionMarker?.let { marker ->
                drawPositionMarker(
                    center = frame.toCanvas(marker.current),
                    headingFrom = marker.previous?.let { frame.toCanvas(it) },
                    painter = markerPainter,
                    discColor = markerDiscColor,
                    ringColor = markerRingColor,
                    arrowTint = markerArrowTint,
                    discRadiusPx = markerDiscRadiusPx,
                    ringWidthPx = markerRingWidthPx,
                    arrowSizePx = markerArrowSizePx,
                )
            }
        }
    }
}

/** Every course/track projection in the app uses a 400×300 view box. */
internal const val TRACE_VIEW_BOX_ASPECT: Float = 4f / 3f

/**
 * Content-fit mapping between projected trace points and an actual canvas.
 *
 * Projected points are normalized to a view box whose aspect
 * ([TRACE_VIEW_BOX_ASPECT]) makes them geometrically true. The frame restores
 * that aspect, takes the bounding box of the content it is given, and applies
 * ONE uniform zoom that fits the box inside the canvas with a small margin —
 * the course fills any pane shape on its constraining axis with no
 * distortion. Shared by [TraceView] and the course editor canvas; the
 * editor's inverse pointer mapping must mirror the same frame (built from the
 * same points) or drags would land on the wrong course point. The editor
 * builds its frame from the static reference loop only, so the zoom never
 * shifts mid-drag.
 */
internal class TraceCanvasFrame(
    points: List<TracePoint>,
    canvasWidth: Float,
    canvasHeight: Float,
    viewBoxAspect: Float = TRACE_VIEW_BOX_ASPECT,
    paddingFraction: Float = 0.05f,
) {
    private val aspect: Float = if (viewBoxAspect > 0f) viewBoxAspect else 1f
    private val scale: Float
    private val offsetX: Float
    private val offsetY: Float

    init {
        var uMin = Float.POSITIVE_INFINITY
        var uMax = Float.NEGATIVE_INFINITY
        var vMin = Float.POSITIVE_INFINITY
        var vMax = Float.NEGATIVE_INFINITY
        for (p in points) {
            // Aspect-true space: the view box spans aspect × 1.
            val u = p.x.toFloat() * aspect
            val v = p.y.toFloat()
            if (u < uMin) uMin = u
            if (u > uMax) uMax = u
            if (v < vMin) vMin = v
            if (v > vMax) vMax = v
        }
        if (points.isEmpty() || canvasWidth <= 0f || canvasHeight <= 0f) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        } else {
            val spanU = (uMax - uMin).coerceAtLeast(1e-6f)
            val spanV = (vMax - vMin).coerceAtLeast(1e-6f)
            val pad = paddingFraction.coerceIn(0f, 0.4f)
            val usableW = canvasWidth * (1f - 2f * pad)
            val usableH = canvasHeight * (1f - 2f * pad)
            scale = minOf(usableW / spanU, usableH / spanV)
            offsetX = (canvasWidth - spanU * scale) / 2f - uMin * scale
            offsetY = (canvasHeight - spanV * scale) / 2f - vMin * scale
        }
    }

    /** Normalized view-box point → canvas pixels. */
    fun toCanvas(point: TracePoint): Offset = Offset(
        offsetX + point.x.toFloat() * aspect * scale,
        offsetY + point.y.toFloat() * scale,
    )

    /** Canvas pixel x → normalized view-box x (inverse of [toCanvas]). */
    fun toNormalizedX(px: Float): Double =
        if (scale > 0f) ((px - offsetX) / scale / aspect).toDouble() else 0.0

    /** Canvas pixel y → normalized view-box y (inverse of [toCanvas]). */
    fun toNormalizedY(py: Float): Double =
        if (scale > 0f) ((py - offsetY) / scale).toDouble() else 0.0
}

/**
 * A live "you are here" indicator for the marking / timing course maps.
 *
 * [current] is the driver's latest projected position; [previous] is a slightly
 * older position that fixes the heading (arrow points [previous]→[current]).
 * Both are in the SAME normalized space as the trace layers, so they map through
 * the shared [TraceCanvasFrame] and stay pinned to the drawn course. A null
 * [previous] (or one coincident with [current]) draws the arrow pointing up.
 */
data class TracePositionMarker(
    val current: TracePoint,
    val previous: TracePoint? = null,
)

/**
 * Build a live position marker for a course map from the [viewport] that placed
 * it (see [com.huanfuli.lapsight.shared.review.ProjectedTrace]). [current] is
 * the live fix; [previous] is an earlier fix that fixes the on-screen heading
 * (null → the arrow points up). Both are projected through the same viewport as
 * the map's layers, so the marker lands in their normalized space and stays
 * pinned to the drawn course.
 *
 * This is the reusable seam for the marking pane, the pre-timing preview, and
 * future selectable live-timing panels: build a `ProjectedTrace`, then pass its
 * `viewport` here with the current/previous GPS fixes.
 */
fun courseMarker(
    viewport: TraceViewport?,
    current: GeoPointDto?,
    previous: GeoPointDto? = null,
): TracePositionMarker? {
    val vp = viewport ?: return null
    val cur = current ?: return null
    val currentPoint = vp.geoToNormalized(cur)
    if (!currentPoint.isWithinTraceBounds()) return null
    return TracePositionMarker(
        current = currentPoint,
        previous = previous
            ?.let { vp.geoToNormalized(it) }
            ?.takeIf { it.isWithinTraceBounds() },
    )
}

private fun TracePoint.isWithinTraceBounds(): Boolean =
    x.isFinite() && y.isFinite() && x in 0.0..1.0 && y in 0.0..1.0

/**
 * Draw the live position marker: a filled disc with a ring for legibility over
 * any trace color, and a navigation arrowhead rotated to the travel heading.
 */
private fun DrawScope.drawPositionMarker(
    center: Offset,
    headingFrom: Offset?,
    painter: VectorPainter,
    discColor: Color,
    ringColor: Color,
    arrowTint: Color,
    discRadiusPx: Float,
    ringWidthPx: Float,
    arrowSizePx: Float,
) {
    drawCircle(color = discColor, radius = discRadiusPx, center = center)
    drawCircle(color = ringColor, radius = discRadiusPx, center = center, style = Stroke(width = ringWidthPx))

    // The Navigation glyph points up (−Y) by default; rotate it onto the travel
    // vector. Heading is computed in canvas pixels so per-axis frame scaling is
    // already baked in.
    val degrees = if (headingFrom != null) {
        val dx = center.x - headingFrom.x
        val dy = center.y - headingFrom.y
        if (hypot(dx, dy) > 1e-3f) {
            (atan2(dx.toDouble(), -dy.toDouble()) * 180.0 / kotlin.math.PI).toFloat()
        } else {
            0f
        }
    } else {
        0f
    }

    rotate(degrees = degrees, pivot = center) {
        translate(left = center.x - arrowSizePx / 2f, top = center.y - arrowSizePx / 2f) {
            with(painter) {
                draw(size = Size(arrowSizePx, arrowSizePx), colorFilter = ColorFilter.tint(arrowTint))
            }
        }
    }
}

/** Resolves a [TraceRole] to the active theme's canvas palette. */
@Composable
internal fun TraceRole.traceColor(): Color = when (this) {
    TraceRole.Reference -> LapSightTheme.colors.traceReference
    TraceRole.Session -> LapSightTheme.colors.traceSession
    TraceRole.Marking -> LapSightTheme.colors.traceMarking
    TraceRole.Outlier -> LapSightTheme.colors.traceOutlier.copy(alpha = 0.5f)
    TraceRole.StartFinish -> LapSightTheme.colors.traceStartFinish
    TraceRole.Sector -> LapSightTheme.colors.traceSector
    TraceRole.BestLap -> LapSightTheme.colors.traceBestLap
}

/**
 * Draw connected line segments for a trace layer (solid stroke).
 */
private fun DrawScope.drawTraceLines(
    points: List<Offset>,
    color: Color,
    strokeWidth: Float,
) {
    if (points.size < 2) return
    for (i in 1 until points.size) {
        drawLine(
            color = color,
            start = points[i - 1],
            end = points[i],
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Draw a finite timing boundary as a short stripe centered on its projected line.
 * The persisted line remains full-length; only this display marker is shortened.
 */
private fun DrawScope.drawShortTimingLine(
    points: List<Offset>,
    halo: Color,
    color: Color,
    outerWidth: Float,
    innerWidth: Float,
) {
    if (points.size < 2) return
    val a = points.first()
    val b = points.last()
    val dx = b.x - a.x
    val dy = b.y - a.y
    val length = hypot(dx.toDouble(), dy.toDouble()).toFloat()
    if (length <= 1e-3f) return

    val markerLength = min(length, innerWidth * 7f)
    val half = markerLength / 2f
    val ux = dx / length
    val uy = dy / length
    val center = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    val start = Offset(center.x - ux * half, center.y - uy * half)
    val end = Offset(center.x + ux * half, center.y + uy * half)
    drawLine(color = halo, start = start, end = end, strokeWidth = outerWidth, cap = StrokeCap.Square)
    drawLine(color = color, start = start, end = end, strokeWidth = innerWidth, cap = StrokeCap.Square)
}

/**
 * Draw connected trace segments, optionally as a smoothed render-only path.
 */
private fun DrawScope.drawTracePath(
    points: List<Offset>,
    color: Color,
    strokeWidth: Float,
    closed: Boolean = false,
    smooth: Boolean = false,
    pathEffect: PathEffect? = null,
) {
    if (points.size < 2) return
    val path = if (smooth && points.size >= 3) smoothPath(points, closed) else straightPath(points, closed)
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = pathEffect,
        ),
    )
}

private fun straightPath(points: List<Offset>, closed: Boolean): Path = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        lineTo(points[i].x, points[i].y)
    }
    if (closed) close()
}

private fun smoothPath(points: List<Offset>, closed: Boolean): Path = Path().apply {
    if (closed && points.size >= 3) {
        val start = midpoint(points.last(), points.first())
        moveTo(start.x, start.y)
        for (i in points.indices) {
            val current = points[i]
            val next = points[(i + 1) % points.size]
            val mid = midpoint(current, next)
            quadraticTo(current.x, current.y, mid.x, mid.y)
        }
        close()
    } else {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.lastIndex) {
            val mid = midpoint(points[i], points[i + 1])
            quadraticTo(points[i].x, points[i].y, mid.x, mid.y)
        }
        lineTo(points.last().x, points.last().y)
    }
}

private fun midpoint(a: Offset, b: Offset): Offset =
    Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
