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
 * Semantic role of a trace layer. The review/data layer never carries colors —
 * the UI resolves each role against the active theme's canvas palette
 * (`LapSightColors.trace*`), so light/dark and any future palette change never
 * touch recorded data or layer builders.
 */
enum class TraceRole {
    /** Track reference line baseline. */
    Reference,

    /** Driven session trace. */
    Session,

    /** Raw marking capture trace. */
    Marking,

    /** Outlier/degraded sections (rendered dashed and translucent). */
    Outlier,

    /** Start/finish line. */
    StartFinish,

    /** Sector boundary lines. */
    Sector,

    /** Selected/best lap highlight (motorsport purple). */
    BestLap,
}

/**
 * A named set of render-only trace points with a semantic [role], stroke, and
 * dash style. Layering order is caller-managed; Canvas renders background
 * layers first.
 */
data class TraceLayer(
    val name: String,
    val points: List<TracePoint>,
    val role: TraceRole,
    val strokeWidth: Float,
    val dashed: Boolean = false,
)

/**
 * An invertible viewport transform between canonical geographic / local-meter
 * coordinates and normalized [0..1] render space (Plan 05-06, D-10).
 *
 * Built once from a set of geo layers via [TraceViewport.fromLayers], it captures
 * the single common bounding-box projection (origin [LocalProjection], min corner,
 * aspect-preserving scale, and centering offset) used by [TraceProjection].
 * Unlike the one-way [TraceProjection.project], a viewport also inverts: an editor
 * surface converts a pointer position in normalized space back to local meters /
 * latitude-longitude so a drag forwards only a *candidate* progress and never a
 * persisted screen coordinate (D-10). The transform is reversible; saved geometry
 * stays in lat/lon.
 *
 * Aspect note: normalized [0..1] coordinates are relative to the declared
 * `width × height` view box, so [scaleX]/[scaleY] differ in normalized units but
 * describe ONE uniform meters→pixels scale once the view box is rendered at its
 * own aspect ratio. Renderers must draw the view box at that aspect (letterboxed
 * inside a differently-shaped canvas) or the course geometry distorts.
 */
class TraceViewport internal constructor(
    /** The canonical projection around the first geo point of the source layers. */
    val projection: LocalProjection,
    private val minX: Double,
    private val minY: Double,
    /** Meters → normalized-x; `scaleX * width == scaleY * height` (pixel-uniform). */
    private val scaleX: Double,
    /** Meters → normalized-y; see [scaleX]. */
    private val scaleY: Double,
    private val offsetX: Double,
    private val offsetY: Double,
) {
    /** Local meters → normalized [0..1] render point. */
    fun localToNormalized(point: LocalPoint): TracePoint = TracePoint(
        x = offsetX + (point.x - minX) * scaleX,
        y = offsetY + (point.y - minY) * scaleY,
    )

    /** Canonical lat/lon → normalized [0..1] render point. */
    fun geoToNormalized(geo: GeoPointDto): TracePoint =
        localToNormalized(projection.toLocal(GeoPoint(geo.latitude, geo.longitude)))

    /** Normalized [0..1] render point → local meters (inverse of [localToNormalized]). */
    fun normalizedToLocal(normalizedX: Double, normalizedY: Double): LocalPoint = LocalPoint(
        x = minX + (normalizedX - offsetX) / scaleX,
        y = minY + (normalizedY - offsetY) / scaleY,
    )

    /** Normalized [0..1] render point → canonical lat/lon (inverse of [geoToNormalized]). */
    fun normalizedToGeo(normalizedX: Double, normalizedY: Double): GeoPointDto {
        val geo = projection.toGeo(normalizedToLocal(normalizedX, normalizedY))
        return GeoPointDto(latitude = geo.latitude, longitude = geo.longitude)
    }

    /** Project one geo layer into normalized render points. */
    fun projectLayer(layer: List<GeoPointDto>): List<TracePoint> = layer.map { geoToNormalized(it) }

    companion object {
        private const val MIN_BOUNDING_BOX_METERS = 1e-6

        /**
         * Build the common bounding-box viewport across [layers], or `null` for an
         * empty/degenerate input (no points, single point, or zero-area bounds). The
         * math is identical to [TraceProjection.project] so the forward projection
         * and the editor's inverse share exactly one transform.
         */
        fun fromLayers(
            layers: List<List<GeoPointDto>>,
            width: Double,
            height: Double,
            padding: Double,
        ): TraceViewport? {
            if (width <= 0.0 || height <= 0.0) return null
            val allDtos = layers.flatten()
            if (allDtos.isEmpty()) return null

            val originDto = allDtos.first()
            val projection = LocalProjection(GeoPoint(originDto.latitude, originDto.longitude))

            var minX = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
            for (dto in allDtos) {
                val pt = projection.toLocal(GeoPoint(dto.latitude, dto.longitude))
                minX = min(minX, pt.x)
                maxX = max(maxX, pt.x)
                minY = min(minY, pt.y)
                maxY = max(maxY, pt.y)
            }

            val spanX = maxX - minX
            val spanY = maxY - minY
            // Degenerate bounding box (all points coincident or within tolerance).
            if (spanX < MIN_BOUNDING_BOX_METERS && spanY < MIN_BOUNDING_BOX_METERS) return null

            // Preserve aspect ratio: one uniform meters→pixels scale for the
            // declared view box, constrained by whichever axis runs out first.
            // In normalized units that splits into scaleX = s/width and
            // scaleY = s/height, so a square in meters renders square whenever
            // the view box is drawn at its own width:height aspect.
            val padded = padding.coerceIn(0.0, 0.4)
            val usable = 1.0 - 2.0 * padded

            val pixelScale = min(
                if (spanX > 0.0) usable * width / spanX else Double.POSITIVE_INFINITY,
                if (spanY > 0.0) usable * height / spanY else Double.POSITIVE_INFINITY,
            )
            val scaleX = pixelScale / width
            val scaleY = pixelScale / height

            val offsetX = padded + (usable - spanX * scaleX) / 2.0
            val offsetY = padded + (usable - spanY * scaleY) / 2.0

            return TraceViewport(projection, minX, minY, scaleX, scaleY, offsetX, offsetY)
        }
    }
}

/**
 * Pure projection from canonical [GeoPointDto] lists to normalized [TracePoint]
 * render layers. Uses [LocalProjection] around the first valid point to convert
 * lat/lon to local meters, computes a stable bounding box across all supplied
 * layers, preserves aspect ratio, applies caller padding, and returns render-only
 * points (D-34). Never writes screen coordinates back to saved models.
 *
 * Delegates to the single [TraceViewport] transform so the offline editor's
 * inverse screen→local conversion stays consistent with this forward projection.
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
        if (layers.isEmpty()) return emptyList()
        val viewport = TraceViewport.fromLayers(layers, width, height, padding)
            ?: return layers.map { emptyList() }
        return layers.map { viewport.projectLayer(it) }
    }
}
