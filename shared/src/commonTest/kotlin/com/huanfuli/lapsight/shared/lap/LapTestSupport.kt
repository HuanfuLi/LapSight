package com.huanfuli.lapsight.shared.lap

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.LocationSource

/**
 * Shared helpers for building deterministic samples in lap engine tests.
 *
 * Coordinates are kept tiny and near the origin so the equirectangular
 * projection stays effectively linear, which makes expected meter offsets easy
 * to reason about in assertions.
 */
object LapTestSupport {

    val ORIGIN = GeoPoint(latitude = 0.0, longitude = 0.0)

    /** Meters east -> longitude degrees at the equator. */
    fun lonForMetersEast(meters: Double): Double =
        meters / LocalProjection.METERS_PER_DEGREE

    /** Meters north -> latitude degrees. */
    fun latForMetersNorth(meters: Double): Double =
        meters / LocalProjection.METERS_PER_DEGREE

    /**
     * Build a sample positioned [eastMeters]/[northMeters] from the origin.
     */
    fun sample(
        elapsedMillis: Long,
        eastMeters: Double,
        northMeters: Double = 0.0,
        accuracy: Double? = 5.0,
        speed: Double? = 12.0,
        heading: Double? = 90.0,
    ): LocationSample = LocationSample(
        elapsedMillis = elapsedMillis,
        latitude = latForMetersNorth(northMeters),
        longitude = lonForMetersEast(eastMeters),
        horizontalAccuracyMeters = accuracy,
        speedMetersPerSecond = speed,
        headingDegrees = heading,
        altitudeMeters = 200.0,
        source = LocationSource.Simulated,
    )

    /** A vertical start/finish line at x = 0 spanning +/- 20 m north/south. */
    fun verticalStartFinish(): StartFinishLine = StartFinishLine(
        pointA = GeoPoint(latForMetersNorth(-20.0), 0.0),
        pointB = GeoPoint(latForMetersNorth(20.0), 0.0),
    )

    /** A vertical sector line at the given east offset. */
    fun verticalSector(id: String, name: String, order: Int, eastMeters: Double): SectorLine =
        SectorLine(
            id = id,
            name = name,
            order = order,
            pointA = GeoPoint(latForMetersNorth(-20.0), lonForMetersEast(eastMeters)),
            pointB = GeoPoint(latForMetersNorth(20.0), lonForMetersEast(eastMeters)),
        )

    /** Run a full sample list through a fresh engine and return final state. */
    fun run(course: CourseDefinition, config: LapEngineConfig, samples: List<LocationSample>): LapTimingState {
        val engine = LapEngine(course, config)
        var state = engine.state
        for (s in samples) state = engine.onSample(s)
        return state
    }
}
