package com.huanfuli.lapsight.shared

/**
 * The normal shared boundary for a stream of [LocationSample]s.
 *
 * This is the boundary that track marking and timing flows consume. Simulated
 * replay, Android Fused Location, and iOS Core Location providers must all
 * expose samples through this same contract so the lap engine, storage, review,
 * and export flows never need provider-specific code.
 *
 * Implementations must keep feed lifecycle (`start`/`stop`/`reset`) independent
 * of any track-marking or timing-session state: starting the feed only produces
 * samples, it never starts a timing run.
 */
interface LocationSampleProvider {

    /** True while the feed is producing samples. */
    val isRunning: Boolean

    /** Begin (or resume) emitting samples. */
    fun start()

    /** Stop emitting samples without losing the feed configuration. */
    fun stop()

    /** Stop and clear provider-local feed state before a fresh capture/run. */
    fun reset()

    /**
     * Return the next sample when running, or `null` when stopped/unavailable.
     * Callers poll this (for example on a UI timer) to advance the feed.
     */
    fun nextSample(): LocationSample?
}
