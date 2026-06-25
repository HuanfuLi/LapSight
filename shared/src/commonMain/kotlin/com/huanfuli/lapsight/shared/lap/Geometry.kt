package com.huanfuli.lapsight.shared.lap

/**
 * A geographic point in WGS84-style decimal degrees.
 *
 * Pure data with no platform dependency. Used as the public input for line
 * definitions and as the raw form before [LocalProjection] maps it to meters.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

/**
 * A point in a flat local meter coordinate space near a session origin.
 *
 * x grows toward local east, y grows toward local north. Produced by
 * [LocalProjection]. All crossing geometry runs in this space so that the
 * algorithms are simple Euclidean operations with stable, testable tolerances.
 */
data class LocalPoint(
    val x: Double,
    val y: Double,
)
