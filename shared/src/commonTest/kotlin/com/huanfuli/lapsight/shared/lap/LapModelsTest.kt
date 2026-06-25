package com.huanfuli.lapsight.shared.lap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LapModelsTest {

    @Test
    fun configHasExplicitDefaults() {
        val config = LapEngineConfig()

        assertEquals(8_000, config.minLapDurationMillis)
        assertEquals(3_000, config.crossingCooldownMillis)
        assertEquals(25.0, config.maxHorizontalAccuracyMeters)
        assertEquals(2.0, config.minSpeedMetersPerSecond)
        assertEquals(80.0, config.directionToleranceDegrees)
        assertTrue(config.enforceDirection)
    }

    @Test
    fun lenientTestConfigDisablesGates() {
        val config = LapEngineConfig.lenientForTests()

        assertEquals(0, config.minLapDurationMillis)
        assertEquals(0, config.crossingCooldownMillis)
        assertNull(config.maxHorizontalAccuracyMeters)
        assertEquals(0.0, config.minSpeedMetersPerSecond)
        assertTrue(!config.enforceDirection)
    }

    @Test
    fun configRejectsInvalidValues() {
        assertFailsWith<IllegalArgumentException> {
            LapEngineConfig(minLapDurationMillis = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            LapEngineConfig(maxHorizontalAccuracyMeters = 0.0)
        }
        assertFailsWith<IllegalArgumentException> {
            LapEngineConfig(directionToleranceDegrees = 200.0)
        }
    }

    @Test
    fun lapEventDurationIsEndMinusStart() {
        val lap = LapEvent(lapNumber = 1, startMillis = 1_000, endMillis = 91_000)
        assertEquals(90_000, lap.durationMillis)
    }

    @Test
    fun initialStateBuildsPendingSectorsInOrder() {
        val course = CourseDefinition(
            startFinish = StartFinishLine(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.001)),
            sectors = listOf(
                SectorLine("S2", "Sector 2", order = 1, GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.001)),
                SectorLine("S1", "Sector 1", order = 0, GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.001)),
            ),
        )

        val state = LapTimingState.initial(course)

        assertEquals(LapPhase.AwaitingStart, state.phase)
        assertEquals(0, state.lapCount)
        assertEquals(listOf("S1", "S2"), state.sectors.map { it.sectorId })
        assertTrue(state.sectors.all { it.status == SectorStatus.Pending })
    }
}
