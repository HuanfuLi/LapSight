package com.huanfuli.lapsight.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedLogicAndroidHostTest {

    @Test
    fun idleStateHasNoGpsFix() {
        val state = GpsProbeState.idle()

        assertEquals(GpsFixStatus.Idle, state.fixStatus)
        assertEquals("--", state.speedKmhLabel)
    }
}
