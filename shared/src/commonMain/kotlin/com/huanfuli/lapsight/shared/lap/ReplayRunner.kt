package com.huanfuli.lapsight.shared.lap

import com.huanfuli.lapsight.shared.LocationSample

/**
 * One step of a replay: the sample that was fed and the resulting state.
 */
data class ReplayStep(
    val sample: LocationSample,
    val state: LapTimingState,
)

/** Aggregate result of replaying a fixture through the lap engine. */
data class ReplayResult(
    val steps: List<ReplayStep>,
) {
    /** Final timing state after the last sample. */
    val finalState: LapTimingState
        get() = steps.lastOrNull()?.state ?: error("Replay had no samples")

    /** Sector events observed in order across the whole replay. */
    val sectorEvents: List<SectorEvent>
        get() = steps.mapNotNull { it.state.latestSector }.distinct()
}

/**
 * Feeds a fixed list of samples through a fresh [LapEngine] deterministically.
 *
 * This is the headless entry point used by tests and by the demo UI path: it
 * needs no UI or platform services, satisfying the "engine runs from replay
 * fixtures" requirement.
 */
class ReplayRunner(
    private val course: CourseDefinition,
    private val config: LapEngineConfig = LapEngineConfig(),
) {
    /** Replay [samples] and capture each intermediate state. */
    fun run(samples: List<LocationSample>): ReplayResult {
        val engine = LapEngine(course, config)
        val steps = samples.map { sample ->
            ReplayStep(sample = sample, state = engine.onSample(sample))
        }
        return ReplayResult(steps)
    }
}
