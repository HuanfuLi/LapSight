package com.huanfuli.lapsight.shared.storage

import com.huanfuli.lapsight.shared.track.ReviewIndex
import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.session.GhostCandidate
import com.huanfuli.lapsight.shared.session.GhostReferencePayloadV1
import com.huanfuli.lapsight.shared.session.TimingSession
import com.huanfuli.lapsight.shared.session.TimingSessionPayloadV1
import com.huanfuli.lapsight.shared.ghost.CourseCompatibilityKey
import com.huanfuli.lapsight.shared.track.CurrentTrackSelection
import com.huanfuli.lapsight.shared.track.GhostReferencePayloadV2
import com.huanfuli.lapsight.shared.track.Track
import com.huanfuli.lapsight.shared.track.TrackMarkingPayloadV1
import com.huanfuli.lapsight.shared.track.TrackMarkingSession
import com.huanfuli.lapsight.shared.track.TrackPayloadV1
import com.huanfuli.lapsight.shared.track.TrackProfile

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
        // Complete-interval V2 Sector results (D-06, D-11). Defaulted so existing
        // callers and V1 history are unaffected; the recorder threads them so a
        // saved session carries complete Sector coverage alongside legacy splits.
        sectorResults: List<com.huanfuli.lapsight.shared.session.SectorResultDto> = emptyList(),
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

    // --- Ghost reference laps (D-01..D-05, D-12, D-24) ---------------------

    /**
     * Loads the persisted full ghost reference lap for the exact [CourseCompatibilityKey].
     *
     * The key includes profile identity, geometry compatibility identity, selected Course
     * Direction, and real/simulated source boundary, so Demo/Real, Recorded/Reverse,
     * duplicates, and geometry changes cannot cross-bind. Implementations validate the
     * request key before building a path and validate payload/request equality before
     * returning [LoadResult.Loaded].
     */
    fun loadReferenceLap(key: CourseCompatibilityKey): LoadResult<GhostReferencePayloadV2>

    /**
     * Persists [payload] under its exact [CourseCompatibilityKey]. The source flag inside
     * [GhostReferencePayloadV2.source] must match `payload.compatibilityKey.isSimulated`;
     * otherwise the save is rejected before any payload can contaminate another slot.
     */
    fun saveReferenceLap(payload: GhostReferencePayloadV2, app: AppMetadata): SaveResult

    /**
     * Loads the persisted full ghost reference lap for [trackId] within the
     * requested source boundary (D-03, D-04). [isSimulated] selects the real
     * (`false`) or simulated (`true`) reference slot; a real lookup NEVER returns
     * a simulated payload and vice versa (D-24). Returns a typed
     * [LoadResult.NotFound]/[LoadResult.Corrupt] so a missing or malformed
     * reference never blocks timing (T-04-05).
     */
    fun loadReferenceLap(trackId: String, isSimulated: Boolean): LoadResult<GhostReferencePayloadV1>

    /**
     * Persists [payload] as the full ghost reference lap for its Track. The
     * real/simulated slot is chosen by [GhostReferencePayloadV1.source]'s
     * `isSimulated`, so a simulated reference never overwrites a real one
     * (D-04, D-24).
     */
    fun saveReferenceLap(payload: GhostReferencePayloadV1, app: AppMetadata): SaveResult

    // --- V2 course profiles + side-by-side migration (D-12..D-14) -----------

    /**
     * Upgrades every persisted V1 Track / TimingSession / Ghost reference into the
     * side-by-side V2 representation (D-12, SC-01, SC-03).
     *
     * Migration is **non-destructive**: each source payload is decoded and validated
     * before any V2 payload is committed, V2 payloads are written into their own
     * locations, and every V1 original file remains readable afterwards (D-13). The
     * profile index is written **last** (payload-first / index-last), so a write
     * fault between the V2 payloads and the index leaves the V1 originals intact and
     * is fully recoverable by re-running [migrate] (T-05-04). Migration is
     * idempotent: deterministic V2 identities mean a re-run yields exactly one
     * logical profile and one first revision per source Track. Migration NEVER writes
     * a current selection (D-01, D-04). Source payloads with unsafe opaque ids or
     * corrupt geometry are skipped (never path-built) and reported in
     * [MigrationResult.skipped] (T-05-03).
     *
     * [app] stamps the migrating app/build onto each freshly written V2 profile
     * payload; session and reference payloads carry their original app metadata.
     */
    fun migrate(app: AppMetadata): MigrationResult

    /**
     * Persists [profile] as a V2 [TrackProfilePayloadV2] aggregate (payload first),
     * then upserts it into the profile index (index last). Rejects an unsafe
     * [TrackProfile.profileId] before any path is built (T-05-03).
     */
    fun saveProfile(profile: TrackProfile, app: AppMetadata): SaveResult

    /** Loads a saved V2 profile by id, returning a typed result for missing/corrupt files. */
    fun loadProfile(profileId: String): LoadResult<TrackProfile>

    /** Lists every non-archived V2 profile recorded in the profile index. */
    fun listActiveProfiles(): List<TrackProfile>

    // --- Explicit current-Track selection (D-01..D-04) ----------------------

    /**
     * Loads the persisted explicit current-Track selection (D-01).
     *
     * Returns [LoadResult.NotFound] when no selection has ever been written (the
     * post-migration default), [LoadResult.Corrupt] when the persisted payload is
     * malformed or carries an unsafe opaque id (defense in depth, T-05-05), and
     * [LoadResult.Loaded] otherwise. There is NO newest/only fallback: an absent
     * selection is reported as absent, never substituted (D-03, D-04).
     */
    fun loadCurrentSelection(): LoadResult<CurrentTrackSelection>

    /**
     * Persists [selection] as the explicit current Track + Course Direction,
     * overwriting any prior selection with a payload-first atomic write. Rejects a
     * non-null but unsafe [CurrentTrackSelection.profileId] before any write
     * (T-05-05).
     */
    fun setCurrentSelection(selection: CurrentTrackSelection)

    /** Clears any persisted current selection (used on archive of the current Track). */
    fun clearCurrentSelection()
}

/**
 * Outcome of a [LocalSessionStore.migrate] run.
 *
 * Counts are of payloads actually written this run; [skipped] records every source
 * payload that was rejected before a path was built (unsafe id / corrupt geometry /
 * unsupported version) so callers can surface unmigrated history without crashing.
 */
data class MigrationResult(
    val profilesMigrated: Int,
    val sessionsMigrated: Int,
    val referencesMigrated: Int,
    val skipped: List<MigrationSkip> = emptyList(),
)

/** One source payload that migration declined to upgrade, with the reason. */
data class MigrationSkip(
    val sourceId: String,
    val reason: String,
)

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
