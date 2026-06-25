package com.huanfuli.lapsight.shared.lap

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.LocationSource

/**
 * Deterministic synthetic fixtures for the lap engine.
 *
 * All geometry is built in a small local meter frame around a fixed origin so
 * that expected meter offsets stay easy to reason about. Coordinates are then
 * converted to lat/lon for [LocationSample]. Fixtures are pure Kotlin builders
 * (no test resources, no persistence), and every timestamp is fixed so replays
 * are reproducible.
 *
 * The demo course is a simple rectangular "loop":
 * - start/finish line is the vertical segment at x = 0 (y in [-25, 25])
 * - the car drives east across it, loops south of the line, and returns,
 *   crossing the line again to complete each lap
 * - two sector lines sit at x = 60 and x = 120
 */
object ReplayFixtures {

    private const val M_PER_DEG = LocalProjection.METERS_PER_DEGREE

    private fun lon(eastMeters: Double): Double = eastMeters / M_PER_DEG
    private fun lat(northMeters: Double): Double = northMeters / M_PER_DEG

    /** The standard demo course used by both replay tests and the demo UI. */
    val DEMO_COURSE: CourseDefinition = CourseDefinition(
        startFinish = StartFinishLine(
            pointA = GeoPoint(lat(-25.0), lon(0.0)),
            pointB = GeoPoint(lat(25.0), lon(0.0)),
        ),
        sectors = listOf(
            SectorLine("S1", "Sector 1", order = 0, GeoPoint(lat(-25.0), lon(60.0)), GeoPoint(lat(25.0), lon(60.0))),
            SectorLine("S2", "Sector 2", order = 1, GeoPoint(lat(-25.0), lon(120.0)), GeoPoint(lat(25.0), lon(120.0))),
        ),
    )

    /** Config tuned for the demo: realistic gates but tolerant of demo speeds. */
    val DEMO_CONFIG: LapEngineConfig = LapEngineConfig(
        minLapDurationMillis = 5_000,
        crossingCooldownMillis = 2_000,
        maxHorizontalAccuracyMeters = 25.0,
        minSpeedMetersPerSecond = 1.0,
        directionToleranceDegrees = 80.0,
        enforceDirection = true,
    )

    private fun sample(
        elapsedMillis: Long,
        eastMeters: Double,
        northMeters: Double,
        accuracy: Double = 6.0,
        speed: Double = 15.0,
        heading: Double = 90.0,
    ): LocationSample = LocationSample(
        elapsedMillis = elapsedMillis,
        latitude = lat(northMeters),
        longitude = lon(eastMeters),
        horizontalAccuracyMeters = accuracy,
        speedMetersPerSecond = speed,
        headingDegrees = heading,
        altitudeMeters = 210.0,
        source = LocationSource.Simulated,
    )

    /** Simplest fixture: one west-to-east crossing of the start/finish line. */
    fun simpleLineCrossing(): List<LocationSample> = listOf(
        sample(0, eastMeters = -15.0, northMeters = 0.0),
        sample(2_000, eastMeters = 15.0, northMeters = 0.0),
    )

    /**
     * The "body" of a lap that begins just after an eastward start/finish
     * crossing at [startMillis] and ends with the next eastward start/finish
     * crossing at [startMillis] + [lapMillis].
     *
     * The body drives east through both sectors, loops south of all line
     * segments back to the west side, and re-crosses start/finish heading east.
     * The opening crossing (the eastward pass that began this lap) is NOT
     * included here; the caller supplies it once at the very start of a session.
     */
    private fun lapBody(startMillis: Long, lapMillis: Long): List<LocationSample> {
        val step = lapMillis / 8
        return listOf(
            // cross sector 1 at x=60
            sample(startMillis + 1 * step, eastMeters = 50.0, northMeters = 0.0),
            sample(startMillis + 2 * step, eastMeters = 70.0, northMeters = 0.0),
            // cross sector 2 at x=120
            sample(startMillis + 3 * step, eastMeters = 110.0, northMeters = 0.0),
            sample(startMillis + 4 * step, eastMeters = 130.0, northMeters = 0.0),
            // loop south (y=-50) heading back west, clear of all line segments
            sample(startMillis + 5 * step, eastMeters = 130.0, northMeters = -50.0, heading = 180.0),
            sample(startMillis + 6 * step, eastMeters = -20.0, northMeters = -50.0, heading = 270.0),
            // return to west of start/finish
            sample(startMillis + 7 * step, eastMeters = -20.0, northMeters = 0.0, heading = 0.0),
            // re-cross start/finish heading east -> closes this lap
            sample(startMillis + lapMillis, eastMeters = 15.0, northMeters = 0.0),
        )
    }

    /** The single eastward crossing that opens the first lap of a session. */
    private fun openingCrossing(): List<LocationSample> = listOf(
        sample(0, eastMeters = -15.0, northMeters = 0.0),
        sample(1_000, eastMeters = 15.0, northMeters = 0.0),
    )

    /** One full lap returning to the start/finish line. */
    fun oneLapLoop(lapMillis: Long = 40_000): List<LocationSample> =
        openingCrossing() + lapBody(startMillis = 1_000, lapMillis = lapMillis)

    /**
     * Multiple laps with variable durations. Each entry in [lapDurations] is a
     * lap length in millis; the engine should complete `lapDurations.size` laps.
     */
    fun multiLapLoop(lapDurations: List<Long> = listOf(40_000, 32_000, 36_000)): List<LocationSample> {
        val samples = mutableListOf<LocationSample>()
        samples += openingCrossing()
        var lapStart = 1_000L
        for (dur in lapDurations) {
            samples += lapBody(startMillis = lapStart, lapMillis = dur)
            lapStart += dur
        }
        return samples
    }

    /**
     * Jitter near the start/finish line: many small noisy samples that hover
     * around x=0 without a clean directional pass. Should not create laps.
     */
    fun jitterNearLine(): List<LocationSample> = listOf(
        sample(0, eastMeters = -2.0, northMeters = 1.0, speed = 0.5),
        sample(500, eastMeters = 1.0, northMeters = -1.0, speed = 0.5),
        sample(1_000, eastMeters = -1.0, northMeters = 2.0, speed = 0.4),
        sample(1_500, eastMeters = 2.0, northMeters = -2.0, speed = 0.6),
        sample(2_000, eastMeters = -1.5, northMeters = 0.5, speed = 0.5),
    )

    /**
     * Low-frequency samples: one lap where the only samples are far apart, so
     * the line lies strictly between consecutive points.
     */
    fun lowFrequencyLap(lapMillis: Long = 40_000): List<LocationSample> = listOf(
        sample(0, eastMeters = -40.0, northMeters = 0.0),
        sample(lapMillis / 4, eastMeters = 140.0, northMeters = 0.0),      // crosses start/finish + both sectors
        sample(lapMillis / 2, eastMeters = 140.0, northMeters = -60.0, heading = 200.0),
        sample(lapMillis * 3 / 4, eastMeters = -40.0, northMeters = -60.0, heading = 270.0),
        sample(lapMillis * 7 / 8, eastMeters = -40.0, northMeters = 0.0, heading = 0.0),
        sample(lapMillis, eastMeters = 40.0, northMeters = 0.0),           // re-cross within line span to complete
    )

    /** A wrong-direction crossing: car crosses start/finish heading west. */
    fun wrongDirectionCrossing(): List<LocationSample> = listOf(
        sample(0, eastMeters = 15.0, northMeters = 0.0, heading = 270.0),
        sample(2_000, eastMeters = -15.0, northMeters = 0.0, heading = 270.0),
    )

    /** Poor-accuracy crossing: a clean pass but with accuracy beyond the gate. */
    fun poorAccuracyCrossing(): List<LocationSample> = listOf(
        sample(0, eastMeters = -15.0, northMeters = 0.0, accuracy = 60.0),
        sample(2_000, eastMeters = 15.0, northMeters = 0.0, accuracy = 60.0),
    )

    /** One lap that crosses exactly one sector (sector 1). */
    fun oneLapOneSector(lapMillis: Long = 40_000): List<LocationSample> = listOf(
        sample(0, eastMeters = -15.0, northMeters = 0.0),
        sample(lapMillis / 8, eastMeters = 15.0, northMeters = 0.0),       // start/finish
        sample(lapMillis * 2 / 8, eastMeters = 50.0, northMeters = 0.0),
        sample(lapMillis * 3 / 8, eastMeters = 70.0, northMeters = 0.0),   // sector 1
        sample(lapMillis * 4 / 8, eastMeters = 70.0, northMeters = -60.0, heading = 200.0),
        sample(lapMillis * 6 / 8, eastMeters = -20.0, northMeters = -60.0, heading = 270.0),
        sample(lapMillis * 7 / 8, eastMeters = -20.0, northMeters = 0.0, heading = 0.0),
        sample(lapMillis, eastMeters = 15.0, northMeters = 0.0),           // complete
    )

    /** Multi-lap loop that crosses both sectors each lap. */
    fun multiLapMultiSector(): List<LocationSample> = multiLapLoop(listOf(40_000, 34_000))
}
