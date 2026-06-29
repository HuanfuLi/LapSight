package com.huanfuli.lapsight.shared.session

import com.huanfuli.lapsight.shared.lap.LapEngineConfig
import com.huanfuli.lapsight.shared.lap.ReplayResult
import com.huanfuli.lapsight.shared.lap.ReplayRunner
import com.huanfuli.lapsight.shared.storage.FileSessionStore
import kotlinx.serialization.SerializationException

/**
 * Typed outcome of decoding an exported timing-session and replaying it through
 * the clean-room engine (D-25, D-28).
 *
 * Mirrors the established "bad input is data, not an exception" pattern used by
 * [com.huanfuli.lapsight.shared.session.CoursePreflightResult] and
 * `storage/LoadResult`: a corrupt export or a payload with no confirmed course is
 * a value the caller branches on, never a thrown serialization failure that could
 * crash the replay/diagnosis path (T-5.1-01).
 */
sealed interface ReplayDecodeResult {
    /** The export decoded and replayed cleanly; [result] is the engine output. */
    data class Decoded(val result: ReplayResult) : ReplayDecodeResult

    /**
     * The exported bytes could not be decoded into a [TimingSessionPayloadV1]
     * (malformed/corrupt/tampered JSON). [reason] is a human-readable diagnosis.
     */
    data class Corrupt(val reason: String) : ReplayDecodeResult

    /**
     * The payload decoded but lacks a confirmed start/finish line, so no course
     * can be rebuilt and no replay can run (D-19).
     */
    data object NoCourse : ReplayDecodeResult
}

/**
 * Pure JSON-export to [ReplayRunner] decode helper: the previously-missing link
 * between a saved/exported field session and the deterministic engine (D-25,
 * D-28). Replay determinism is the spine of the Go / Hardening-Required / No-Go
 * decision, so every field failure must be reproducible by feeding its export
 * back through the SAME engine that produced it live.
 *
 * Clean-room rule (AGENTS.md): this object lives in `shared/commonMain`, imports
 * zero Compose/platform/Okio symbols, and is exercised from `commonTest`. It does
 * NOT introduce a second parser or a second timing algorithm — it composes the
 * existing seams:
 * - [FileSessionStore.canonicalJson] (the same config [JsonExportService] encodes
 *   with, so encode/decode are byte-symmetric),
 * - [LocationSampleDto.toModel] (the existing sample mapper),
 * - [courseFromTrack] (the direction-aware course rebuild, so Reverse decodes
 *   correctly),
 * - [ReplayRunner] (the consume side / clean-room [LapEngine]).
 */
object SessionReplayDecoder {

    /**
     * Decode [exportedBytes] (canonical [TimingSessionPayloadV1] JSON as produced
     * by [JsonExportService.exportTimingSession]) and replay the embedded samples
     * through a fresh [ReplayRunner].
     *
     * Returns:
     * - [ReplayDecodeResult.Decoded] with the engine [ReplayResult] on success,
     * - [ReplayDecodeResult.Corrupt] when the bytes are not valid payload JSON
     *   (serialization failure is caught and converted — never re-thrown),
     * - [ReplayDecodeResult.NoCourse] when the payload carries no confirmed
     *   start/finish line (D-19).
     *
     * @param config lap-engine tuning to replay under; defaults to the production
     *   [LapEngineConfig]. Pass the session's original config to reproduce a field
     *   run exactly.
     */
    fun decodeForReplay(
        exportedBytes: ByteArray,
        config: LapEngineConfig = LapEngineConfig(),
    ): ReplayDecodeResult {
        val payload = try {
            FileSessionStore.canonicalJson.decodeFromString(
                TimingSessionPayloadV1.serializer(),
                exportedBytes.decodeToString(),
            )
        } catch (e: SerializationException) {
            return ReplayDecodeResult.Corrupt(e.message ?: "Malformed timing-session export")
        } catch (e: IllegalArgumentException) {
            // decodeToString / kotlinx can raise IAE for structurally invalid input.
            return ReplayDecodeResult.Corrupt(e.message ?: "Invalid timing-session export")
        }

        // A confirmed start/finish must be a real (non-degenerate) line: identical
        // endpoints cannot define a crossing, so the export carries no usable course
        // (D-19). Treat it as data, not a silent 0-lap replay.
        val sf = payload.session.startFinish
        val degenerate = sf.pointA.latitude == sf.pointB.latitude &&
            sf.pointA.longitude == sf.pointB.longitude
        if (degenerate) return ReplayDecodeResult.NoCourse

        val course = courseFromTrack(
            sf,
            payload.session.sectors,
            payload.session.direction,
        ) ?: return ReplayDecodeResult.NoCourse

        val samples = payload.samples.map { it.toModel() }
        return ReplayDecodeResult.Decoded(ReplayRunner(course, config).run(samples))
    }
}
