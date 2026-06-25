package com.huanfuli.lapsight.shared

import com.huanfuli.lapsight.shared.fixtures.GpsFixtureLibrary

/**
 * A provider-layer simulated GPS feed (D-03, D-05).
 *
 * Replays a deterministic [GpsFixtureLibrary] scenario through the normal
 * [LocationSampleProvider] boundary so the rest of the app (track marking,
 * timing, review, export) consumes it exactly as it will consume real phone
 * GPS later. It owns only feed state — it never starts or stops a timing or
 * marking session itself.
 *
 * While running, the feed flows continuously: once the scenario's samples are
 * exhausted it wraps around with a forward-advancing timestamp, modelling a
 * phone that keeps moving around the track (D-05). After [stop] it emits no
 * samples until restarted.
 *
 * @param scenarioId the [GpsFixtureLibrary] scenario to replay.
 * @param samples the resolved sample list (defaults to the scenario's samples).
 */
class SimulatedGpsProvider(
    val scenarioId: String = GpsFixtureLibrary.CLEAN_10_LOOP,
    private val samples: List<LocationSample> =
        GpsFixtureLibrary.scenario(scenarioId).samples,
) : LocationSampleProvider {

    private var index = 0
    private var running = false

    /** The scenario id currently driving the feed. */
    val activeScenarioId: String get() = scenarioId

    /** Number of distinct samples in one pass of the scenario. */
    val sampleCount: Int get() = samples.size

    // Time spanned by one pass, plus one average step, so wrapped timestamps
    // keep moving strictly forward across the loop boundary.
    private val cycleMillis: Long = if (samples.size < 2) {
        0L
    } else {
        val span = samples.last().elapsedMillis - samples.first().elapsedMillis
        span + span / (samples.size - 1)
    }

    override val isRunning: Boolean get() = running

    override fun start() {
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun reset() {
        running = false
        index = 0
    }

    override fun nextSample(): LocationSample? {
        if (!running || samples.isEmpty()) return null
        val base = samples[index % samples.size]
        val completedCycles = index / samples.size
        index++
        return if (completedCycles == 0) {
            base
        } else {
            base.copy(elapsedMillis = base.elapsedMillis + completedCycles * cycleMillis)
        }
    }
}
