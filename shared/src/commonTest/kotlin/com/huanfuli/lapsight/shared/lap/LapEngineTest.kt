package com.huanfuli.lapsight.shared.lap

import com.huanfuli.lapsight.shared.lap.LapTestSupport.sample
import com.huanfuli.lapsight.shared.lap.LapTestSupport.verticalSector
import com.huanfuli.lapsight.shared.lap.LapTestSupport.verticalStartFinish
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LapEngineTest {

    private val course = CourseDefinition(startFinish = verticalStartFinish())
    private val lenient = LapEngineConfig.lenientForTests()

    // The start/finish line is the finite segment x=0, y in [-20, 20].
    // To "loop" back without recrossing it, the return leg travels south of the
    // line (y = -40), so the westward movement does not intersect the segment.
    private fun returnSouth(elapsedMillis: Long, eastMeters: Double) =
        sample(elapsedMillis, eastMeters = eastMeters, northMeters = -40.0)

    @Test
    fun firstCrossingStartsTiming() {
        val engine = LapEngine(course, lenient)
        engine.onSample(sample(0, -10.0))
        val state = engine.onSample(sample(2_000, 10.0))

        assertEquals(LapPhase.Timing, state.phase)
        assertEquals(1, state.currentLapNumber)
        assertEquals(0, state.lapCount)
        assertEquals(1_000L, state.currentLapStartMillis)
    }

    @Test
    fun secondCrossingCompletesLap() {
        val engine = LapEngine(course, lenient)
        engine.onSample(sample(0, -10.0))
        engine.onSample(sample(2_000, 10.0))   // start at t=1000
        // loop back around the south end without recrossing the line
        engine.onSample(returnSouth(30_000, 10.0))
        engine.onSample(returnSouth(58_000, -10.0))
        engine.onSample(sample(60_000, -10.0)) // approach from west again
        val state = engine.onSample(sample(62_000, 10.0)) // crossing east at t=61000

        assertEquals(1, state.lapCount)
        assertTrue(state.lastLapMillis != null)
        assertEquals(2, state.currentLapNumber)
    }

    @Test
    fun pointToPointFinishCompletesOneRunWithoutOpeningNextLap() {
        val openCourse = CourseDefinition(
            startFinish = verticalStartFinish(),
            finishLine = StartFinishLine(
                pointA = GeoPoint(LapTestSupport.latForMetersNorth(-20.0), LapTestSupport.lonForMetersEast(100.0)),
                pointB = GeoPoint(LapTestSupport.latForMetersNorth(20.0), LapTestSupport.lonForMetersEast(100.0)),
            ),
        )
        val engine = LapEngine(openCourse, lenient)
        engine.onSample(sample(0, -10.0))
        engine.onSample(sample(2_000, 10.0)) // start at t=1000
        engine.onSample(sample(9_000, 90.0))
        val state = engine.onSample(sample(11_000, 110.0)) // finish at t=10000

        assertEquals(1, state.lapCount)
        assertTrue(state.lastLapMillis in 8_999L..9_000L)
        assertEquals(LapPhase.AwaitingStart, state.phase)
        assertNull(state.currentLapNumber)
        assertEquals(1, state.completedLaps.size)
    }

    @Test
    fun bestLapUpdatesOnlyWhenFaster() {
        val engine = LapEngine(course, lenient)
        // start lap1 at t=1000
        engine.onSample(sample(0, -10.0))
        engine.onSample(sample(2_000, 10.0))
        // loop south, complete lap1 (~60s) at t=61000
        engine.onSample(returnSouth(30_000, 10.0))
        engine.onSample(returnSouth(58_000, -10.0))
        engine.onSample(sample(60_000, -10.0))
        engine.onSample(sample(62_000, 10.0))
        val afterLap1 = engine.state
        val best1 = afterLap1.bestLapMillis
        // loop south, complete lap2 faster (~30s) at t=91000
        engine.onSample(returnSouth(75_000, 10.0))
        engine.onSample(returnSouth(88_000, -10.0))
        engine.onSample(sample(90_000, -10.0))
        engine.onSample(sample(92_000, 10.0))
        val afterLap2 = engine.state

        assertTrue(best1 != null)
        assertTrue(afterLap2.bestLapMillis!! < best1!!)
        assertEquals(2, afterLap2.lapCount)
    }

    @Test
    fun cooldownBlocksDuplicateCrossing() {
        val config = LapEngineConfig(
            minLapDurationMillis = 0,
            crossingCooldownMillis = 10_000,
            maxHorizontalAccuracyMeters = null,
            minSpeedMetersPerSecond = 0.0,
            enforceDirection = false,
        )
        val engine = LapEngine(course, config)
        engine.onSample(sample(0, -10.0))
        engine.onSample(sample(2_000, 10.0))   // start lap at t=1000
        // immediate re-cross within cooldown
        engine.onSample(sample(3_000, -10.0))
        val state = engine.onSample(sample(4_000, 10.0)) // crossing at ~3000, within cooldown

        assertEquals(0, state.lapCount)
        assertEquals(LapRejectReason.Cooldown, state.lastRejectReason)
    }

    @Test
    fun minLapDurationBlocksFalseLaps() {
        val config = LapEngineConfig(
            minLapDurationMillis = 30_000,
            crossingCooldownMillis = 0,
            maxHorizontalAccuracyMeters = null,
            minSpeedMetersPerSecond = 0.0,
            enforceDirection = false,
        )
        val engine = LapEngine(course, config)
        engine.onSample(sample(0, -10.0))
        engine.onSample(sample(2_000, 10.0))   // start at t=1000
        engine.onSample(sample(5_000, -10.0))
        val state = engine.onSample(sample(7_000, 10.0)) // crossing at ~6000, lap ~5s < 30s

        assertEquals(0, state.lapCount)
        assertEquals(LapRejectReason.BelowMinLapDuration, state.lastRejectReason)
    }

    @Test
    fun accuracyThresholdBlocksPoorSamples() {
        val config = LapEngineConfig(
            minLapDurationMillis = 0,
            crossingCooldownMillis = 0,
            maxHorizontalAccuracyMeters = 10.0,
            minSpeedMetersPerSecond = 0.0,
            enforceDirection = false,
        )
        val engine = LapEngine(course, config)
        engine.onSample(sample(0, -10.0, accuracy = 50.0))
        val state = engine.onSample(sample(2_000, 10.0, accuracy = 50.0))

        assertEquals(LapPhase.AwaitingStart, state.phase)
        assertEquals(LapRejectReason.PoorAccuracy, state.lastRejectReason)
    }

    @Test
    fun speedThresholdBlocksStationarySamples() {
        val config = LapEngineConfig(
            minLapDurationMillis = 0,
            crossingCooldownMillis = 0,
            maxHorizontalAccuracyMeters = null,
            minSpeedMetersPerSecond = 5.0,
            enforceDirection = false,
        )
        val engine = LapEngine(course, config)
        engine.onSample(sample(0, -10.0, speed = 1.0))
        val state = engine.onSample(sample(2_000, 10.0, speed = 1.0))

        assertEquals(LapPhase.AwaitingStart, state.phase)
        assertEquals(LapRejectReason.TooSlow, state.lastRejectReason)
    }

    @Test
    fun directionGateBlocksWrongWayCrossing() {
        val config = LapEngineConfig(
            minLapDurationMillis = 0,
            crossingCooldownMillis = 0,
            maxHorizontalAccuracyMeters = null,
            minSpeedMetersPerSecond = 0.0,
            enforceDirection = true,
        )
        val engine = LapEngine(course, config)
        // start lap going east
        engine.onSample(sample(0, -10.0))
        engine.onSample(sample(2_000, 10.0)) // start at t=1000, learns east sign
        // try to complete by crossing west (wrong way)
        engine.onSample(sample(40_000, 10.0))
        val state = engine.onSample(sample(42_000, -10.0)) // crossing west

        assertEquals(0, state.lapCount)
        assertEquals(LapRejectReason.WrongDirection, state.lastRejectReason)
    }

    @Test
    fun sectorCrossingRecordsSplitInsideLap() {
        val sectorCourse = CourseDefinition(
            startFinish = verticalStartFinish(),
            sectors = listOf(verticalSector("S1", "Sector 1", 0, eastMeters = 50.0)),
        )
        val engine = LapEngine(sectorCourse, lenient)
        engine.onSample(sample(0, -10.0))
        engine.onSample(sample(2_000, 10.0))   // start lap at t=1000
        engine.onSample(sample(4_000, 40.0))
        val state = engine.onSample(sample(6_000, 60.0)) // crosses x=50 at ~5000

        val s1 = state.sectors.first { it.sectorId == "S1" }
        assertEquals(SectorStatus.Crossed, s1.status)
        assertTrue(s1.splitMillis != null && s1.splitMillis!! > 0)
        assertTrue(state.latestSector != null)
    }

    @Test
    fun sectorCrossingBeforeLapStartIsIgnored() {
        val sectorCourse = CourseDefinition(
            startFinish = verticalStartFinish(),
            sectors = listOf(verticalSector("S1", "Sector 1", 0, eastMeters = 50.0)),
        )
        val engine = LapEngine(sectorCourse, lenient)
        // cross sector before ever crossing start/finish
        engine.onSample(sample(0, 40.0))
        val state = engine.onSample(sample(2_000, 60.0))

        val s1 = state.sectors.first { it.sectorId == "S1" }
        assertEquals(SectorStatus.Pending, s1.status)
        assertEquals(LapRejectReason.SectorBeforeLapStart, s1.lastRejectReason)
    }

    @Test
    fun duplicateSectorInOneLapIsRejected() {
        val sectorCourse = CourseDefinition(
            startFinish = verticalStartFinish(),
            sectors = listOf(verticalSector("S1", "Sector 1", 0, eastMeters = 50.0)),
        )
        val engine = LapEngine(sectorCourse, lenient)
        engine.onSample(sample(0, -10.0))
        engine.onSample(sample(2_000, 10.0))   // start lap
        engine.onSample(sample(4_000, 40.0))
        engine.onSample(sample(6_000, 60.0))   // first sector crossing
        // cross sector again (back and forth) within same lap
        engine.onSample(sample(8_000, 40.0))
        val state = engine.onSample(sample(10_000, 60.0))

        val s1 = state.sectors.first { it.sectorId == "S1" }
        assertEquals(SectorStatus.Crossed, s1.status)
        assertEquals(LapRejectReason.DuplicateSector, s1.lastRejectReason)
    }

    @Test
    fun sectorStateResetsAfterLapCompletes() {
        val sectorCourse = CourseDefinition(
            startFinish = verticalStartFinish(),
            sectors = listOf(verticalSector("S1", "Sector 1", 0, eastMeters = 50.0)),
        )
        val engine = LapEngine(sectorCourse, lenient)
        engine.onSample(sample(0, -10.0))
        engine.onSample(sample(2_000, 10.0))   // start lap1
        engine.onSample(sample(4_000, 40.0))
        engine.onSample(sample(6_000, 60.0))   // sector crossed in lap1
        // loop south back to the west side without recrossing any line
        engine.onSample(returnSouth(30_000, 60.0))
        engine.onSample(returnSouth(58_000, -10.0))
        engine.onSample(sample(60_000, -10.0))
        val state = engine.onSample(sample(62_000, 10.0)) // complete lap1, start lap2

        assertEquals(1, state.lapCount)
        val s1 = state.sectors.first { it.sectorId == "S1" }
        assertEquals(SectorStatus.Pending, s1.status)
        assertNull(s1.splitMillis)
    }

    @Test
    fun resetReturnsToInitialState() {
        val engine = LapEngine(course, lenient)
        engine.onSample(sample(0, -10.0))
        engine.onSample(sample(2_000, 10.0))
        engine.reset()

        assertEquals(LapPhase.AwaitingStart, engine.state.phase)
        assertEquals(0, engine.state.lapCount)
        assertNull(engine.state.currentLapNumber)
    }

    @Test
    fun firstCrossingExactlyOnLineDoesNotLockDirectionGate() {
        // WR-01: a first crossing that starts exactly on the line has signedSide
        // == 0. The gate must not adopt sign 0 and reject every later lap.
        val config = LapEngineConfig(
            minLapDurationMillis = 0,
            crossingCooldownMillis = 0,
            maxHorizontalAccuracyMeters = null,
            minSpeedMetersPerSecond = 0.0,
            enforceDirection = true,
        )
        val engine = LapEngine(course, config)
        engine.onSample(sample(0, 0.0))         // start on the line (x = 0)
        engine.onSample(sample(2_000, 10.0))    // starts lap; side == 0 here
        // Loop south and complete a normal eastbound lap.
        engine.onSample(returnSouth(30_000, 10.0))
        engine.onSample(returnSouth(58_000, -10.0))
        engine.onSample(sample(60_000, -10.0))
        val state = engine.onSample(sample(62_000, 10.0))

        assertEquals(1, state.lapCount)
        assertNull(state.lastRejectReason)
    }

    @Test
    fun singleSegmentCrossingStartFinishAndSectorRecordsBoth() {
        // WR-03/WR-06: one low-frequency segment crosses start/finish (x = 0)
        // and then a sector (x = 50). The sector split must still be recorded,
        // not skipped because the same segment also produced a start/finish.
        val sectorCourse = CourseDefinition(
            startFinish = verticalStartFinish(),
            sectors = listOf(verticalSector("S1", "Sector 1", 0, eastMeters = 50.0)),
        )
        val engine = LapEngine(sectorCourse, lenient)
        engine.onSample(sample(0, -10.0))
        val state = engine.onSample(sample(10_000, 60.0))

        assertEquals(LapPhase.Timing, state.phase)
        val s1 = state.sectors.first { it.sectorId == "S1" }
        assertEquals(SectorStatus.Crossed, s1.status)
        assertTrue(s1.splitMillis != null && s1.splitMillis!! > 0)
    }
}
