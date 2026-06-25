package com.huanfuli.lapsight.shared.lap

import com.huanfuli.lapsight.shared.GpsFixStatus
import com.huanfuli.lapsight.shared.LocationSample

/**
 * Headless driver for a demo/replay timing session.
 *
 * Wraps a [LapEngine] over a preset [CourseDefinition] and a fixed list of
 * deterministic samples. The UI advances it one sample at a time (e.g. on a
 * timer) and renders the resulting [LapDashState]. All lap logic stays in the
 * engine; this only sequences samples and maps to presentation state.
 *
 * Defaults use [ReplayFixtures.DEMO_COURSE] and a multi-lap, multi-sector
 * replay so the dash visibly advances current/last/best/lap count and sector
 * splits without any real GPS provider.
 */
class DemoLapSession(
    private val courseName: String = "Demo Course",
    private val course: CourseDefinition = ReplayFixtures.DEMO_COURSE,
    private val config: LapEngineConfig = ReplayFixtures.DEMO_CONFIG,
    private val samples: List<LocationSample> = ReplayFixtures.multiLapLoop(
        listOf(40_000, 32_000, 36_000),
    ),
) {
    private val engine = LapEngine(course, config)
    private var index = 0
    private var latestSample: LocationSample? = null
    private var running = false

    var dashState: LapDashState = LapDashState.idle(courseName)
        private set

    /** Total number of replay samples; used by the UI to loop or stop. */
    val sampleCount: Int get() = samples.size

    /** True once every replay sample has been consumed. */
    val isFinished: Boolean get() = index >= samples.size

    /** Begin (or resume) the demo session. */
    fun start() {
        running = true
        dashState = dashState.copy(isRunning = true, fixStatus = GpsFixStatus.Simulated)
    }

    /** Pause without losing progress. */
    fun stop() {
        running = false
        dashState = dashState.copy(isRunning = false)
    }

    /** Reset to the beginning of the replay. */
    fun reset() {
        engine.reset()
        index = 0
        latestSample = null
        running = false
        dashState = LapDashState.idle(courseName)
    }

    /**
     * Advance the session by one replay sample, updating [dashState]. No-op when
     * stopped or finished. Returns the updated dash state.
     */
    fun tick(): LapDashState {
        if (!running || isFinished) return dashState
        val sample = samples[index]
        index += 1
        latestSample = sample
        val timing = engine.onSample(sample)
        dashState = LapDashState.from(
            isRunning = running && !isFinished,
            fixStatus = GpsFixStatus.Simulated,
            courseName = courseName,
            timing = timing,
            latestSample = sample,
        )
        if (isFinished) {
            running = false
            dashState = dashState.copy(isRunning = false)
        }
        return dashState
    }
}
