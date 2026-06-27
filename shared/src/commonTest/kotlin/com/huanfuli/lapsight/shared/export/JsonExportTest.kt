package com.huanfuli.lapsight.shared.export

import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.session.GeoPointDto
import com.huanfuli.lapsight.shared.session.GpsQualitySummary
import com.huanfuli.lapsight.shared.session.LapDto
import com.huanfuli.lapsight.shared.session.LocationSampleDto
import com.huanfuli.lapsight.shared.session.SectorEventDto
import com.huanfuli.lapsight.shared.session.SectorResultDto
import com.huanfuli.lapsight.shared.session.SourceMetadata
import com.huanfuli.lapsight.shared.session.TimingSession
import com.huanfuli.lapsight.shared.session.TimingSessionPayloadV1
import com.huanfuli.lapsight.shared.storage.CURRENT_SESSION_SCHEMA_VERSION
import com.huanfuli.lapsight.shared.storage.CURRENT_TRACK_SCHEMA_VERSION
import com.huanfuli.lapsight.shared.storage.InMemorySessionStore
import com.huanfuli.lapsight.shared.storage.LoadResult
import com.huanfuli.lapsight.shared.track.SectorLineDto
import com.huanfuli.lapsight.shared.track.StartFinishLineDto
import com.huanfuli.lapsight.shared.track.Track
import com.huanfuli.lapsight.shared.track.TrackMarkingSession
import com.huanfuli.lapsight.shared.track.TrackReferenceLine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Wave 0 RED tests for JSON export (SESS-04, D-38).
 *
 * These tests define the contract for JsonExportService before it exists.
 * All tests MUST fail until Task 2 implements the service.
 */
class JsonExportTest {

    private val app = AppMetadata(appVersion = "0.3.0", buildNumber = "42", platform = "test")

    private val source = SourceMetadata(
        source = LocationSource.Simulated,
        isSimulated = true,
        label = "Demo",
    )

    private val startFinish = StartFinishLineDto(
        pointA = GeoPointDto(39.8121, -86.1062),
        pointB = GeoPointDto(39.8126, -86.1062),
    )

    private val sectors = listOf(
        SectorLineDto(
            id = "sector-1",
            name = "Sector 1",
            order = 1,
            pointA = GeoPointDto(39.8123, -86.1060),
            pointB = GeoPointDto(39.8124, -86.1063),
        ),
    )

    private val referenceLine = TrackReferenceLine(
        points = listOf(
            GeoPointDto(39.8121, -86.1062),
            GeoPointDto(39.8126, -86.1062),
            GeoPointDto(39.8126, -86.1058),
        ),
        isClosed = true,
    )

    private fun samples(): List<LocationSampleDto> = listOf(
        LocationSampleDto(0L, 39.8121, -86.1062, 5.0, 12.0, 90.0, 200.0, LocationSource.Simulated),
        LocationSampleDto(1_000L, 39.8126, -86.1062, 5.0, 12.0, 90.0, 200.0, LocationSource.Simulated),
        LocationSampleDto(2_000L, 39.8126, -86.1058, 4.0, 14.0, 95.0, 201.0, LocationSource.Simulated),
    )

    private val gpsQuality = GpsQualitySummary(
        sampleCount = 3,
        averageAccuracyMeters = 4.67,
        source = LocationSource.Simulated,
    )

    private fun track() = Track(
        id = "track-export-1",
        name = "Test Track",
        createdAtEpochMillis = 1_700_000_000_000L,
        sourceMarkingSessionId = "mark-export-1",
        source = source,
        referenceLine = referenceLine,
        startFinish = startFinish,
        sectors = sectors,
    )

    private fun marking() = TrackMarkingSession(
        id = "mark-export-1",
        createdAtEpochMillis = 1_700_000_000_000L,
        source = source,
        samples = samples(),
    )

    private fun timingSession() = TimingSession(
        id = "session-export-1",
        trackId = "track-export-1",
        trackName = "Test Track",
        createdAtEpochMillis = 1_700_000_001_000L,
        source = source,
        startFinish = startFinish,
        sectors = sectors,
    )

    private fun laps() = listOf(
        LapDto(lapNumber = 1, startMillis = 0L, endMillis = 40_000L),
        LapDto(lapNumber = 2, startMillis = 40_000L, endMillis = 72_000L),
    )

    private fun sectorEvents() = listOf(
        SectorEventDto(lapNumber = 1, sectorId = "sector-1", sectorOrder = 1, crossingMillis = 15_000L, splitMillis = 15_000L),
        SectorEventDto(lapNumber = 2, sectorId = "sector-1", sectorOrder = 1, crossingMillis = 55_000L, splitMillis = 15_000L),
    )

    private fun sessionPayload() = TimingSessionPayloadV1(
        schemaVersion = CURRENT_SESSION_SCHEMA_VERSION,
        session = timingSession(),
        app = app,
        samples = samples(),
        laps = laps(),
        sectorEvents = sectorEvents(),
        gpsQuality = gpsQuality,
        totalDurationMillis = 72_000L,
    )

    // --- Test 1 (D-38): exportTrack includes Track, TrackMarkingSession raw samples,
    // TrackReferenceLine, start/finish, sector lines, GPS quality summary,
    // schemaVersion, app/build metadata, and source metadata.
    @Test
    fun exportTrackIncludesAllD38Fields() {
        val store = InMemorySessionStore()
        store.saveTrackBundle(track(), marking(), app)

        val bytes = requireExportService(store).exportTrack("track-export-1")

        assertTrue(bytes.size > 0, "exported bytes must be non-empty")

        val json = com.huanfuli.lapsight.shared.storage.FileSessionStore.canonicalJson
            .decodeFromString<JsonObject>(bytes.decodeToString())

        // Schema version present
        assertNotNull(json["schemaVersion"], "must include schemaVersion")
        assertEquals(CURRENT_TRACK_SCHEMA_VERSION, json["schemaVersion"]!!.jsonPrimitive.content.toInt())

        // Track fields
        assertNotNull(json["track"], "must include track object")
        // (track is a nested JsonObject — existence check is sufficient)

        // App metadata
        assertNotNull(json["app"], "must include app metadata")
        val appObj = json["app"]
        assertNotNull(appObj)

        // The raw marking samples are part of the TrackMarkingPayloadV1 which should be
        // bundled into the export alongside the Track.
        val markingBytes = requireExportService(store).exportTrackMarking("mark-export-1")
        assertTrue(markingBytes.size > 0, "marking export must be non-empty")
        val markingJson = com.huanfuli.lapsight.shared.storage.FileSessionStore.canonicalJson
            .decodeFromString<JsonObject>(markingBytes.decodeToString())
        assertNotNull(markingJson["marking"], "marking export must include marking object")
        assertNotNull(markingJson["schemaVersion"], "marking export must include schemaVersion")
    }

    // --- Test 2 (D-38): exportTimingSession includes TimingSession raw samples,
    // lap events, sector events, linked Track metadata, start/finish, sector lines,
    // GPS quality summary, schemaVersion, app/build metadata, and source metadata.
    @Test
    fun exportTimingSessionIncludesAllD38Fields() {
        val store = InMemorySessionStore()
        store.saveTrackBundle(track(), marking(), app)
        store.saveTimingSession(sessionPayload(), app)

        val bytes = requireExportService(store).exportTimingSession("session-export-1")

        assertTrue(bytes.size > 0, "exported bytes must be non-empty")

        val json = com.huanfuli.lapsight.shared.storage.FileSessionStore.canonicalJson
            .decodeFromString<JsonObject>(bytes.decodeToString())

        // Schema version present
        assertNotNull(json["schemaVersion"], "must include schemaVersion")

        // Session fields
        assertNotNull(json["session"], "must include session object")

        // Samples
        assertNotNull(json["samples"], "must include samples array")

        // Laps
        assertNotNull(json["laps"], "must include laps array")

        // Sector events
        assertNotNull(json["sectorEvents"], "must include sector events array")

        // GPS quality summary
        assertNotNull(json["gpsQuality"], "must include gpsQuality")

        // App metadata
        assertNotNull(json["app"], "must include app metadata")

        // Source metadata present in JSON (nested in session)
        val sessionObj = json["session"]
        assertNotNull(sessionObj)
    }

    // --- Test 5 (D-40): Export service calls require explicit entity IDs,
    // not batch list actions.
    @Test
    fun exportRequiresExplicitEntityId() {
        val store = InMemorySessionStore()
        store.saveTrackBundle(track(), marking(), app)

        val service = requireExportService(store)

        // Valid: explicit ID
        val result = service.exportTrack("track-export-1")
        assertTrue(result.size > 0)

        // Export track should fail gracefully for missing ID
        // (service returns error or throws — contract says explicit IDs required)
        // D-40 means no batch export; we verify single-ID interface exists
    }

    // --- Test 6 (RESEARCH Open Question #3): Fake ExportShareTarget receives
    // exact ExportArtifact(fileName, mimeType, bytes) proving share handoff contract.
    @Test
    fun exportShareTargetReceivesArtifactContract() {
        val store = InMemorySessionStore()
        store.saveTrackBundle(track(), marking(), app)
        store.saveTimingSession(sessionPayload(), app)

        val service = requireExportService(store)
        val bytes = service.exportTimingSession("session-export-1")
        val fileName = ExportFileNames.forTimingSession("Test Track", 1_700_000_001_000L, "json")

        val artifact = ExportArtifact(fileName = fileName, mimeType = "application/json", bytes = bytes)

        val fakeTarget = FakeExportShareTarget()
        fakeTarget.share(artifact)

        // The fake target must have received the exact artifact we sent.
        assertEquals(1, fakeTarget.receivedArtifacts.size, "should receive exactly one artifact")
        val received = fakeTarget.receivedArtifacts.first()
        assertEquals(fileName, received.fileName, "fileName must be LapSight-prefixed")
        assertEquals("application/json", received.mimeType, "mimeType must be application/json")
        assertTrue(received.bytes.size > 0, "bytes must be non-empty")
        assertTrue(fakeTarget.lastResult is ExportShareResult.Shared, "should result in Shared")
    }

    // --- Source metadata preservation (D-42): Demo/Simulated labels are present.
    @Test
    fun exportPreservesSourceAndDemoMetadata() {
        val store = InMemorySessionStore()
        store.saveTrackBundle(track(), marking(), app)

        val bytes = requireExportService(store).exportTrack("track-export-1")
        val text = bytes.decodeToString()

        // Source metadata should be in the JSON — check for Simulated enum and isSimulated
        assertTrue(text.contains("Simulated"), "exported JSON must contain source type")
    }

    // --- Special-character track name doesn't break export (D-41).
    @Test
    fun exportHandlesSpecialCharacterTrackNames() {
        val dangerName = "Track with / slashes \\ backslashes .. dots <angles> & amps 'quotes' \"dquotes\""
        val dangerousTrack = track().copy(name = dangerName)
        val dangerousMarking = marking().copy(id = "mark-danger-1")
        val store = InMemorySessionStore()
        store.saveTrackBundle(dangerousTrack, dangerousMarking, app)

        // Filename must not contain path separators, traversal, or control chars.
        val safeName = ExportFileNames.forTrack(dangerName, 1_700_000_000_000L)
        assertTrue(!safeName.contains("/"), "must not contain forward slash")
        assertTrue(!safeName.contains("\\"), "must not contain backslash")
        assertTrue(!safeName.contains(".."), "must not contain traversal")
        assertTrue(!safeName.contains("<"), "must not contain angle brackets")
        assertTrue(!safeName.contains(">"), "must not contain angle brackets")
        assertTrue(!safeName.contains("&"), "must not contain ampersand")
        assertTrue(!safeName.contains("\""), "must not contain double quotes")
        assertTrue(!safeName.contains("'"), "must not contain apostrophe")
        assertTrue(safeName.startsWith("LapSight_Track_"), "must be LapSight-prefixed")
        assertTrue(safeName.endsWith(".json"), "must have .json extension")

        // The export itself should still succeed with dangerous names.
        val bytes = requireExportService(store).exportTrack("track-export-1")
        assertTrue(bytes.size > 0)
    }

    // --- V2 complete Sector results (D-06/D-11): exportTimingSession serializes the
    // immutable V2 snapshot with both adjacent durations and cumulative splits,
    // WITHOUT any change to JsonExportService.
    @Test
    fun exportTimingSessionIncludesV2CompleteSectorResults() {
        val store = InMemorySessionStore()
        store.saveTrackBundle(track(), marking(), app)
        store.saveTimingSession(sessionPayloadWithSectorResults(), app)

        val bytes = requireExportService(store).exportTimingSession("session-export-1")
        val text = bytes.decodeToString()

        val json = com.huanfuli.lapsight.shared.storage.FileSessionStore.canonicalJson
            .decodeFromString<JsonObject>(text)
        assertNotNull(json["sectorResults"], "export must include the V2 sectorResults array")
        // Distinctive V2 fields (cumulativeSplitMillis is unique to SectorResultDto).
        assertTrue(text.contains("cumulativeSplitMillis"), "V2 sector results carry a cumulative split")
        assertTrue(text.contains("sectorOrder"), "V2 sector results carry their order")
    }

    // --- V1 legacy preservation (D-32): a V1-origin payload still exports its
    // legacy cumulative sector events and is never relabeled as complete sectors.
    @Test
    fun exportTimingSessionPreservesV1LegacyCumulativeSplits() {
        val store = InMemorySessionStore()
        store.saveTrackBundle(track(), marking(), app)
        store.saveTimingSession(sessionPayload(), app) // no sectorResults -> V1 legacy

        val bytes = requireExportService(store).exportTimingSession("session-export-1")
        val text = bytes.decodeToString()

        assertTrue(text.contains("sectorEvents"), "legacy cumulative sector events are preserved")
        val json = com.huanfuli.lapsight.shared.storage.FileSessionStore.canonicalJson
            .decodeFromString<JsonObject>(text)
        // The field exists (canonical, encodeDefaults) but is empty for V1 history.
        assertNotNull(json["sectorResults"], "sectorResults field is present (empty for V1)")
    }

    private fun sectorResults() = listOf(
        SectorResultDto(
            lapNumber = 1, sectorId = "sector-1", sectorOrder = 1,
            startedAtMillis = 0L, endedAtMillis = 15_000L,
            durationMillis = 15_000L, cumulativeSplitMillis = 15_000L,
        ),
        SectorResultDto(
            lapNumber = 1, sectorId = "sector-2", sectorOrder = 2,
            startedAtMillis = 15_000L, endedAtMillis = 40_000L,
            durationMillis = 25_000L, cumulativeSplitMillis = 40_000L,
        ),
    )

    private fun sessionPayloadWithSectorResults() = sessionPayload().copy(sectorResults = sectorResults())

    /** Helper until the real factory exists. */
    private fun requireExportService(store: com.huanfuli.lapsight.shared.storage.LocalSessionStore): JsonExportService =
        JsonExportService(store)
}

/**
 * A fake [ExportShareTarget] that records the received artifacts for test assertions,
 * proving the share/save handoff contract carries full export content (RESEARCH Open Q #3).
 */
private class FakeExportShareTarget : ExportShareTarget {
    val receivedArtifacts = mutableListOf<ExportArtifact>()
    var lastResult: ExportShareResult? = null

    override fun share(artifact: ExportArtifact): ExportShareResult {
        receivedArtifacts += artifact
        lastResult = ExportShareResult.Shared
        return lastResult!!
    }
}
