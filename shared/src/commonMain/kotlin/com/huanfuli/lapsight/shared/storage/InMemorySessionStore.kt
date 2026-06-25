package com.huanfuli.lapsight.shared.storage

import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.track.ReviewEntryType
import com.huanfuli.lapsight.shared.track.ReviewIndex
import com.huanfuli.lapsight.shared.track.ReviewIndexRow
import com.huanfuli.lapsight.shared.track.Track
import com.huanfuli.lapsight.shared.track.TrackMarkingPayloadV1
import com.huanfuli.lapsight.shared.track.TrackMarkingSession
import com.huanfuli.lapsight.shared.track.TrackPayloadV1

/**
 * In-memory [LocalSessionStore] used as the default for Compose previews, tests,
 * and any context that has no platform storage root wired (D-21).
 *
 * It mirrors [FileSessionStore]'s contract — payload-before-index upsert order,
 * typed load results, schemaVersion validation — without touching the file
 * system. This is the store `App()` defaults to so previews/tests need no
 * `StoragePaths` initialization; the Android entrypoint injects a
 * `FileSessionStore` over the real app-private root.
 */
class InMemorySessionStore : LocalSessionStore {

    private val tracks = LinkedHashMap<String, TrackPayloadV1>()
    private val markings = LinkedHashMap<String, TrackMarkingPayloadV1>()
    private val rows = mutableListOf<ReviewIndexRow>()

    override fun saveTrackBundle(track: Track, marking: TrackMarkingSession, app: AppMetadata): SaveResult {
        // Payloads first (mirrors FileSessionStore), then the index row.
        markings[marking.id] = TrackMarkingPayloadV1(marking = marking, app = app)
        tracks[track.id] = TrackPayloadV1(track = track, app = app)
        upsertRows(
            listOf(
                ReviewIndexRow(
                    id = marking.id,
                    type = ReviewEntryType.TrackMarking,
                    name = track.name,
                    createdAtEpochMillis = marking.createdAtEpochMillis,
                    source = marking.source,
                    payloadPath = "markings/${marking.id}.json",
                    sampleCount = marking.samples.size,
                ),
                ReviewIndexRow(
                    id = track.id,
                    type = ReviewEntryType.Track,
                    name = track.name,
                    createdAtEpochMillis = track.createdAtEpochMillis,
                    source = track.source,
                    payloadPath = "tracks/${track.id}.json",
                ),
            ),
        )
        return SaveResult.Saved(
            trackPath = "tracks/${track.id}.json",
            markingPath = "markings/${marking.id}.json",
        )
    }

    override fun loadTrack(trackId: String): LoadResult<TrackPayloadV1> {
        val payload = tracks[trackId] ?: return LoadResult.NotFound
        val problem = validateSchema(payload.schemaVersion, payload.track.id.isBlank(), "missing track id")
        return if (problem != null) LoadResult.Corrupt(problem) else LoadResult.Loaded(payload)
    }

    override fun loadTrackMarking(markingId: String): LoadResult<TrackMarkingPayloadV1> {
        val payload = markings[markingId] ?: return LoadResult.NotFound
        val badCoords = payload.marking.samples.any { !it.latitude.isFinite() || !it.longitude.isFinite() }
        val problem = when {
            payload.schemaVersion != CURRENT_TRACK_SCHEMA_VERSION -> "unsupported schemaVersion ${payload.schemaVersion}"
            payload.marking.id.isBlank() -> "missing marking id"
            badCoords -> "non-finite sample coordinates"
            else -> null
        }
        return if (problem != null) LoadResult.Corrupt(problem) else LoadResult.Loaded(payload)
    }

    override fun readIndex(): ReviewIndex = ReviewIndex(rows = rows.toList())

    private fun upsertRows(newRows: List<ReviewIndexRow>) {
        val keys = newRows.map { it.id to it.type }.toSet()
        rows.removeAll { (it.id to it.type) in keys }
        rows += newRows
    }

    private fun validateSchema(schemaVersion: Int, idMissing: Boolean, idMessage: String): String? = when {
        schemaVersion != CURRENT_TRACK_SCHEMA_VERSION -> "unsupported schemaVersion $schemaVersion"
        idMissing -> idMessage
        else -> null
    }
}
