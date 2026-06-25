package com.huanfuli.lapsight.shared.lap

import kotlin.math.PI
import kotlin.math.cos

/**
 * Equirectangular local-tangent projection around a fixed session origin.
 *
 * Maps geographic degrees to a flat x/y meter plane suitable for track-scale
 * geometry (a few kilometers). It is intentionally simple and deterministic:
 *
 * - x = east offset in meters
 * - y = north offset in meters
 *
 * Accuracy degrades far from the origin and near the poles, which is acceptable
 * for closed-course lap timing. Tests document expected tolerances. The
 * projection is reversible ([toGeo]) closely enough for sanity checks.
 *
 * @param origin the geographic origin, usually the first sample of a session.
 */
class LocalProjection(val origin: GeoPoint) {

    private val metersPerDegLat = METERS_PER_DEGREE
    private val metersPerDegLon = METERS_PER_DEGREE * cos(origin.latitude.toRadians())

    /** Project a geographic point into local meters relative to [origin]. */
    fun toLocal(point: GeoPoint): LocalPoint = LocalPoint(
        x = (point.longitude - origin.longitude) * metersPerDegLon,
        y = (point.latitude - origin.latitude) * metersPerDegLat,
    )

    /** Inverse of [toLocal]. Approximate; for test sanity checks. */
    fun toGeo(point: LocalPoint): GeoPoint = GeoPoint(
        latitude = origin.latitude + point.y / metersPerDegLat,
        longitude = origin.longitude + point.x / metersPerDegLon,
    )

    companion object {
        /** Meters per degree of latitude (WGS84 mean). */
        const val METERS_PER_DEGREE: Double = 111_320.0
    }
}

private fun Double.toRadians(): Double = this * PI / 180.0
