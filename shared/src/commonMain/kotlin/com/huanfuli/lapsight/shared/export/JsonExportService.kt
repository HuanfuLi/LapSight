package com.huanfuli.lapsight.shared.export

import com.huanfuli.lapsight.shared.session.TimingSessionPayloadV1
import com.huanfuli.lapsight.shared.storage.FileSessionStore
import com.huanfuli.lapsight.shared.storage.LoadResult
import com.huanfuli.lapsight.shared.storage.LocalSessionStore
import com.huanfuli.lapsight.shared.track.TrackMarkingPayloadV1
import com.huanfuli.lapsight.shared.track.TrackPayloadV1

/**
 * Canonical JSON export service for Track and TimingSession bundles (D-38).
 *
 * Loads canonical saved payloads from [store] and encodes them using the same
 * `canonicalJson` config as [FileSessionStore] so saved and exported JSON share
 * identical formatting (D-24).
 *
 * Each export method operates on a single explicit entity ID; batch export is
 * deliberately absent (D-40).
 */
class JsonExportService(
    private val store: LocalSessionStore,
) {
    private val json = FileSessionStore.canonicalJson

    /**
     * Exports the full [TrackPayloadV1] for the given track ID as canonical JSON.
     *
     * Includes: schemaVersion, Track (name, id, source, referenceLine, startFinish,
     * sectors), AppMetadata, and all serialized defaults.
     */
    fun exportTrack(trackId: String): ByteArray {
        val result = store.loadTrack(trackId)
        return when (result) {
            is LoadResult.Loaded -> json.encodeToString(TrackPayloadV1.serializer(), result.value)
                .encodeToByteArray()
            is LoadResult.NotFound -> throw ExportNotFoundException("Track not found: $trackId")
            is LoadResult.Corrupt -> throw ExportFailedException("Track payload corrupt: ${result.reason}")
        }
    }

    /**
     * Exports the full [TrackMarkingPayloadV1] for the given marking ID as canonical JSON.
     *
     * Includes: schemaVersion, TrackMarkingSession (raw samples, source), AppMetadata.
     */
    fun exportTrackMarking(markingId: String): ByteArray {
        val result = store.loadTrackMarking(markingId)
        return when (result) {
            is LoadResult.Loaded -> json.encodeToString(TrackMarkingPayloadV1.serializer(), result.value)
                .encodeToByteArray()
            is LoadResult.NotFound -> throw ExportNotFoundException("Marking not found: $markingId")
            is LoadResult.Corrupt -> throw ExportFailedException("Marking payload corrupt: ${result.reason}")
        }
    }

    /**
     * Exports the full [TimingSessionPayloadV1] for the given session ID as canonical JSON.
     *
     * Includes: schemaVersion, TimingSession, raw samples, lap events, sector events,
     * GPS quality summary, start/finish, sector lines, AppMetadata, and source metadata.
     */
    fun exportTimingSession(sessionId: String): ByteArray {
        val result = store.loadTimingSession(sessionId)
        return when (result) {
            is LoadResult.Loaded -> json.encodeToString(TimingSessionPayloadV1.serializer(), result.value)
                .encodeToByteArray()
            is LoadResult.NotFound -> throw ExportNotFoundException("Session not found: $sessionId")
            is LoadResult.Corrupt -> throw ExportFailedException("Session payload corrupt: ${result.reason}")
        }
    }
}

/** Thrown when the requested entity ID is not found in the store. */
class ExportNotFoundException(message: String) : Exception(message)

/** Thrown when a payload exists but is corrupt or unreadable. */
class ExportFailedException(message: String) : Exception(message)
