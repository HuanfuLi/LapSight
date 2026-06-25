package com.huanfuli.lapsight.shared.lap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LapDashStateTest {

    @Test
    fun formatsLapTimeAsMinutesSecondsMillis() {
        assertEquals("00:00.000", 0L.formatLapTime())
        assertEquals("01:30.500", 90_500L.formatLapTime())
        assertEquals("--:--.---", (null as Long?).formatLapTime())
        assertEquals("00:00.000", (-50L).formatLapTime())
    }

    @Test
    fun demoSessionAdvancesAndCompletesLaps() {
        val session = DemoLapSession()
        session.start()
        while (!session.isFinished) {
            session.tick()
        }
        val dash = session.dashState

        assertEquals(3, dash.lapCount)
        assertTrue(dash.bestLapMillis != null)
        assertTrue(dash.lastLapMillis != null)
        assertEquals("Demo Course", dash.courseName)
        assertTrue(!dash.isRunning)
    }

    @Test
    fun demoSessionExposesSectorSummaries() {
        val session = DemoLapSession()
        session.start()
        repeat(session.sampleCount) { session.tick() }

        assertEquals(2, session.dashState.sectorSummaries.size)
        assertEquals(listOf("S1", "S2"), session.dashState.sectorSummaries.map { it.id })
    }

    @Test
    fun resetReturnsToIdle() {
        val session = DemoLapSession()
        session.start()
        session.tick()
        session.reset()

        assertEquals(0, session.dashState.lapCount)
        assertTrue(!session.dashState.isRunning)
    }

    @Test
    fun tickIsNoOpWhenStopped() {
        val session = DemoLapSession()
        // not started
        val before = session.dashState
        val after = session.tick()
        assertEquals(before, after)
    }
}
