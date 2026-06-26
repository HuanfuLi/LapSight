package com.huanfuli.lapsight.shared.fixtures

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.LocationSource
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Wave 0 coverage for the Phase 4 variable-pace ghost UAT fixture.
 *
 * This fixture is deliberately provider-layer data only: a deterministic
 * lat/lon time series that can be replayed through [SimulatedGpsProvider] and
 * the normal TimingSession path later. It must make slower positive delta,
 * faster negative delta, and in-session new-best reference update observable
 * without physically driving/riding laps (D-20..D-24).
 */
class GhostVariablePaceFixtureTest {

    @Test
    fun variablePaceScenarioHasStableIdAndSimulatedSource() {
        val scenario = GpsFixtureLibrary.scenario(GpsFixtureLibrary.VARIABLE_PACE_GHOST_UAT)
        val again = GpsFixtureLibrary.scenario(GpsFixtureLibrary.VARIABLE_PACE_GHOST_UAT)

        assertEquals("variable-pace-ghost-uat", scenario.id)
        assertEquals(scenario.samples, again.samples, "fixture must be deterministic across calls")
        assertTrue(scenario.samples.isNotEmpty(), "scenario must contain replay samples")
        assertTrue(
            scenario.samples.all { it.source == LocationSource.Simulated },
            "all UAT ghost samples must be visibly simulated (D-24)",
        )
    }

    @Test
    fun variablePaceScenarioCarriesSlowerFasterAndNewBestLoops() {
        val samples = GpsFixtureLibrary.scenario(GpsFixtureLibrary.VARIABLE_PACE_GHOST_UAT).samples
        val topCrossingTimes = repeatedTopCrossingTimes(samples)
        val loopDurations = topCrossingTimes.zipWithNext { a, b -> b - a }

        assertEquals(
            listOf(24_000L, 27_000L, 22_000L, 20_000L, 23_000L),
            loopDurations,
            "loop durations must include slower, faster, and new-best laps for deterministic UAT",
        )
        assertTrue(loopDurations[1] > loopDurations[0], "lap 2 should be slower than the first reference")
        assertTrue(loopDurations[2] < loopDurations[0], "lap 3 should be faster than the first reference")
        assertEquals(loopDurations[3], loopDurations.minOrNull(), "lap 4 should become the new best")
        assertTrue(loopDurations[4] > loopDurations[3], "lap 5 should chase the updated in-session best")
    }

    @Test
    fun variablePaceScenarioFormsContinuousLoopGeometry() {
        val samples = GpsFixtureLibrary.scenario(GpsFixtureLibrary.VARIABLE_PACE_GHOST_UAT).samples

        for (i in 1 until samples.size) {
            assertTrue(
                samples[i].elapsedMillis > samples[i - 1].elapsedMillis,
                "variable-pace fixture timestamps must be strictly monotonic",
            )
        }

        val latSpread = samples.maxOf { it.latitude } - samples.minOf { it.latitude }
        val lonSpread = samples.maxOf { it.longitude } - samples.minOf { it.longitude }
        assertTrue(latSpread > 0.0001, "latitude must vary around the loop")
        assertTrue(lonSpread > 0.0001, "longitude must vary around the loop")

        val topPoints = repeatedTopSamples(samples)
        assertTrue(topPoints.size >= 6, "fixture must contain enough repeated loop anchors")
        val firstTop = topPoints.first()
        topPoints.drop(1).forEach { top ->
            assertTrue(abs(top.latitude - firstTop.latitude) < 1e-12)
            assertTrue(abs(top.longitude - firstTop.longitude) < 1e-12)
        }
    }

    private fun repeatedTopCrossingTimes(samples: List<LocationSample>): List<Long> =
        repeatedTopSamples(samples).map { it.elapsedMillis }

    private fun repeatedTopSamples(samples: List<LocationSample>): List<LocationSample> {
        val maxLatitude = samples.maxOf { it.latitude }
        return samples.filter { abs(it.latitude - maxLatitude) < 1e-12 }
    }
}
