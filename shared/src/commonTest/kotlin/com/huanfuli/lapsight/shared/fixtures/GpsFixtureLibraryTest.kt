package com.huanfuli.lapsight.shared.fixtures

import com.huanfuli.lapsight.shared.GpsQualitySummary
import com.huanfuli.lapsight.shared.LocationSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Wave 0 (RED) coverage for the deterministic GPS fixture library.
 *
 * These tests pin the contract for D-01 (deterministic mock), D-02 (realistic
 * `LocationSample` time series), D-04 (the six required scenarios), and D-42
 * (simulated source metadata). They use only `kotlin.test` and the shared
 * domain types — no Compose, Android, iOS, Okio, or serialization.
 */
class GpsFixtureLibraryTest {

    @Test
    fun cleanTenLoopIsDeterministicAndRealistic() {
        val a = GpsFixtureLibrary.cleanTenLoop()
        val b = GpsFixtureLibrary.cleanTenLoop()
        assertEquals(a, b, "fixtures must be deterministic across calls (D-01)")
        assertTrue(a.size >= 100, "clean 10-loop should be a dense multi-loop trace")

        // Monotonic, realistic timestamps (D-02).
        for (i in 1 until a.size) {
            assertTrue(
                a[i].elapsedMillis > a[i - 1].elapsedMillis,
                "elapsedMillis must be strictly monotonic",
            )
        }

        // Non-zero lat/lon movement around a closed loop (D-02).
        val latSpread = a.maxOf { it.latitude } - a.minOf { it.latitude }
        val lonSpread = a.maxOf { it.longitude } - a.minOf { it.longitude }
        assertTrue(latSpread > 0.0001, "latitude must vary across the loop")
        assertTrue(lonSpread > 0.0001, "longitude must vary across the loop")

        // Realistic metadata present and explicitly simulated (D-02/D-42).
        a.forEach { sample ->
            val speed = assertNotNull(sample.speedMetersPerSecond, "speed must be present")
            assertNotNull(sample.horizontalAccuracyMeters, "accuracy must be present")
            assertNotNull(sample.headingDegrees, "heading must be present")
            assertTrue(speed > 0.0, "moving feed has non-zero speed")
            assertEquals(LocationSource.Simulated, sample.source)
        }
    }

    @Test
    fun requiredScenariosExistByStableId() {
        val expected = listOf(
            "clean-10-loop",
            "minimum-5-loop",
            "one-outlier-loop",
            "noise-drift",
            "dropped-low-frequency",
            "multi-session-best-candidate",
        )
        assertEquals(
            expected.toSet(),
            GpsFixtureLibrary.requiredScenarioIds.toSet(),
            "all six D-04 scenarios must be advertised by stable id",
        )
        expected.forEach { id ->
            val scenario = GpsFixtureLibrary.scenario(id)
            assertEquals(id, scenario.id)
            assertTrue(scenario.samples.isNotEmpty(), "scenario $id must have samples")
            assertTrue(
                scenario.samples.all { it.source == LocationSource.Simulated },
                "scenario $id must be fully simulated (D-42)",
            )
        }
    }

    @Test
    fun multiSessionScenarioCarriesSeveralSessions() {
        val scenario = GpsFixtureLibrary.scenario("multi-session-best-candidate")
        assertTrue(
            scenario.sessions.size >= 2,
            "multi-session candidate must hold several sessions for fastest-lap selection (D-04)",
        )
        scenario.sessions.forEach { session ->
            assertTrue(session.isNotEmpty(), "each session must have samples")
            assertTrue(session.all { it.source == LocationSource.Simulated })
        }
    }

    @Test
    fun qualitySummaryRollsUpSimulatedMetadata() {
        val samples = GpsFixtureLibrary.cleanTenLoop()
        val summary = GpsQualitySummary.from(samples)
        assertEquals(samples.size, summary.sampleCount)
        assertTrue(summary.durationMillis > 0)
        assertTrue(summary.averageUpdateRateHz > 0.0)
        assertEquals(setOf(LocationSource.Simulated), summary.sources)
        val best = assertNotNull(summary.bestAccuracyMeters)
        val worst = assertNotNull(summary.worstAccuracyMeters)
        assertTrue(best <= worst)
    }

    @Test
    fun degradedSamplesAreCounted() {
        val outlier = GpsFixtureLibrary.scenario("one-outlier-loop").samples
        val summary = GpsQualitySummary.from(outlier)
        assertTrue(
            summary.degradedSampleCount > 0,
            "the outlier scenario must surface degraded samples for review/export",
        )
    }

    @Test
    fun emptySamplesProduceSafeSummary() {
        val summary = GpsQualitySummary.from(emptyList())
        assertEquals(0, summary.sampleCount)
        assertEquals(0L, summary.durationMillis)
        assertEquals(0.0, summary.averageUpdateRateHz)
        assertEquals(0, summary.degradedSampleCount)
        assertTrue(summary.sources.isEmpty())
    }
}
