package com.huanfuli.lapsight.shared.storage

import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.session.GhostCandidate
import com.huanfuli.lapsight.shared.session.GhostReferencePayloadV1
import com.huanfuli.lapsight.shared.session.GpsQualitySummary
import com.huanfuli.lapsight.shared.session.LapDto
import com.huanfuli.lapsight.shared.session.LocationSampleDto
import com.huanfuli.lapsight.shared.session.SectorEventDto
import com.huanfuli.lapsight.shared.session.SourceMetadata
import com.huanfuli.lapsight.shared.session.TimingSession
import com.huanfuli.lapsight.shared.session.TimingSessionPayloadV1
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
    private val sessionsDir: Path get() = root / SESSIONS_DIR
    private val draftsDir: Path get() = root / DRAFTS_DIR
    private val referencesDir: Path get() = root / REFERENCES_DIR
    private val indexPath: Path get() = root / INDEX_FILE
    private val activeDraftPath: Path get() = draftsDir / ACTIVE_DRAFT_FILE

    /** Per-Track reference file, separated by source boundary (D-03, D-04). */
    private fun referencePath(trackId: String, isSimulated: Boolean): Path =
        referencesDir / "${trackId}__${if (isSimulated) SIM_SLOT else REAL_SLOT}.json"

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

    // --- Timing session drafts (D-13..D-20) --------------------------------

    override fun saveTimingDraft(
        session: TimingSession,
        samples: List<LocationSampleDto>,
        laps: List<LapDto>,
        sectorEvents: List<SectorEventDto>,
        gpsQuality: GpsQualitySummary,
        totalDurationMillis: Long,
        app: AppMetadata,
    ) {
        val payload = TimingSessionPayloadV1(
            session = session,
            app = app,
            samples = samples,
            laps = laps,
            sectorEvents = sectorEvents,
            gpsQuality = gpsQuality,
            totalDurationMillis = totalDurationMillis,
        )
        // Drafts persist continuously (D-13). Atomic temp-write + move avoids
        // partial-write corruption on crash (Pitfall 2).
        writeAtomically(activeDraftPath, json.encodeToString(payload))
    }

    override fun loadUnfinishedDraft(): TimingSessionPayloadV1? {
        if (!fileSystem.exists(activeDraftPath)) return null
        return try {
            val text = fileSystem.read(activeDraftPath) { readUtf8() }
            json.decodeFromString<TimingSessionPayloadV1>(text)
        } catch (e: SerializationException) {
            // Corrupt/missing draft must not crash recovery (T-03-11).
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    override fun saveTimingSession(payload: TimingSessionPayloadV1, app: AppMetadata): SaveResult {
        val sessionPath = sessionsDir / "${payload.session.id}.json"
        // Canonical payload first (D-22, D-23), then the index row.
        writeAtomically(sessionPath, json.encodeToString(payload))
        val updated = upsertRows(
            readIndex(),
            listOf(
                ReviewIndexRow(
                    id = payload.session.id,
                    type = ReviewEntryType.TimingSession,
                    name = payload.session.trackName,
                    createdAtEpochMillis = payload.session.createdAtEpochMillis,
                    source = payload.session.source,
                    payloadPath = "$SESSIONS_DIR/${payload.session.id}.json",
                    sampleCount = payload.samples.size,
                    bestLapMillis = payload.laps.minByOrNull { it.durationMillis }?.durationMillis,
                ),
            ),
        )
        writeAtomically(indexPath, json.encodeToString(updated))
        // Clear the draft now that the canonical session is saved (D-14).
        if (fileSystem.exists(activeDraftPath)) {
            fileSystem.delete(activeDraftPath)
        }
        return SaveResult.Saved(
            trackPath = sessionPath.toString(),
            markingPath = activeDraftPath.toString(),
        )
    }

    override fun discardTimingDraft() {
        if (fileSystem.exists(activeDraftPath)) {
            fileSystem.delete(activeDraftPath)
        }
    }

    override fun loadTimingSession(sessionId: String): LoadResult<TimingSessionPayloadV1> =
        load(sessionsDir / "$sessionId.json") { payload ->
            when {
                payload.schemaVersion != CURRENT_SESSION_SCHEMA_VERSION ->
                    "unsupported schemaVersion ${payload.schemaVersion}"
                payload.session.id.isBlank() -> "missing session id"
                payload.session.trackId.isBlank() -> "missing track id"
                else -> null
            }
        }

    override fun ghostCandidateForTrack(trackId: String): GhostCandidate? {
        val index = readIndex()
        val sessionIds = index.rows
            .filter {
                it.type == ReviewEntryType.TimingSession &&
                    it.name.isNotBlank() &&
                    // The index row does not carry trackId directly; we resolve
                    // candidates by scanning saved payloads and filtering on
                    // trackId + source. isSimulated lives on SourceMetadata.
                    true
            }
            .map { it.id }

        var best: GhostCandidate? = null
        for (id in sessionIds) {
            val payload = loadTimingSession(id)
            if (payload !is LoadResult.Loaded) continue
            val value = payload.value
            if (value.session.trackId != trackId) continue
            // Real candidates EXCLUDE simulated sessions (D-20, D-43).
            if (value.session.source.isSimulated) continue
            val bestLap = value.laps.minByOrNull { it.durationMillis } ?: continue
            val candidate = GhostCandidate(
                trackId = trackId,
                sessionId = value.session.id,
                lapNumber = bestLap.lapNumber,
                lapDurationMillis = bestLap.durationMillis,
            )
            if (best == null || candidate.lapDurationMillis < best!!.lapDurationMillis) {
                best = candidate
            }
        }
        return best
    }

    override fun loadReferenceLap(trackId: String, isSimulated: Boolean): LoadResult<GhostReferencePayloadV1> =
        load(referencePath(trackId, isSimulated)) { payload ->
            when {
                payload.schemaVersion != CURRENT_GHOST_REFERENCE_SCHEMA_VERSION ->
                    "unsupported schemaVersion ${payload.schemaVersion}"
                payload.trackId.isBlank() -> "missing track id"
                payload.trackId != trackId -> "track id mismatch"
                // Defense in depth: the stored source must match the requested
                // real/simulated boundary so a real lookup never returns a
                // simulated reference even if a file was misplaced (D-04, D-24).
                payload.source.isSimulated != isSimulated -> "source boundary mismatch"
                payload.progressPoints.size < 2 -> "reference progress curve too short"
                else -> null
            }
        }

    override fun saveReferenceLap(payload: GhostReferencePayloadV1, app: AppMetadata): SaveResult {
        val path = referencePath(payload.trackId, payload.source.isSimulated)
        writeAtomically(path, json.encodeToString(payload))
        return SaveResult.Saved(trackPath = path.toString(), markingPath = path.toString())
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
        private const val SESSIONS_DIR = "sessions"
        private const val DRAFTS_DIR = "drafts"
        private const val REFERENCES_DIR = "references"
        private const val ACTIVE_DRAFT_FILE = "timing.json"
        private const val INDEX_FILE = "index.json"
        private const val TMP_SUFFIX = ".tmp"

        /** Filename slot suffixes keeping real and simulated references apart (D-04). */
        private const val REAL_SLOT = "real"
        private const val SIM_SLOT = "sim"

        /** Canonical JSON used for both saved and exported payloads (D-24). */
        val canonicalJson: Json = Json {
            prettyPrint = true
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = true
        }
    }
}
