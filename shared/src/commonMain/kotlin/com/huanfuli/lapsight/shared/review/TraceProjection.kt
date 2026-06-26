package com.huanfuli.lapsight.shared.review

import com.huanfuli.lapsight.shared.lap.GeoPoint
import com.huanfuli.lapsight.shared.lap.LocalPoint
import com.huanfuli.lapsight.shared.lap.LocalProjection
import com.huanfuli.lapsight.shared.session.GeoPointDto
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A normalized render-only point in [0..1] range projected from canonical
 * lat/lon. Never persisted — screen coordinates are rendering-only (D-34).
 */
data class TracePoint(
    val x: Double,
    val y: Double,
)

/**
 * A named set of render-only trace points with color, stroke, and dash style.
 * Layering order is caller-managed; Canvas renders background layers first.
 */
data class TraceLayer(
    val name: String,
    val points: List<TracePoint>,
    val color: Long,
    val strokeWidth: Float,
    val dashed: Boolean = false,
)

/**
 * Pure projection from canonical [GeoPointDto] lists to normalized [TracePoint]
 * render layers. Uses [LocalProjection] around the first valid point to convert
 * lat/lon to local meters, computes a stable bounding box across all supplied
 * layers, preserves aspect ratio, applies caller padding, and returns render-only
 * points (D-34). Never writes screen coordinates back to saved models.
 */
object TraceProjection {

    private const val MIN_BOUNDING_BOX_METERS = 1e-6

    /**
     * Project multiple geo-point layers into a common normalized coordinate
     * system. Returns one [TracePoint] list per input layer.
     *
     * @param layers  one or more lists of [GeoPointDto] to project together.
     * @param width   target canvas width for aspect-ratio preservation.
     * @param height  target canvas height for aspect-ratio preservation.
     * @param padding fraction of the smaller dimension reserved as padding.
     * @return projected trace points, one list per layer, or empty lists for
     *         degenerate inputs.
     */
    fun project(
        layers: List<List<GeoPointDto>>,
        width: Double,
        height: Double,
        padding: Double,
    ): List<List<TracePoint>> {
        if (layers.isEmpty()) return emptyList()

        // Collect all points and find the first valid one as projection origin.
        val allDtos = layers.flatten()
        if (allDtos.isEmpty()) return layers.map { emptyList() }

        val originDto = allDtos.first()
        val projection = LocalProjection(GeoPoint(originDto.latitude, originDto.longitude))

        // Convert every point to local meters.
        val allLocal: List<List<LocalPoint>> = layers.map { layer ->
            layer.map { dto -> projection.toLocal(GeoPoint(dto.latitude, dto.longitude)) }
        }

        // Compute the bounding box across ALL layers.
        val allLocalFlat = allLocal.flatten()
        if (allLocalFlat.isEmpty()) return layers.map { emptyList() }

        var minX = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY

        for (pt in allLocalFlat) {
            minX = min(minX, pt.x)
            maxX = max(maxX, pt.x)
            minY = min(minY, pt.y)
            maxY = max(maxY, pt.y)
        }

        val spanX = maxX - minX
        val spanY = maxY - minY

        // Degenerate bounding box (all points coincident or within tolerance).
        if (spanX < MIN_BOUNDING_BOX_METERS && spanY < MIN_BOUNDING_BOX_METERS) {
            return layers.map { emptyList() }
        }

        // Preserve aspect ratio: fit the geographic bounding box into the
        // target canvas while keeping the correct proportions.
        val padded = padding.coerceIn(0.0, 0.4)
        val canvasAspect = if (height > 0.0) width / height else 1.0
        val dataAspect = if (spanY > 1e-9) spanX / spanY else 1.0

        // Compute the scale so the data fits within the padded canvas.
        val usableWidth = 1.0 - 2.0 * padded
        val usableHeight = 1.0 - 2.0 * padded

        val scaleX: Double
        val scaleY: Double
        if (dataAspect > canvasAspect) {
            // Data is wider than canvas → constrained by width.
            scaleX = usableWidth / spanX
            scaleY = scaleX // preserve aspect ratio
        } else {
            // Data is taller (or square) → constrained by height.
            scaleY = usableHeight / spanY
            scaleX = scaleY // preserve aspect ratio
        }

        // Center offset after scaling + padding.
        val scaledSpanX = spanX * scaleX
        val scaledSpanY = spanY * scaleY
        val offsetX = padded + (usableWidth - scaledSpanX) / 2.0
        val offsetY = padded + (usableHeight - scaledSpanY) / 2.0

        // Project each layer.
        return allLocal.map { localLayer ->
            localLayer.map { pt ->
                TracePoint(
                    x = offsetX + (pt.x - minX) * scaleX,
                    y = offsetY + (pt.y - minY) * scaleY,
                )
            }
        }
    }
}
