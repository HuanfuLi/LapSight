package com.huanfuli.lapsight.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huanfuli.lapsight.shared.review.TraceLayer
import com.huanfuli.lapsight.shared.review.TracePoint
import com.huanfuli.lapsight.shared.review.TraceRole

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
 */
@Composable
fun TraceView(
    layers: List<TraceLayer>,
    modifier: Modifier = Modifier,
    minHeight: Dp = 200.dp,
    maxHeight: Dp = 320.dp,
    fillParent: Boolean = false,
) {
    if (layers.isEmpty()) return

    val density = LocalDensity.current
    val height = if (maxHeight < minHeight) minHeight else maxHeight

    BoxWithConstraints(
        modifier = if (fillParent) {
            modifier.fillMaxSize()
        } else {
            modifier
                .fillMaxWidth()
                .height(height)
        },
    ) {
        val canvasWidth = this.maxWidth
        val boundedHeight = this.maxHeight
        val canvasHeight = if (fillParent && boundedHeight.value.isFinite()) boundedHeight else height

        if (canvasWidth.value <= 0f || canvasHeight.value <= 0f) return@BoxWithConstraints

        val w = with(density) { canvasWidth.toPx() }
        val h = with(density) { canvasHeight.toPx() }
        // Draw the projection view box at its own aspect ratio, centered — the
        // normalized points are only geometrically true at that aspect (D-34).
        val frame = TraceCanvasFrame(w, h)

        // Resolve every role up front — DrawScope is not composable.
        val roleColors = TraceRole.entries.associateWith { it.traceColor() }

        Canvas(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            for (layer in layers) {
                if (layer.points.size < 2) continue

                val color = roleColors.getValue(layer.role)
                val strokePx = with(density) { layer.strokeWidth.dp.toPx() }

                if (layer.dashed) {
                    drawTracePath(
                        points = layer.points.map { frame.toCanvas(it) },
                        color = color,
                        strokeWidth = strokePx,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
                    )
                } else {
                    drawTraceLines(
                        points = layer.points.map { frame.toCanvas(it) },
                        color = color,
                        strokeWidth = strokePx,
                    )
                }
            }
        }
    }
}

/** Every course/track projection in the app uses a 400×300 view box. */
internal const val TRACE_VIEW_BOX_ASPECT: Float = 4f / 3f

/**
 * Letterboxed mapping between the projection's normalized [0..1] view box and
 * an actual canvas: the view box renders at [TRACE_VIEW_BOX_ASPECT] centered
 * inside the canvas, so course geometry keeps true proportions in any pane
 * shape. Shared by [TraceView] and the course editor canvas — the editor's
 * inverse pointer mapping must mirror the same frame or drags would land on
 * the wrong course point.
 */
internal class TraceCanvasFrame(
    canvasWidth: Float,
    canvasHeight: Float,
    viewBoxAspect: Float = TRACE_VIEW_BOX_ASPECT,
) {
    val drawWidth: Float
    val drawHeight: Float
    val offsetX: Float
    val offsetY: Float

    init {
        val aspect = if (viewBoxAspect > 0f) viewBoxAspect else 1f
        drawWidth = minOf(canvasWidth, canvasHeight * aspect)
        drawHeight = drawWidth / aspect
        offsetX = (canvasWidth - drawWidth) / 2f
        offsetY = (canvasHeight - drawHeight) / 2f
    }

    /** Normalized view-box point → canvas pixels. */
    fun toCanvas(point: TracePoint): Offset = Offset(
        offsetX + point.x.toFloat() * drawWidth,
        offsetY + point.y.toFloat() * drawHeight,
    )

    /** Canvas pixel x → normalized view-box x (inverse of [toCanvas]). */
    fun toNormalizedX(px: Float): Double =
        if (drawWidth > 0f) ((px - offsetX) / drawWidth).toDouble() else 0.0

    /** Canvas pixel y → normalized view-box y (inverse of [toCanvas]). */
    fun toNormalizedY(py: Float): Double =
        if (drawHeight > 0f) ((py - offsetY) / drawHeight).toDouble() else 0.0
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
        )
    }
}

/**
 * Draw connected path segments for a dashed trace layer.
 */
private fun DrawScope.drawTracePath(
    points: List<Offset>,
    color: Color,
    strokeWidth: Float,
    pathEffect: PathEffect,
) {
    if (points.size < 2) return
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            lineTo(points[i].x, points[i].y)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = strokeWidth,
            pathEffect = pathEffect,
        ),
    )
}
