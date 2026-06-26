package com.huanfuli.lapsight.shared.review

import com.huanfuli.lapsight.shared.session.GeoPointDto

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
 * render layers. Uses [com.huanfuli.lapsight.shared.lap.LocalProjection] around
 * the first valid point to convert lat/lon to local meters, computes a stable
 * bounding box across all supplied layers, preserves aspect ratio, applies caller
 * padding, and returns render-only points (D-34). Never writes screen coordinates
 * back to saved models.
 *
 * This is a stub; real implementation arrives in Plan 03-07 Task 2.
 */
object TraceProjection {

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
        // Stub — real implementation in Task 2.
        return layers.map { emptyList() }
    }
}
