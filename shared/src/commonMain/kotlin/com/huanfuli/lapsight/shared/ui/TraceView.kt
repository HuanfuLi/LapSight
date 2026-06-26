package com.huanfuli.lapsight.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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

/**
 * Offline vector trace renderer (SESS-03, D-33 through D-36).
 *
 * Renders a list of [TraceLayer]s onto a Compose [Canvas] using the UI-SPEC
 * color/stroke contract. No map tiles, no external map SDKs, no geocoding.
 * Canonical lat/lon stays in saved data; only projected render points enter
 * the Canvas (D-34).
 *
 * Colors and strokes per UI-SPEC:
 * - Reference line / best-lap highlight: cyan #62E3FF, 3px / 4px
 * - Marking trace / session trace: muted #9AA8B8, 2px
 * - Outlier sections: orange #FFB84D 50% alpha, 2px dashed
 * - Start/finish line: green #8CFF9B, 3px
 * - Sector lines: amber #FFD166, 2px
 *
 * @param layers    trace layers to render (background to foreground).
 * @param modifier  optional Compose modifier.
 * @param minHeight the minimum height of the trace area in dp.
 * @param maxHeight the maximum height of the trace area in dp.
 */
@Composable
fun TraceView(
    layers: List<TraceLayer>,
    modifier: Modifier = Modifier,
    minHeight: Dp = 200.dp,
    maxHeight: Dp = 320.dp,
) {
    if (layers.isEmpty()) return

    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight, max = maxHeight),
    ) {
        val canvasWidth = maxWidth
        val canvasHeight = maxHeight

        if (canvasWidth.value <= 0f || canvasHeight.value <= 0f) return@BoxWithConstraints

        val w = with(density) { canvasWidth.toPx() }
        val h = with(density) { canvasHeight.toPx() }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight, max = maxHeight),
        ) {
            for (layer in layers) {
                if (layer.points.size < 2) continue

                val color = Color(layer.color)
                val strokePx = with(density) { layer.strokeWidth.dp.toPx() }

                if (layer.dashed) {
                    drawTracePath(
                        points = layer.points.map { Offset(it.x.toFloat() * w, it.y.toFloat() * h) },
                        color = color,
                        strokeWidth = strokePx,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
                    )
                } else {
                    drawTraceLines(
                        points = layer.points.map { Offset(it.x.toFloat() * w, it.y.toFloat() * h) },
                        color = color,
                        strokeWidth = strokePx,
                    )
                }
            }
        }
    }
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
