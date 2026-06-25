package com.huanfuli.lapsight.shared

import kotlin.test.Test
import kotlin.test.assertFalse

class SharedLogicIOSTest {

    @Test
    fun stoppedStateKeepsLastSample() {
        val running = GpsProbeSimulator.next(GpsProbeState.started(), tick = 1)
        val stopped = running.stopped()

        assertFalse(stopped.isRunning)
        assertFalse(stopped.latestSample == null)
    }
}
