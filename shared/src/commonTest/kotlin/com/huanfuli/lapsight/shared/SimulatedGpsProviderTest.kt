package com.huanfuli.lapsight.shared

import com.huanfuli.lapsight.shared.fixtures.GpsFixtureLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wave 0 (RED) coverage for the provider-layer simulated GPS feed.
 *
 * Pins D-03 (the simulator only replaces the provider layer — no demo-only
 * business workflow), D-05 (a Drive-startable feed that flows continuously),
 * and D-42 (simulated source metadata). The provider must start/stop
 * independently of any track-marking or timing state.
 */
class SimulatedGpsProviderTest {

    @Test
    fun doesNotEmitBeforeStart() {
        val provider = SimulatedGpsProvider(GpsFixtureLibrary.CLEAN_10_LOOP)
        assertFalse(provider.isRunning)
        assertNull(provider.nextSample(), "no samples until the feed is started (D-03)")
    }

    @Test
    fun startsIndependentlyAndAdvancesContinuously() {
        val provider = SimulatedGpsProvider(GpsFixtureLibrary.CLEAN_10_LOOP)
        provider.start()
        assertTrue(provider.isRunning, "feed runs without any timing/marking session (D-03)")

        // Advances continuously while running, even beyond a single loop length (D-05).
        var previous = assertNotNull(provider.nextSample(), "running feed must emit a first sample")
        repeat(500) {
            val next = assertNotNull(provider.nextSample(), "running feed must emit continuously")
            assertTrue(
                next.elapsedMillis >= previous.elapsedMillis,
                "feed time must move forward",
            )
            assertEquals(LocationSource.Simulated, next.source, "feed is simulated (D-42)")
            previous = next
        }
    }

    @Test
    fun emitsNothingAfterStop() {
        val provider = SimulatedGpsProvider(GpsFixtureLibrary.CLEAN_10_LOOP)
        provider.start()
        assertNotNull(provider.nextSample())
        provider.stop()
        assertFalse(provider.isRunning)
        assertNull(provider.nextSample(), "no samples after the feed is stopped (D-03)")
    }

    @Test
    fun resetRewindsTheFeed() {
        val provider = SimulatedGpsProvider(GpsFixtureLibrary.CLEAN_10_LOOP)
        provider.start()
        val first = provider.nextSample()
        provider.nextSample()
        provider.reset()
        assertFalse(provider.isRunning, "reset stops the feed")
        provider.start()
        val afterReset = provider.nextSample()
        assertEquals(first, afterReset, "reset rewinds to the start of the feed")
    }

    @Test
    fun exposesActiveScenarioWithoutOwningTimingState() {
        val provider = SimulatedGpsProvider(GpsFixtureLibrary.MINIMUM_5_LOOP)
        assertEquals(GpsFixtureLibrary.MINIMUM_5_LOOP, provider.activeScenarioId)
    }

    @Test
    fun variablePaceGhostScenarioWrapsContinuouslyThroughProviderBoundary() {
        val provider = SimulatedGpsProvider(GpsFixtureLibrary.VARIABLE_PACE_GHOST_UAT)
        provider.start()

        val first = assertNotNull(provider.nextSample())
        var previous = first
        repeat(provider.sampleCount + 8) {
            val next = assertNotNull(provider.nextSample(), "variable-pace feed must emit continuously")
            assertTrue(
                next.elapsedMillis > previous.elapsedMillis,
                "wrapped variable-pace feed timestamps must keep moving forward",
            )
            assertEquals(LocationSource.Simulated, next.source)
            previous = next
        }
        assertTrue(
            previous.elapsedMillis > first.elapsedMillis,
            "wrapped samples must advance beyond the first cycle without starting a timing session",
        )
    }

    @Test
    fun isUsableThroughTheProviderBoundary() {
        // The simulator must be a normal LocationSampleProvider so later marking
        // and timing flows consume it unchanged (D-03).
        val provider: LocationSampleProvider = SimulatedGpsProvider(GpsFixtureLibrary.CLEAN_10_LOOP)
        provider.start()
        assertNotNull(provider.nextSample())
        provider.stop()
        assertNull(provider.nextSample())
    }
}
