package com.huanfuli.lapsight.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedCommonTest {

    @Test
    fun simulatorProducesRunningGpsProbeState() {
        val state = GpsProbeSimulator.next(GpsProbeState.started(), tick = 3)

        assertTrue(state.isRunning)
        assertEquals(GpsFixStatus.Simulated, state.fixStatus)
        assertEquals(3, state.sampleCount)
        assertEquals("00:03", state.elapsedLabel)
        assertTrue(state.speedKmhLabel != "--")
    }
}
