package com.huanfuli.lapsight.shared.storage

import com.huanfuli.lapsight.shared.track.ReviewIndex
import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.session.GhostCandidate
import com.huanfuli.lapsight.shared.session.TimingSession
import com.huanfuli.lapsight.shared.session.TimingSessionPayloadV1
import com.huanfuli.lapsight.shared.track.Track
import com.huanfuli.lapsight.shared.track.TrackMarkingPayloadV1
import com.huanfuli.lapsight.shared.track.TrackMarkingSession
import com.huanfuli.lapsight.shared.track.TrackPayloadV1

/**
 * Repository API for local-first, versioned, index-backed storage (D-21 through D-25).
 *
 * Implementations write canonical JSON payload files under an app-private root and
 * maintain a lightweight metadata index for Review list screens. Payloads are
 * always written before the index is updated so a failed write never leaves a
 * dangling index entry.
 */
interface LocalSessionStore {

    /**
     * Persists a [track] and its source [marking] session as a bundle: both payload
     * files are written (atomically) before the [ReviewIndex] is updated.
     */
    fun saveTrackBundle(track: Track, marking: TrackMarkingSession, app: AppMetadata): SaveResult

    /** Loads a saved track payload, returning a typed result for missing/corrupt files. */
    fun loadTrack(trackId: String): LoadResult<TrackPayloadV1>

    /** Loads a saved marking payload, returning a typed result for missing/corrupt files. */
    fun loadTrackMarking(markingId: String): LoadResult<TrackMarkingPayloadV1>

    /** Reads the current Review metadata index (empty if none exists yet). */
    fun readIndex(): ReviewIndex

    // --- Timing session drafts (D-13..D-20) --------------------------------

    /**
     * Checkpoints an active timing draft: raw samples, completed laps, and
     * sector events are persisted continuously so the draft survives crash /
     * app restart before formal Save (D-13). Overwrites any prior active draft
     * for the same session id.
     */
    fun saveTimingDraft(
        session: TimingSession,
        samples: List<com.huanfuli.lapsight.shared.session.LocationSampleDto>,
        laps: List<com.huanfuli.lapsight.shared.session.LapDto>,
        sectorEvents: List<com.huanfuli.lapsight.shared.session.SectorEventDto>,
        gpsQuality: com.huanfuli.lapsight.shared.session.GpsQualitySummary,
        totalDurationMillis: Long,
        app: AppMetadata,
    )

    /**
     * Loads the unfinished timing draft if one exists (D-15). Returns null when
     * no draft is present, or [LoadResult.Corrupt] surfaced as null so recovery
     * never crashes the caller (T-03-11).
     */
    fun loadUnfinishedDraft(): TimingSessionPayloadV1?

    /**
     * Promotes a stopped draft to a canonical saved [TimingSessionPayloadV1] plus
     * a Review index row (D-14, D-16). The active/stopped draft is cleared after
     * the canonical payload and index are written.
     */
    fun saveTimingSession(payload: TimingSessionPayloadV1, app: AppMetadata): SaveResult

    /**
     * Removes the unfinished draft payload and any draft-only index entries so
     * it never enters formal Review history (D-16, T-03-12). No-op when no
     * draft exists.
     */
    fun discardTimingDraft()

    /** Loads a saved timing-session payload by id. */
    fun loadTimingSession(sessionId: String): LoadResult<TimingSessionPayloadV1>

    /**
     * Derives the per-Track fastest valid lap as a [GhostCandidate], EXCLUDING
     * any session whose source metadata is simulated (D-20, D-43). Returns null
     * when no real candidate exists.
     */
    fun ghostCandidateForTrack(trackId: String): GhostCandidate?
}

/** Outcome of a save operation. */
sealed interface SaveResult {
    /** All payloads and the index were written successfully. */
    data class Saved(val trackPath: String, val markingPath: String) : SaveResult
}

/**
 * Typed result of a payload load (D-21).
 *
 * Malformed/corrupt JSON yields [Corrupt] and a missing file yields [NotFound];
 * neither crashes the caller.
 */
sealed interface LoadResult<out T> {
    data class Loaded<T>(val value: T) : LoadResult<T>
    data object NotFound : LoadResult<Nothing>
    data class Corrupt(val reason: String) : LoadResult<Nothing>
}
