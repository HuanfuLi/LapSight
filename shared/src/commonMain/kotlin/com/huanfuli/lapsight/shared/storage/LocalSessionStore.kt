package com.huanfuli.lapsight.shared.storage

import com.huanfuli.lapsight.shared.track.ReviewIndex
import com.huanfuli.lapsight.shared.session.AppMetadata
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
