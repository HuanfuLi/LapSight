package com.huanfuli.lapsight.shared.session

import com.huanfuli.lapsight.shared.GpsQualitySummary as GpsQuality
import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.LocationSampleProvider
import com.huanfuli.lapsight.shared.nowEpochMillis

/** Lifecycle phase of a diagnostic raw recording (D-16). */
enum class RawRecordingPhase { Idle, Recording, Stopped }

/**
 * Immutable view of the active raw recording rendered by the Drive dash (D-32).
 *
 * Carries the running sample count, latest fix, and a [GpsQuality] rollup so the
 * dash can show capture progress WITHOUT any lap/ghost timing state — there is no
 * lap count, delta, or sector data here by construction (D-16).
 */
data class RawRecordingSnapshot(
    val phase: RawRecordingPhase,
    val isActive: Boolean,
    val sampleCount: Int,
    val latestSample: LocationSample?,
    val gpsQuality: GpsQuality?,
)

/**
 * Diagnostic raw-recording export payload (D-16, D-17).
 *
 * Built purely from [LocationSampleDto] (via [LocationSample.toDto]) plus a
 * [GpsQuality] rollup. [isDiagnosticRaw] is always true and [lapCount] is always
 * zero: this output is STRUCTURALLY excluded from valid-session counting and can
 * never be mistaken for a [TimingSessionPayloadV1] (D-17). It carries no
 * [TimingSession], no laps, and no sector/ghost data.
 */
data class RawRecordingPayload(
    val id: String,
    val createdAtEpochMillis: Long,
    val samples: List<LocationSampleDto>,
    val gpsQuality: GpsQuality,
    /** Explicit diagnostic marker so this can never count as a valid session (D-17). */
    val isDiagnosticRaw: Boolean = true,
    /** Always zero: a raw recording constructs no lap engine and no laps (D-16). */
    val lapCount: Int = 0,
)

/**
 * A thin diagnostic raw-recording seam used when the app is NOT Ready (D-16, D-17).
 *
 * Mirrors the [com.huanfuli.lapsight.shared.ui.DriveMarkingController] provider /
 * tick / stop loop but produces a diagnostic raw record — NOT a Track and NOT a
 * timing session. It records samples for later replay/diagnosis while the user
 * cannot yet start trustworthy formal timing, and is structurally excluded from
 * the five valid closed-course sessions: its output reports zero laps and an
 * explicit raw marker (D-17).
 *
 * It deliberately constructs NO `TimingSessionRecorder`, `LapEngine`, or
 * `LiveDeltaEngine`: there is no lap, ghost, delta, or sector state anywhere in
 * this seam. It owns no Compose/platform dependency.
 *
 * @param provider the same GPS feed boundary the marking/timing flows consume.
 * @param now clock used for the payload id/timestamp; injectable for tests.
 */
class RawRecordingController(
    private val provider: LocationSampleProvider,
    private val now: () -> Long = ::nowEpochMillisSafe,
) {
    private var phase: RawRecordingPhase = RawRecordingPhase.Idle
    private val samples: MutableList<LocationSample> = mutableListOf()

    /** Current immutable view of the raw recording. */
    fun snapshot(): RawRecordingSnapshot = RawRecordingSnapshot(
        phase = phase,
        isActive = phase == RawRecordingPhase.Recording,
        sampleCount = samples.size,
        latestSample = samples.lastOrNull(),
        gpsQuality = if (samples.isEmpty()) null else GpsQuality.from(samples),
    )

    /**
     * Begin a diagnostic raw recording. Starts the feed from a clean state so the
     * capture is self-contained; constructs no lap/ghost/timing state (D-16).
     */
    fun start() {
        provider.reset()
        samples.clear()
        provider.start()
        phase = RawRecordingPhase.Recording
    }

    /**
     * Advance the feed by every sample ready this tick, accumulating them into the
     * diagnostic trace. Draining the full backlog (rather than one sample per tick)
     * means a real receiver's buffered fixes are never dropped at the poll rate.
     * Returns the drained batch in arrival order (empty when not recording or the
     * feed is stopped).
     */
    fun tick(): List<LocationSample> {
        if (phase != RawRecordingPhase.Recording) return emptyList()
        if (!provider.isRunning) return emptyList()
        val batch = provider.drainPending()
        if (batch.isEmpty()) return emptyList()
        samples.addAll(batch)
        return batch
    }

    /**
     * Stop recording and return the diagnostic payload (samples + GPS quality),
     * explicitly flagged raw with zero laps so it never counts as a valid session
     * (D-17). Stops the feed without losing the captured trace.
     */
    fun stop(): RawRecordingPayload {
        provider.stop()
        phase = RawRecordingPhase.Stopped
        val createdAt = now()
        return RawRecordingPayload(
            id = "raw-$createdAt",
            createdAtEpochMillis = createdAt,
            samples = samples.map { it.toDto() },
            gpsQuality = GpsQuality.from(samples),
        )
    }
}

/** Wall-clock epoch millis that never throws (mirrors the controller guards). */
private fun nowEpochMillisSafe(): Long = try {
    nowEpochMillis()
} catch (_: Throwable) {
    0L
}
