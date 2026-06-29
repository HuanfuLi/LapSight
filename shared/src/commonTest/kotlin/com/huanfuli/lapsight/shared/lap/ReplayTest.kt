package com.huanfuli.lapsight.shared.lap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Replay-level tests covering GPS-05 (replay a session through the engine) and
 * LAP-06 (engine runs from synthetic fixtures without UI/platform services).
 */
class ReplayTest {

    private fun runner(config: LapEngineConfig = ReplayFixtures.DEMO_CONFIG) =
        ReplayRunner(ReplayFixtures.DEMO_COURSE, config)

    @Test
    fun replayIsDeterministic() {
        val a = runner().run(ReplayFixtures.multiLapLoop())
        val b = runner().run(ReplayFixtures.multiLapLoop())
        // D-26/D-27: determinism covers the FULL algorithmic output, not just
        // finalState. The legacy assertion missed completed-lap and sector-result
        // drift entirely; assert the whole ordered state sequence, the completed
        // laps, and the ordered sector-event sequence are byte-identical.
        assertEquals(
            a.steps.map { it.state },
            b.steps.map { it.state },
            "the full ordered per-sample timing-state sequence must be identical (D-26)",
        )
        assertEquals(
            a.finalState.completedLaps,
            b.finalState.completedLaps,
            "the completed-lap list (numbers + exact start/end millis) must be identical",
        )
        assertEquals(
            a.sectorEvents,
            b.sectorEvents,
            "the ordered sector-event sequence must be identical (closes the finalState-only gap)",
        )
        assertEquals(a.finalState, b.finalState)
    }

    @Test
    fun simpleCrossingStartsTiming() {
        val result = runner().run(ReplayFixtures.simpleLineCrossing())
        assertEquals(LapPhase.Timing, result.finalState.phase)
        assertEquals(0, result.finalState.lapCount)
    }

    @Test
    fun oneLapLoopCompletesExactlyOneLap() {
        val result = runner().run(ReplayFixtures.oneLapLoop())
        assertEquals(1, result.finalState.lapCount)
        assertTrue(result.finalState.lastLapMillis != null)
        assertTrue(result.finalState.bestLapMillis != null)
    }

    @Test
    fun multiLapProducesExpectedCounters() {
        val durations = listOf(40_000L, 32_000L, 36_000L)
        val result = runner().run(ReplayFixtures.multiLapLoop(durations))

        assertEquals(3, result.finalState.lapCount)
        // best lap should be the shortest of the completed laps
        val best = result.finalState.bestLapMillis!!
        val completed = result.finalState.completedLaps.map { it.durationMillis }
        assertEquals(completed.min(), best)
        // last lap corresponds to the final completed lap
        assertEquals(completed.last(), result.finalState.lastLapMillis)
    }

    @Test
    fun jitterDoesNotCreateLaps() {
        val result = runner().run(ReplayFixtures.jitterNearLine())
        assertEquals(0, result.finalState.lapCount)
    }

    @Test
    fun lowFrequencySamplesStillDetectLaps() {
        val result = runner().run(ReplayFixtures.lowFrequencyLap())
        assertEquals(1, result.finalState.lapCount)
    }

    @Test
    fun wrongDirectionDoesNotCompleteLapAfterStart() {
        // First pass starts the lap; a later wrong-direction crossing must not
        // complete a lap. Using lenient-but-direction-enforced config.
        val config = ReplayFixtures.DEMO_CONFIG.copy(minLapDurationMillis = 0, crossingCooldownMillis = 0)
        val samples = ReplayFixtures.oneLapLoop() + ReplayFixtures.wrongDirectionCrossing()
        val result = ReplayRunner(ReplayFixtures.DEMO_COURSE, config).run(samples)
        // one valid lap from the loop, the wrong-direction extra pass rejected
        assertEquals(1, result.finalState.lapCount)
    }

    @Test
    fun poorAccuracyCrossingIsRejected() {
        val result = runner().run(ReplayFixtures.poorAccuracyCrossing())
        assertEquals(LapPhase.AwaitingStart, result.finalState.phase)
        assertEquals(LapRejectReason.PoorAccuracy, result.finalState.lastRejectReason)
    }

    @Test
    fun oneLapOneSectorRecordsSingleSectorSplit() {
        val result = runner().run(ReplayFixtures.oneLapOneSector())
        val crossed = result.finalState.sectors.filter { it.status == SectorStatus.Crossed }
        // After the lap completes, sectors reset; check the latest sector event
        // captured a split for S1 during the lap.
        val s1Events = result.sectorEvents.filter { it.sectorId == "S1" }
        assertTrue(s1Events.isNotEmpty())
        assertTrue(s1Events.first().splitMillis > 0)
    }

    @Test
    fun multiSectorFixtureProducesPerLapSplits() {
        val result = runner().run(ReplayFixtures.multiLapMultiSector())
        val s1 = result.sectorEvents.filter { it.sectorId == "S1" }
        val s2 = result.sectorEvents.filter { it.sectorId == "S2" }
        // At least one split each across the multi-lap replay.
        assertTrue(s1.isNotEmpty())
        assertTrue(s2.isNotEmpty())
        // Sector 2 split is later in the lap than sector 1 for the same lap.
        val lap1S1 = s1.firstOrNull { it.lapNumber == 1 }
        val lap1S2 = s2.firstOrNull { it.lapNumber == 1 }
        if (lap1S1 != null && lap1S2 != null) {
            assertTrue(lap1S2.splitMillis > lap1S1.splitMillis)
        }
    }
}
