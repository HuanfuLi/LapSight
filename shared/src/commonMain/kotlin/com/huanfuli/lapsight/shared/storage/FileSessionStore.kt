package com.huanfuli.lapsight.shared.storage

import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.track.ReviewEntryType
import com.huanfuli.lapsight.shared.track.ReviewIndex
import com.huanfuli.lapsight.shared.track.ReviewIndexRow
import com.huanfuli.lapsight.shared.track.Track
import com.huanfuli.lapsight.shared.track.TrackMarkingPayloadV1
import com.huanfuli.lapsight.shared.track.TrackMarkingSession
import com.huanfuli.lapsight.shared.track.TrackPayloadV1
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

/**
 * Okio-backed [LocalSessionStore] (D-21 through D-25).
 *
 * Layout under the injected app-private [root]:
 * ```
 * <root>/tracks/<trackId>.json       canonical Track payload
 * <root>/markings/<markingId>.json   canonical TrackMarkingSession payload
 * <root>/index.json                  lightweight Review metadata index
 * ```
 *
 * Every write goes to a sibling `*.tmp` file and is then atomically moved into
 * place to avoid partial-write corruption. Payloads are written before the index
 * so a failed index write never leaves a dangling index row (D-22, D-23). Loads
 * return a typed [LoadResult]; malformed JSON is reported as [LoadResult.Corrupt]
 * rather than crashing the caller (D-21). No database/ORM schema is created.
 */
class FileSessionStore(
    private val fileSystem: FileSystem,
    private val root: Path,
    private val json: Json = canonicalJson,
) : LocalSessionStore {

    private val tracksDir: Path get() = root / TRACKS_DIR
    private val markingsDir: Path get() = root / MARKINGS_DIR
    private val indexPath: Path get() = root / INDEX_FILE

    override fun saveTrackBundle(track: Track, marking: TrackMarkingSession, app: AppMetadata): SaveResult {
        val markingPayload = TrackMarkingPayloadV1(marking = marking, app = app)
        val trackPayload = TrackPayloadV1(track = track, app = app)

        val markingPath = markingsDir / "${marking.id}.json"
        val trackPath = tracksDir / "${track.id}.json"

        // Payloads first: write the recoverable evidence before touching the index.
        writeAtomically(markingPath, json.encodeToString(markingPayload))
        writeAtomically(trackPath, json.encodeToString(trackPayload))

        // Index last: a failure here leaves payloads intact and no dangling row.
        val updated = upsertRows(
            readIndex(),
            listOf(
                ReviewIndexRow(
                    id = marking.id,
                    type = ReviewEntryType.TrackMarking,
                    name = track.name,
                    createdAtEpochMillis = marking.createdAtEpochMillis,
                    source = marking.source,
                    payloadPath = "$MARKINGS_DIR/${marking.id}.json",
                    sampleCount = marking.samples.size,
                ),
                ReviewIndexRow(
                    id = track.id,
                    type = ReviewEntryType.Track,
                    name = track.name,
                    createdAtEpochMillis = track.createdAtEpochMillis,
                    source = track.source,
                    payloadPath = "$TRACKS_DIR/${track.id}.json",
                ),
            ),
        )
        writeAtomically(indexPath, json.encodeToString(updated))

        return SaveResult.Saved(
            trackPath = trackPath.toString(),
            markingPath = markingPath.toString(),
        )
    }

    override fun loadTrack(trackId: String): LoadResult<TrackPayloadV1> =
        load(tracksDir / "$trackId.json") { payload ->
            when {
                payload.schemaVersion != CURRENT_TRACK_SCHEMA_VERSION ->
                    "unsupported schemaVersion ${payload.schemaVersion}"
                payload.track.id.isBlank() -> "missing track id"
                else -> null
            }
        }

    override fun loadTrackMarking(markingId: String): LoadResult<TrackMarkingPayloadV1> =
        load(markingsDir / "$markingId.json") { payload ->
            when {
                payload.schemaVersion != CURRENT_TRACK_SCHEMA_VERSION ->
                    "unsupported schemaVersion ${payload.schemaVersion}"
                payload.marking.id.isBlank() -> "missing marking id"
                payload.marking.samples.any { !it.latitude.isFinite() || !it.longitude.isFinite() } ->
                    "non-finite sample coordinates"
                else -> null
            }
        }

    override fun readIndex(): ReviewIndex {
        if (!fileSystem.exists(indexPath)) return ReviewIndex()
        return try {
            val text = fileSystem.read(indexPath) { readUtf8() }
            json.decodeFromString<ReviewIndex>(text)
        } catch (e: SerializationException) {
            // A corrupt index must not crash list screens; treat as empty and let a
            // subsequent save rebuild it.
            ReviewIndex()
        } catch (e: IllegalArgumentException) {
            ReviewIndex()
        }
    }

    private inline fun <reified T> load(path: Path, validate: (T) -> String?): LoadResult<T> {
        if (!fileSystem.exists(path)) return LoadResult.NotFound
        return try {
            val text = fileSystem.read(path) { readUtf8() }
            val value = json.decodeFromString<T>(text)
            val problem = validate(value)
            if (problem != null) LoadResult.Corrupt(problem) else LoadResult.Loaded(value)
        } catch (e: SerializationException) {
            LoadResult.Corrupt(e.message ?: "malformed JSON")
        } catch (e: IllegalArgumentException) {
            LoadResult.Corrupt(e.message ?: "invalid payload")
        }
    }

    private fun writeAtomically(path: Path, content: String) {
        path.parent?.let { fileSystem.createDirectories(it) }
        val tmp = path.parent!! / "${path.name}$TMP_SUFFIX"
        fileSystem.write(tmp) { writeUtf8(content) }
        fileSystem.atomicMove(tmp, path)
    }

    private fun upsertRows(index: ReviewIndex, rows: List<ReviewIndexRow>): ReviewIndex {
        val keys = rows.map { it.id to it.type }.toSet()
        val retained = index.rows.filterNot { (it.id to it.type) in keys }
        return index.copy(rows = retained + rows)
    }

    companion object {
        private const val TRACKS_DIR = "tracks"
        private const val MARKINGS_DIR = "markings"
        private const val INDEX_FILE = "index.json"
        private const val TMP_SUFFIX = ".tmp"

        /** Canonical JSON used for both saved and exported payloads (D-24). */
        val canonicalJson: Json = Json {
            prettyPrint = true
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = true
        }
    }
}
