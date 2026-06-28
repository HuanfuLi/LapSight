package com.huanfuli.lapsight.shared.lap

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.lap.LapTestSupport.sample
import com.huanfuli.lapsight.shared.lap.LapTestSupport.verticalSector
import com.huanfuli.lapsight.shared.lap.LapTestSupport.verticalStartFinish
import com.huanfuli.lapsight.shared.track.CourseDirection
import com.huanfuli.lapsight.shared.track.CourseGeometryBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Replay gate for selectable Recorded / Reverse course direction (SC-03 / SC-04,
 * D-17, D-18, D-20, D-21).
 *
 * Direction is a configuration over ONE physical revision, not a global
 * wrong-direction state machine: Recorded and Reverse reuse the identical physical
 * anchors but invert progress, Sector order, and every boundary's endpoint
 * orientation, and they accept the OPPOSITE physical crossing direction. The
 * engine takes an explicit accepted approach side from course construction, so the
 * very first start/finish crossing in the wrong direction is rejected (no
 * learned-first-crossing semantics), and a mid-lap turnaround never fabricates a
 * short lap/Sector nor pauses timing.
 *
 * The base course is built forward-oriented (a recorded eastbound crossing of the
 * vertical start/finish at x=0 yields a positive signed side), exactly the
 * orientation `CourseGeometryBuilder` and the demo course produce.
 */
class CourseDirectionReplayTest {

    private val lenient = LapEngineConfig.lenientForTests()

    /** Recorded-oriented base course: vertical start/finish at x=0, N-1 boundaries east of it. */
    private fun baseCourse(n: Int): CourseDefinition {
        val boundaries = (0 until n - 1).map { i ->
            verticalSector("S${i + 1}", "Sector ${i + 1}", order = i, eastMeters = 50.0 * (i + 1))
        }
        return CourseDefinition(verticalStartFinish(), boundaries)
    }

    private fun runLap(course: CourseDefinition, samples: List<LocationSample>): LapTimingState {
        val engine = LapEngine(course, lenient)
        var state = engine.state
        for (s in samples) state = engine.onSample(s)
        return state
    }

    // --- D-18: Recorded and Reverse are distinct ordered configs over one revision

    @Test
    fun recordedAndReverseShareAnchorsButProduceDistinctOrderedDefinitions() {
        val base = baseCourse(3) // boundaries at x=50 (order 0) and x=100 (order 1)
        val recorded = CourseGeometryBuilder.directionalCourse(base, CourseDirection.Recorded)
        val reverse = CourseGeometryBuilder.directionalCourse(base, CourseDirection.Reverse)

        // Both carry an explicit accepted approach side (no learned-first-crossing).
        assertNotNull(recorded.acceptedStartFinishSign, "recorded must declare an accepted side")
        assertNotNull(reverse.acceptedStartFinishSign, "reverse must declare an accepted side")

        // Identical physical anchors: the same set of boundary longitudes appears in both.
        val recordedLons = recorded.orderedSectors.map { it.pointA.longitude }.toSet()
        val reverseLons = reverse.orderedSectors.map { it.pointA.longitude }.toSet()
        assertEquals(recordedLons, reverseLons, "Reverse reuses the recorded physical anchors")

        // Recorded order walks boundaries west->east (x=50 then x=100); Reverse inverts it.
        val recordedFirstLon = recorded.orderedSectors.first().pointA.longitude
        val reverseFirstLon = reverse.orderedSectors.first().pointA.longitude
        assertTrue(
            reverseFirstLon > recordedFirstLon,
            "Reverse crosses the physically-far boundary first (reversed Sector order, D-11)",
        )

        // Start/finish endpoints are swapped (every boundary orientation is reversed, D-11).
        assertEquals(
            recorded.startFinish.pointA,
            reverse.startFinish.pointB,
            "Reverse swaps the start/finish endpoints",
        )
        assertEquals(
            recorded.startFinish.pointB,
            reverse.startFinish.pointA,
            "Reverse swaps the start/finish endpoints",
        )
    }

    // --- D-21 / SC-04: explicit accepted side rejects the FIRST opposite crossing

    @Test
    fun recordedRejectsOppositeFirstCrossingThenAcceptsForward() {
        val recorded = CourseGeometryBuilder.directionalCourse(baseCourse(2), CourseDirection.Recorded)
        val engine = LapEngine(recorded, lenient)

        // First physical crossing is WESTBOUND (opposite the recorded direction).
        engine.onSample(sample(0, eastMeters = 10.0))
        val rejected = engine.onSample(sample(1_000, eastMeters = -10.0))
        assertEquals(LapPhase.AwaitingStart, rejected.phase, "an opposite first crossing must not start timing")
        assertEquals(LapRejectReason.WrongDirection, rejected.lastRejectReason)
        assertEquals(0, rejected.lapCount)

        // A subsequent EASTBOUND (recorded-direction) crossing starts the lap.
        engine.onSample(sample(2_000, eastMeters = -10.0))
        val started = engine.onSample(sample(3_000, eastMeters = 10.0))
        assertEquals(LapPhase.Timing, started.phase, "the recorded-direction crossing starts timing")
        assertEquals(1, started.currentLapNumber)
    }

    @Test
    fun reverseFlipsAcceptedDirection() {
        val reverse = CourseGeometryBuilder.directionalCourse(baseCourse(2), CourseDirection.Reverse)
        val engine = LapEngine(reverse, lenient)

        // For Reverse, an EASTBOUND (recorded-direction) crossing is now the wrong way.
        engine.onSample(sample(0, eastMeters = -10.0))
        val rejected = engine.onSample(sample(1_000, eastMeters = 10.0))
        assertEquals(LapPhase.AwaitingStart, rejected.phase, "Reverse rejects the recorded-direction crossing")
        assertEquals(LapRejectReason.WrongDirection, rejected.lastRejectReason)

        // A WESTBOUND crossing (the reverse direction) starts the lap.
        engine.onSample(sample(2_000, eastMeters = 10.0))
        val started = engine.onSample(sample(3_000, eastMeters = -10.0))
        assertEquals(LapPhase.Timing, started.phase, "Reverse accepts the reverse-direction crossing")
    }

    // --- D-18 / D-21: same physical replay, only direction decides lap completion

    /**
     * A full reverse lap: the car traverses the recorded loop backwards, crossing
     * the physically-far boundary (x=100) before the near one (x=50) and crossing
     * start/finish heading WEST. Boundaries only span y in [-20, 20], so the south
     * (y=-40) return leg is clear of every line segment.
     */
    private fun reverseCleanLap(): List<LocationSample> = listOf(
        sample(0, eastMeters = 10.0),                                  // east of start/finish
        sample(1_000, eastMeters = -10.0),                            // cross x=0 WEST -> opens lap
        sample(2_000, eastMeters = -10.0, northMeters = -40.0),       // drop south, clear of lines
        sample(3_000, eastMeters = 150.0, northMeters = -40.0),       // run east, south of all boundaries
        sample(4_000, eastMeters = 150.0, northMeters = 0.0),         // rise north, east of all boundaries
        sample(5_000, eastMeters = 90.0, northMeters = 0.0),          // cross x=100 WEST -> reverse Sector 1
        sample(6_000, eastMeters = 40.0, northMeters = 0.0),          // cross x=50 WEST -> reverse Sector 2
        sample(7_000, eastMeters = -10.0, northMeters = 0.0),         // cross x=0 WEST -> closes lap
    )

    @Test
    fun reverseCompletesLapWithReversedSectorOrderWhileRecordedRejectsSameReplay() {
        val reverse = CourseGeometryBuilder.directionalCourse(baseCourse(3), CourseDirection.Reverse)
        val recorded = CourseGeometryBuilder.directionalCourse(baseCourse(3), CourseDirection.Recorded)
        val samples = reverseCleanLap()

        val reverseState = runLap(reverse, samples)
        assertEquals(1, reverseState.lapCount, "reverse-direction replay completes a lap on the Reverse config")
        val results = reverseState.completedSectorResults.filter { it.lapNumber == 1 }
        assertEquals(3, results.size, "a fully observed reverse lap yields exactly N complete Sectors")
        assertEquals(
            listOf(1, 2, 3),
            results.map { it.sectorOrder },
            "Sectors are recorded in reversed traversal order beginning at start/finish (D-11)",
        )

        // The IDENTICAL physical replay never completes a lap on the Recorded config:
        // every start/finish crossing here is westbound, which Recorded rejects (D-21).
        val recordedState = runLap(recorded, samples)
        assertEquals(0, recordedState.lapCount, "the same replay completes no lap under the Recorded config")
    }

    // --- D-17 / D-20: a turnaround creates no false lap/Sector and never pauses ----

    @Test
    fun turnaroundCreatesNoFalseLapOrSectorAndKeepsTimingRunning() {
        val recorded = CourseGeometryBuilder.directionalCourse(baseCourse(2), CourseDirection.Recorded)
        val engine = LapEngine(recorded, lenient)

        // Open lap 1 eastbound (crossing x=0 at t=500).
        engine.onSample(sample(0, eastMeters = -10.0))
        engine.onSample(sample(1_000, eastMeters = 10.0))
        // Cross the boundary at x=50 eastbound -> closes reverse... recorded Sector 1.
        val afterBoundary = engine.onSample(sample(2_000, eastMeters = 60.0))
        val elapsedBeforeTurn = afterBoundary.currentLapElapsedMillis
        assertNotNull(elapsedBeforeTurn)
        assertTrue(elapsedBeforeTurn > 0)
        val sectorsAfterBoundary = afterBoundary.completedSectorResults.size

        // Turnaround: re-cross the boundary heading WEST (backward; order gate ignores it)...
        engine.onSample(sample(3_000, eastMeters = 40.0))
        // ...then re-cross start/finish heading WEST (opposite): rejected, NO false lap.
        val afterTurn = engine.onSample(sample(4_000, eastMeters = -10.0))
        assertEquals(0, afterTurn.lapCount, "a wrong-way turnaround must not complete a lap (D-21)")
        assertEquals(LapPhase.Timing, afterTurn.phase, "timing continues through the turnaround (D-17)")
        assertEquals(LapRejectReason.WrongDirection, afterTurn.lastRejectReason)
        assertEquals(
            sectorsAfterBoundary,
            afterTurn.completedSectorResults.size,
            "the backward boundary re-cross adds no Sector result (D-20)",
        )
        // Timing was never paused/reset: elapsed kept growing across the turnaround.
        assertTrue(
            afterTurn.currentLapElapsedMillis!! > elapsedBeforeTurn,
            "the running clock is never paused by a turnaround (D-17)",
        )

        // Resuming the recorded direction completes the lap normally.
        val completed = engine.onSample(sample(5_000, eastMeters = 10.0))
        assertEquals(1, completed.lapCount, "a real recorded-direction crossing still completes the lap")
    }

    // --- Direction-relative progress transform (D-11/D-18) -------------------------

    @Test
    fun directionalProgressMirrorsAroundStartFinish() {
        val l = 400.0
        val s0 = 100.0
        // Recorded progress is wrap(s - s0); Reverse progress is wrap(s0 - s).
        assertEquals(
            50.0,
            CourseGeometryBuilder.directionalProgress(CourseDirection.Recorded, s = 150.0, startProgress = s0, perimeter = l),
            absoluteTolerance = 1e-9,
        )
        assertEquals(
            l - 50.0,
            CourseGeometryBuilder.directionalProgress(CourseDirection.Reverse, s = 150.0, startProgress = s0, perimeter = l),
            absoluteTolerance = 1e-9,
        )
        // At the start/finish anchor both directions read zero progress.
        assertEquals(
            0.0,
            CourseGeometryBuilder.directionalProgress(CourseDirection.Recorded, s = s0, startProgress = s0, perimeter = l),
            absoluteTolerance = 1e-9,
        )
        assertEquals(
            0.0,
            CourseGeometryBuilder.directionalProgress(CourseDirection.Reverse, s = s0, startProgress = s0, perimeter = l),
            absoluteTolerance = 1e-9,
        )
    }
}
