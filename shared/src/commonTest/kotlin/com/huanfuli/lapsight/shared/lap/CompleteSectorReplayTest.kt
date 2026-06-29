package com.huanfuli.lapsight.shared.lap

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.lap.LapTestSupport.sample
import com.huanfuli.lapsight.shared.lap.LapTestSupport.verticalSector
import com.huanfuli.lapsight.shared.lap.LapTestSupport.verticalStartFinish
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Replay gate for complete, ordered Sector intervals (SC-02 / D-06, D-07, D-11,
 * D-20).
 *
 * Unlike the legacy line-centric [SectorEvent] (a single cumulative split per
 * sector LINE), the engine now derives N complete adjacent INTERVALS from N-1
 * intermediate boundaries plus the start/finish closure:
 *
 * ```
 * Start/Finish -> [Sector 1] -> B1 -> [Sector 2] -> ... -> [Sector N] -> Start/Finish
 * ```
 *
 * These assertions fail under the old `SectorEvent.splitMillis`-only model and
 * pin the new [SectorResult] contract: adjacent durations, separate cumulative
 * Split, final-interval closure at the lap crossing, strict expected order
 * (duplicate/out-of-order/opposite rejected), and incomplete coverage that never
 * blocks lap timing.
 */
class CompleteSectorReplayTest {

    private val lenient = LapEngineConfig.lenientForTests()

    /** Course with [n] complete Sectors = [n] - 1 evenly spaced intermediate boundaries. */
    private fun courseForSectors(n: Int): CourseDefinition {
        val boundaries = (0 until n - 1).map { i ->
            verticalSector("S${i + 1}", "Sector ${i + 1}", order = i, eastMeters = 50.0 * (i + 1))
        }
        return CourseDefinition(verticalStartFinish(), boundaries)
    }

    /**
     * A single fully observed lap: open across start/finish, cross every
     * intermediate boundary in order heading east, loop south clear of all line
     * segments, and re-cross start/finish to close the final Sector and the lap.
     */
    private fun cleanLapSamples(n: Int): List<LocationSample> {
        val m = n - 1
        val samples = mutableListOf<LocationSample>()
        samples += sample(0, eastMeters = -10.0)
        samples += sample(1_000, eastMeters = 10.0) // start/finish crossing at t=500
        for (i in 0 until m) {
            val t = 1_000L * (i + 2)
            samples += sample(t, eastMeters = 50.0 * (i + 1) + 10.0) // crosses boundary i at x=50*(i+1)
        }
        val lastEastTime = 1_000L * (m + 1)
        val lastX = 50.0 * m + 10.0
        samples += sample(lastEastTime + 1_000, eastMeters = lastX, northMeters = -40.0)
        samples += sample(lastEastTime + 2_000, eastMeters = -10.0, northMeters = -40.0)
        samples += sample(lastEastTime + 3_000, eastMeters = -10.0, northMeters = 0.0)
        samples += sample(lastEastTime + 4_000, eastMeters = 10.0, northMeters = 0.0) // closes lap
        return samples
    }

    private fun runLap(course: CourseDefinition, samples: List<LocationSample>): LapTimingState {
        val engine = LapEngine(course, lenient)
        var state = engine.state
        for (s in samples) state = engine.onSample(s)
        return state
    }

    // --- D-06/D-07: N complete intervals for N=2..6, none when disabled ---------

    @Test
    fun nConfiguredSectorsYieldExactlyNCompleteOrderedResults() {
        for (n in 2..6) {
            val course = courseForSectors(n)
            assertEquals(n, course.derivedSectors.size, "N=$n must derive exactly N complete sectors")

            val state = runLap(course, cleanLapSamples(n))
            assertEquals(1, state.lapCount, "N=$n: the lap must complete")

            val results = state.completedSectorResults.filter { it.lapNumber == 1 }
            assertEquals(n, results.size, "N=$n: a fully observed lap yields exactly N sector results")
            assertEquals(
                (1..n).toList(),
                results.map { it.sectorOrder },
                "N=$n: sector order begins at start/finish and is contiguous (D-11)",
            )
        }
    }

    @Test
    fun disabledSectorsYieldNoSectorResults() {
        val course = CourseDefinition(verticalStartFinish(), sectors = emptyList())
        assertTrue(course.derivedSectors.isEmpty(), "disabled sectors derive no intervals")

        // Drive a normal lap (no boundaries to cross).
        val samples = cleanLapSamples(1) // m=0: open + south loop + close
        val state = runLap(course, samples)

        assertEquals(1, state.lapCount, "lap still completes with sectors disabled")
        assertTrue(state.completedSectorResults.isEmpty(), "disabled sectors yield no results (D-07)")
    }

    // --- D-11: duration is adjacent difference; cumulative Split is separate ----

    @Test
    fun durationDiffersFromCumulativeSplitAfterSectorOne() {
        val n = 3
        val state = runLap(courseForSectors(n), cleanLapSamples(n))
        val results = state.completedSectorResults.filter { it.lapNumber == 1 }
        assertEquals(3, results.size)

        val s1 = results[0]
        assertEquals(
            s1.cumulativeSplitMillis,
            s1.durationMillis,
            "Sector 1 duration equals its cumulative split (it opens at the lap start)",
        )

        val s2 = results[1]
        assertTrue(
            s2.cumulativeSplitMillis > s2.durationMillis,
            "Sector 2 cumulative split exceeds its adjacent duration (D-11)",
        )
        assertEquals(
            s2.endedAtMillis - s2.startedAtMillis,
            s2.durationMillis,
            "duration is the adjacent-crossing difference",
        )
    }

    // --- D-06: final interval closes on the accepted start/finish crossing ------

    @Test
    fun finalSectorClosesOnStartFinishAtSameTimestampAsLap() {
        val n = 4
        val state = runLap(courseForSectors(n), cleanLapSamples(n))
        val results = state.completedSectorResults.filter { it.lapNumber == 1 }
        val lap = state.completedLaps.last()
        val finalSector = results.last()

        assertEquals(n, finalSector.sectorOrder, "the last result is Sector N")
        assertEquals(
            lap.endMillis,
            finalSector.endedAtMillis,
            "final Sector and lap close at the same interpolated start/finish timestamp (D-06)",
        )
        assertEquals(
            lap.durationMillis,
            finalSector.cumulativeSplitMillis,
            "the final cumulative split equals the full lap duration",
        )
    }

    // --- D-20: duplicate / out-of-order / opposite crossings do not advance -----

    @Test
    fun duplicateAndBackwardBoundaryCrossingsDoNotAddSectorResults() {
        // N=2 -> one intermediate boundary at x=50.
        val course = courseForSectors(2)
        val samples = mutableListOf<LocationSample>()
        samples += sample(0, eastMeters = -10.0)
        samples += sample(1_000, eastMeters = 10.0) // open at t=500
        samples += sample(2_000, eastMeters = 60.0) // cross boundary 0 -> closes Sector 1
        // Back-and-forth re-cross of the SAME (already passed) boundary: must not advance.
        samples += sample(3_000, eastMeters = 40.0) // westbound re-cross of x=50 (opposite/backward)
        samples += sample(4_000, eastMeters = 60.0) // eastbound duplicate re-cross of x=50
        // Loop south and close the lap (closes the final Sector 2).
        samples += sample(5_000, eastMeters = 60.0, northMeters = -40.0)
        samples += sample(6_000, eastMeters = -10.0, northMeters = -40.0)
        samples += sample(7_000, eastMeters = -10.0, northMeters = 0.0)
        samples += sample(8_000, eastMeters = 10.0, northMeters = 0.0) // close

        val state = runLap(course, samples)
        val results = state.completedSectorResults.filter { it.lapNumber == 1 }

        assertEquals(1, state.lapCount, "the lap completes despite the noisy re-crossings")
        assertEquals(
            2,
            results.size,
            "only the two in-order intervals are recorded; duplicate/backward crossings add none (D-20)",
        )
        assertEquals(listOf(1, 2), results.map { it.sectorOrder })
    }

    // --- D-20: a missed intermediate boundary leaves coverage unavailable -------

    @Test
    fun missedIntermediateBoundaryLeavesCoverageIncompleteButLapCompletes() {
        // N=3 -> boundaries at x=50 and x=100. Cross the first, miss the second.
        val course = courseForSectors(3)
        val samples = mutableListOf<LocationSample>()
        samples += sample(0, eastMeters = -10.0)
        samples += sample(1_000, eastMeters = 10.0) // open
        samples += sample(2_000, eastMeters = 60.0) // cross boundary 0 -> closes Sector 1
        // Detour south BEFORE reaching x=100, so boundary 1 is never crossed.
        samples += sample(3_000, eastMeters = 60.0, northMeters = -40.0)
        samples += sample(4_000, eastMeters = -10.0, northMeters = -40.0)
        samples += sample(5_000, eastMeters = -10.0, northMeters = 0.0)
        samples += sample(6_000, eastMeters = 10.0, northMeters = 0.0) // close lap

        val state = runLap(course, samples)
        val results = state.completedSectorResults.filter { it.lapNumber == 1 }

        assertEquals(1, state.lapCount, "lap timing continues through a missed boundary (D-20)")
        assertEquals(
            listOf(1),
            results.map { it.sectorOrder },
            "only Sector 1 is available; the missed boundary leaves Sectors 2..N uncovered",
        )
        assertTrue(
            results.size < course.derivedSectors.size,
            "coverage is explicitly incomplete (fewer than N results)",
        )
    }

    // --- D-25/D-26/D-27: completedSectorResults are deterministic across N runs --

    @Test
    fun completedSectorResultsAreIdenticalAcrossNRuns() {
        // Replay determinism is a hard gate (D-25): the same samples must yield the
        // SAME complete sector intervals every run. A finalState-only check would
        // miss adjacent-duration / cumulative-split / ordering drift, so assert the
        // whole completedSectorResults list (exact Long-millis) is identical across
        // N>=3 independent runs of a fresh engine over the same fixture (D-26/D-27).
        val n = 4
        val course = courseForSectors(n)
        val samples = cleanLapSamples(n)

        val runs = (1..3).map { runLap(course, samples).completedSectorResults }
        val baseline = runs.first()
        assertEquals(n, baseline.filter { it.lapNumber == 1 }.size, "the fixture must produce N sectors")
        runs.drop(1).forEachIndexed { index, results ->
            assertEquals(
                baseline,
                results,
                "run ${index + 2} produced different completedSectorResults than run 1 (determinism drift)",
            )
        }
    }

    // --- D-06/D-11: many crossings in one low-frequency segment stay ordered ----

    @Test
    fun multipleCrossingsInOneSegmentKeepInterpolationOrderingStable() {
        // N=3 -> boundaries at x=50, x=100. One segment crosses start/finish AND
        // both boundaries at once; results must come out in interpolated-time order.
        val course = courseForSectors(3)
        val samples = mutableListOf<LocationSample>()
        samples += sample(0, eastMeters = -10.0)
        samples += sample(1_000, eastMeters = 150.0) // crosses x=0, x=50, x=100 in one segment
        samples += sample(2_000, eastMeters = 150.0, northMeters = -40.0)
        samples += sample(3_000, eastMeters = -10.0, northMeters = -40.0)
        samples += sample(4_000, eastMeters = -10.0, northMeters = 0.0)
        samples += sample(5_000, eastMeters = 10.0, northMeters = 0.0) // close lap

        val state = runLap(course, samples)
        val results = state.completedSectorResults.filter { it.lapNumber == 1 }

        assertEquals(3, results.size, "all three intervals are observed across the lap")
        assertEquals(listOf(1, 2, 3), results.map { it.sectorOrder })
        // Strictly increasing closure timestamps prove stable interpolation ordering.
        val ends = results.map { it.endedAtMillis }
        assertEquals(ends.sorted(), ends, "sector closures are emitted in interpolated-time order")
        assertTrue(ends[0] < ends[1] && ends[1] < ends[2], "no two closures collapse out of order")
    }
}
