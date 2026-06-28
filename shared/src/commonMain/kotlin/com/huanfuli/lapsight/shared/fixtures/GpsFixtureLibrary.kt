package com.huanfuli.lapsight.shared.fixtures

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.session.GeoPointDto
import com.huanfuli.lapsight.shared.track.TrackReferenceLine
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A named, deterministic GPS fixture scenario built from fixed seeds (D-04).
 *
 * @property id stable scenario id used by tests, demo controls, and committed
 *   JSON fixture files.
 * @property name human-readable scenario label.
 * @property description what the scenario models and why it matters.
 * @property sessions one or more capture sessions. Single-session scenarios
 *   hold exactly one entry; [multi-session-best-candidate][GpsFixtureLibrary.MULTI_SESSION_BEST_CANDIDATE]
 *   holds several so later code can pick a per-track fastest valid lap.
 */
data class GpsFixtureScenario(
    val id: String,
    val name: String,
    val description: String,
    val sessions: List<List<LocationSample>>,
) {
    /** Flattened samples for single-session use; the first session for multi. */
    val samples: List<LocationSample>
        get() = if (sessions.size == 1) sessions[0] else sessions.flatten()
}

/**
 * Deterministic GPS mock/fixture system (D-01).
 *
 * All scenarios are generated from fixed seeds with a tiny self-contained
 * pseudo-random generator, so a given scenario produces identical samples on
 * every call and on every platform. Geometry is a closed oval "track" around a
 * fixed origin, converted to realistic latitude/longitude using a flat local
 * meter frame. Every sample carries [LocationSource.Simulated] so demo data is
 * never mistaken for live GPS (D-42).
 *
 * This is pure shared Kotlin: no Compose, Android, iOS, Okio, or serialization.
 * Committed JSON copies of each scenario live under
 * `commonMain/composeResources/files/fixtures/` for external/debug tooling; the
 * builders here are the canonical generators (D-04).
 */
object GpsFixtureLibrary {

    const val CLEAN_10_LOOP = "clean-10-loop"
    const val MINIMUM_5_LOOP = "minimum-5-loop"
    const val ONE_OUTLIER_LOOP = "one-outlier-loop"
    const val NOISE_DRIFT = "noise-drift"
    const val DROPPED_LOW_FREQUENCY = "dropped-low-frequency"
    const val MULTI_SESSION_BEST_CANDIDATE = "multi-session-best-candidate"
    const val VARIABLE_PACE_GHOST_UAT = "variable-pace-ghost-uat"
    const val COURSE_MATCH_AMBIGUITY = "course-match-ambiguity"
    const val COURSE_MATCH_RECOVERY = "course-match-recovery"
    const val COURSE_MATCH_BACKWARD = "course-match-backward"

    /** The six required scenario ids from D-04, in canonical order. */
    val requiredScenarioIds: List<String> = listOf(
        CLEAN_10_LOOP,
        MINIMUM_5_LOOP,
        ONE_OUTLIER_LOOP,
        NOISE_DRIFT,
        DROPPED_LOW_FREQUENCY,
        MULTI_SESSION_BEST_CANDIDATE,
    )

    // --- Track geometry (fixed) -------------------------------------------

    private const val BASE_LAT = 39.8121
    private const val BASE_LON = -86.1062
    private const val M_PER_DEG = 111_320.0
    private val M_PER_DEG_LON = M_PER_DEG * cos(BASE_LAT * PI / 180.0)

    private const val SEMI_MAJOR_M = 200.0 // east radius
    private const val SEMI_MINOR_M = 130.0 // north radius
    private const val POINTS_PER_LOOP = 240
    private const val LOOP_MILLIS = 24_000L
    private const val ALTITUDE_M = 219.0
    private const val CLEAN_ACCURACY_M = 4.5

    private fun lat(northMeters: Double): Double = BASE_LAT + northMeters / M_PER_DEG
    private fun lon(eastMeters: Double): Double = BASE_LON + eastMeters / M_PER_DEG_LON

    /**
     * A minimal deterministic linear-congruential generator. Seeded explicitly
     * per scenario so noisy fixtures stay reproducible without any platform RNG.
     */
    private class Lcg(seed: Long) {
        private var state = seed * 6364136223846793005L + 1442695040888963407L

        /** Uniform double in [0, 1). */
        fun nextUnit(): Double {
            state = state * 6364136223846793005L + 1442695040888963407L
            val bits = (state ushr 11) and 0x1F_FFFF_FFFF_FFFFL // 53 bits
            return bits.toDouble() / (1L shl 53).toDouble()
        }

        /** Symmetric noise in [-magnitude, +magnitude). */
        fun nextNoise(magnitude: Double): Double = (nextUnit() - 0.5) * 2.0 * magnitude
    }

    /**
     * Generate [loops] continuous oval loops as a single capture session.
     *
     * @param pointsPerLoop samples per loop (lower = lower frequency).
     * @param loopMillis time for one loop (controls speed and lap duration).
     * @param accuracyBase nominal horizontal accuracy in meters.
     * @param accuracyJitter +/- jitter applied to accuracy.
     * @param positionNoiseMeters +/- positional noise applied to each sample.
     * @param driftMetersPerSecond slow one-directional drift accumulated over time.
     * @param seed RNG seed for all noise.
     * @param keepEveryFraction probability in (0,1] that a sample is kept
     *   (models dropped/low-frequency capture); 1.0 keeps all.
     */
    private fun ovalSession(
        loops: Int,
        pointsPerLoop: Int = POINTS_PER_LOOP,
        loopMillis: Long = LOOP_MILLIS,
        accuracyBase: Double = CLEAN_ACCURACY_M,
        accuracyJitter: Double = 0.8,
        positionNoiseMeters: Double = 0.0,
        driftMetersPerSecond: Double = 0.0,
        seed: Long = 1L,
        keepEveryFraction: Double = 1.0,
    ): List<LocationSample> {
        val rng = Lcg(seed)
        val stepMillis = loopMillis / pointsPerLoop
        val omega = 2.0 * PI / (loopMillis / 1_000.0) // rad/sec
        val total = loops * pointsPerLoop
        val out = ArrayList<LocationSample>(total)
        for (g in 0 until total) {
            // Drop sampling decision first so seed advances deterministically.
            val keep = keepEveryFraction >= 1.0 || rng.nextUnit() < keepEveryFraction
            val elapsedMillis = g.toLong() * stepMillis
            if (!keep) continue

            val t = (g % pointsPerLoop).toDouble() / pointsPerLoop
            val angle = 2.0 * PI * t
            val elapsedSeconds = elapsedMillis / 1_000.0
            val drift = driftMetersPerSecond * elapsedSeconds

            val east = SEMI_MAJOR_M * sin(angle) + drift +
                if (positionNoiseMeters > 0) rng.nextNoise(positionNoiseMeters) else 0.0
            val north = SEMI_MINOR_M * cos(angle) +
                if (positionNoiseMeters > 0) rng.nextNoise(positionNoiseMeters) else 0.0

            val velEast = SEMI_MAJOR_M * cos(angle) * omega
            val velNorth = -SEMI_MINOR_M * sin(angle) * omega
            val speed = sqrt(velEast * velEast + velNorth * velNorth)
            val heading = (atan2(velEast, velNorth) * 180.0 / PI + 360.0) % 360.0
            val accuracy = accuracyBase +
                if (accuracyJitter > 0) kotlin.math.abs(rng.nextNoise(accuracyJitter)) else 0.0

            out += LocationSample(
                elapsedMillis = elapsedMillis,
                latitude = lat(north),
                longitude = lon(east),
                horizontalAccuracyMeters = accuracy,
                speedMetersPerSecond = speed,
                headingDegrees = heading,
                altitudeMeters = ALTITUDE_M,
                source = LocationSource.Simulated,
            )
        }
        return out
    }

    /**
     * Inject one bad/outlier loop into an otherwise clean trace: a contiguous
     * span is jumped far off-track with degraded accuracy (D-04 outlier case).
     */
    private fun withOutlierLoop(samples: List<LocationSample>): List<LocationSample> {
        if (samples.isEmpty()) return samples
        val start = (samples.size * 5) / 10
        val end = minOf(samples.size, start + POINTS_PER_LOOP / 3)
        return samples.mapIndexed { index, sample ->
            if (index in start until end) {
                sample.copy(
                    latitude = sample.latitude + 300.0 / M_PER_DEG,
                    longitude = sample.longitude + 280.0 / M_PER_DEG_LON,
                    horizontalAccuracyMeters = 48.0,
                    speedMetersPerSecond = (sample.speedMetersPerSecond ?: 0.0) + 22.0,
                )
            } else {
                sample
            }
        }
    }

    // --- Scenario builders -------------------------------------------------

    /** Clean 10-loop marking/timing input (D-04). */
    fun cleanTenLoop(): List<LocationSample> = ovalSession(loops = 10, seed = 101L)

    /** Minimum 5-loop input (D-04). */
    fun minimumFiveLoop(): List<LocationSample> = ovalSession(loops = 5, seed = 202L)

    /** Clean trace with one bad/outlier loop (D-04). */
    fun oneOutlierLoop(): List<LocationSample> =
        withOutlierLoop(ovalSession(loops = 10, seed = 303L))

    /** GPS noise and slow drift (D-04). */
    fun noiseDrift(): List<LocationSample> = ovalSession(
        loops = 8,
        accuracyBase = 9.0,
        accuracyJitter = 6.0,
        positionNoiseMeters = 8.0,
        driftMetersPerSecond = 0.05,
        seed = 404L,
    )

    /** Dropped / low-frequency samples (D-04). */
    fun droppedLowFrequency(): List<LocationSample> = ovalSession(
        loops = 6,
        pointsPerLoop = 12,
        accuracyBase = 12.0,
        accuracyJitter = 5.0,
        seed = 505L,
        keepEveryFraction = 0.65,
    )

    /**
     * Multiple sessions under one track for fastest-lap selection (D-04). The
     * middle session runs the loops fastest, so a later best-lap selector has a
     * clear winner across sessions.
     */
    fun multiSessionBestCandidate(): List<List<LocationSample>> = listOf(
        ovalSession(loops = 6, loopMillis = 26_000L, seed = 606L),
        ovalSession(loops = 6, loopMillis = 22_000L, seed = 707L),
        ovalSession(loops = 6, loopMillis = 25_000L, seed = 808L),
    )

    /**
     * Provider-layer UAT trace for ghost/live-delta work (D-20..D-24).
     *
     * The loop geometry is the same deterministic oval used by the other
     * fixtures, but lap durations intentionally vary:
     *
     * - 24s baseline reference
     * - 27s slower lap -> positive delta
     * - 22s faster lap -> negative delta
     * - 20s in-session new best -> immediate reference update
     * - 23s lap chasing the updated in-session best
     *
     * A final top-anchor sample closes the last loop so tests and UAT can
     * derive exact loop durations from repeated GPS positions.
     */
    fun variablePaceGhostUat(): List<LocationSample> = ovalVariablePaceSession(
        loopMillis = listOf(24_000L, 27_000L, 22_000L, 20_000L, 23_000L),
        seed = 909L,
    )

    /**
     * Stable rectangular course for matcher excursion/rematch and backward-motion
     * replay. The start/finish anchor is the first point and the recorded direction
     * follows the bottom edge eastward.
     */
    fun courseMatchReferenceLine(): TrackReferenceLine = referenceLine(
        0.0 to 0.0,
        120.0 to 0.0,
        120.0 to 80.0,
        0.0 to 80.0,
    )

    /**
     * Thin parallel-sided loop whose center is equally close to two non-adjacent
     * straights. The single sample must be treated as ambiguous, not guessed.
     */
    fun courseMatchAmbiguityReferenceLine(): TrackReferenceLine = referenceLine(
        0.0 to 0.0,
        120.0 to 0.0,
        120.0 to 8.0,
        0.0 to 8.0,
    )

    fun courseMatchAmbiguity(): List<LocationSample> = listOf(
        courseMatchSample(elapsedMillis = 0L, eastMeters = 60.0, northMeters = 4.0),
    )

    /** Matched -> off-course -> matched, with timestamps/speed suitable for rematch continuity. */
    fun courseMatchRecovery(): List<LocationSample> = listOf(
        courseMatchSample(elapsedMillis = 0L, eastMeters = 10.0, northMeters = 0.0),
        courseMatchSample(elapsedMillis = 1_000L, eastMeters = 20.0, northMeters = 0.0),
        courseMatchSample(elapsedMillis = 2_000L, eastMeters = 20.0, northMeters = -80.0),
        courseMatchSample(elapsedMillis = 3_000L, eastMeters = 30.0, northMeters = 0.0),
    )

    /** Forward progress followed by real backward movement on the same course segment. */
    fun courseMatchBackward(): List<LocationSample> = listOf(
        courseMatchSample(elapsedMillis = 0L, eastMeters = 20.0, northMeters = 0.0),
        courseMatchSample(elapsedMillis = 1_000L, eastMeters = 30.0, northMeters = 0.0),
        courseMatchSample(elapsedMillis = 2_000L, eastMeters = 25.0, northMeters = 0.0),
    )

    private fun referenceLine(vararg localMeters: Pair<Double, Double>): TrackReferenceLine =
        TrackReferenceLine(
            points = localMeters.map { (east, north) ->
                GeoPointDto(latitude = lat(north), longitude = lon(east))
            },
            isClosed = true,
        )

    private fun courseMatchSample(
        elapsedMillis: Long,
        eastMeters: Double,
        northMeters: Double,
        accuracyMeters: Double = CLEAN_ACCURACY_M,
        speedMetersPerSecond: Double = 10.0,
        source: LocationSource = LocationSource.Simulated,
    ): LocationSample = LocationSample(
        elapsedMillis = elapsedMillis,
        latitude = lat(northMeters),
        longitude = lon(eastMeters),
        horizontalAccuracyMeters = accuracyMeters,
        speedMetersPerSecond = speedMetersPerSecond,
        headingDegrees = 90.0,
        altitudeMeters = ALTITUDE_M,
        source = source,
    )

    private fun ovalVariablePaceSession(
        loopMillis: List<Long>,
        accuracyBase: Double = CLEAN_ACCURACY_M,
        accuracyJitter: Double = 0.8,
        seed: Long = 1L,
    ): List<LocationSample> {
        require(loopMillis.isNotEmpty()) { "variable-pace fixture must contain at least one loop" }
        val rng = Lcg(seed)
        val out = ArrayList<LocationSample>(loopMillis.size * POINTS_PER_LOOP + 1)
        var loopStartMillis = 0L

        loopMillis.forEach { durationMillis ->
            val omega = 2.0 * PI / (durationMillis / 1_000.0)
            for (point in 0 until POINTS_PER_LOOP) {
                val elapsedMillis = loopStartMillis + durationMillis * point / POINTS_PER_LOOP
                val angle = 2.0 * PI * point.toDouble() / POINTS_PER_LOOP
                out += ovalSample(
                    elapsedMillis = elapsedMillis,
                    angle = angle,
                    omega = omega,
                    accuracyMeters = accuracyBase +
                        if (accuracyJitter > 0) {
                            kotlin.math.abs(rng.nextNoise(accuracyJitter))
                        } else {
                            0.0
                        },
                )
            }
            loopStartMillis += durationMillis
        }

        val lastOmega = 2.0 * PI / (loopMillis.last() / 1_000.0)
        out += ovalSample(
            elapsedMillis = loopStartMillis,
            angle = 0.0,
            omega = lastOmega,
            accuracyMeters = accuracyBase +
                if (accuracyJitter > 0) {
                    kotlin.math.abs(rng.nextNoise(accuracyJitter))
                } else {
                    0.0
                },
        )
        return out
    }

    private fun ovalSample(
        elapsedMillis: Long,
        angle: Double,
        omega: Double,
        accuracyMeters: Double,
    ): LocationSample {
        val east = SEMI_MAJOR_M * sin(angle)
        val north = SEMI_MINOR_M * cos(angle)
        val velEast = SEMI_MAJOR_M * cos(angle) * omega
        val velNorth = -SEMI_MINOR_M * sin(angle) * omega
        val speed = sqrt(velEast * velEast + velNorth * velNorth)
        val heading = (atan2(velEast, velNorth) * 180.0 / PI + 360.0) % 360.0
        return LocationSample(
            elapsedMillis = elapsedMillis,
            latitude = lat(north),
            longitude = lon(east),
            horizontalAccuracyMeters = accuracyMeters,
            speedMetersPerSecond = speed,
            headingDegrees = heading,
            altitudeMeters = ALTITUDE_M,
            source = LocationSource.Simulated,
        )
    }

    /** Build the [GpsFixtureScenario] for a stable [id], or fail fast. */
    fun scenario(id: String): GpsFixtureScenario = when (id) {
        CLEAN_10_LOOP -> GpsFixtureScenario(
            id = id,
            name = "Clean 10-loop",
            description = "Ten clean continuous loops for track marking and timing.",
            sessions = listOf(cleanTenLoop()),
        )
        MINIMUM_5_LOOP -> GpsFixtureScenario(
            id = id,
            name = "Minimum 5-loop",
            description = "Five clean loops: the minimum marking input.",
            sessions = listOf(minimumFiveLoop()),
        )
        ONE_OUTLIER_LOOP -> GpsFixtureScenario(
            id = id,
            name = "One outlier loop",
            description = "Ten loops with one bad off-track, low-accuracy loop.",
            sessions = listOf(oneOutlierLoop()),
        )
        NOISE_DRIFT -> GpsFixtureScenario(
            id = id,
            name = "Noise and drift",
            description = "Eight loops with positional noise and slow GPS drift.",
            sessions = listOf(noiseDrift()),
        )
        DROPPED_LOW_FREQUENCY -> GpsFixtureScenario(
            id = id,
            name = "Dropped / low-frequency",
            description = "Sparse, irregular low-frequency capture with dropouts.",
            sessions = listOf(droppedLowFrequency()),
        )
        MULTI_SESSION_BEST_CANDIDATE -> GpsFixtureScenario(
            id = id,
            name = "Multi-session best candidate",
            description = "Three sessions on one track; the middle is fastest.",
            sessions = multiSessionBestCandidate(),
        )
        VARIABLE_PACE_GHOST_UAT -> GpsFixtureScenario(
            id = id,
            name = "Variable-pace ghost UAT",
            description = "Five simulated oval laps with slower, faster, and same-session new-best pacing for live ghost delta UAT.",
            sessions = listOf(variablePaceGhostUat()),
        )
        COURSE_MATCH_AMBIGUITY -> GpsFixtureScenario(
            id = id,
            name = "Course-match ambiguity",
            description = "A sample equally close to two non-adjacent parallel course segments.",
            sessions = listOf(courseMatchAmbiguity()),
        )
        COURSE_MATCH_RECOVERY -> GpsFixtureScenario(
            id = id,
            name = "Course-match recovery",
            description = "Matched samples around a temporary off-course excursion and automatic rematch.",
            sessions = listOf(courseMatchRecovery()),
        )
        COURSE_MATCH_BACKWARD -> GpsFixtureScenario(
            id = id,
            name = "Course-match backward movement",
            description = "Forward then backward movement on the same course segment without pausing timing.",
            sessions = listOf(courseMatchBackward()),
        )
        else -> throw IllegalArgumentException("Unknown GPS fixture scenario id: $id")
    }
}
