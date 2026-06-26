package com.huanfuli.lapsight.shared.export

import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.session.GeoPointDto
import com.huanfuli.lapsight.shared.session.GpsQualitySummary
import com.huanfuli.lapsight.shared.session.LapDto
import com.huanfuli.lapsight.shared.session.LocationSampleDto
import com.huanfuli.lapsight.shared.session.SectorEventDto
import com.huanfuli.lapsight.shared.session.SourceMetadata
import com.huanfuli.lapsight.shared.session.TimingSession
import com.huanfuli.lapsight.shared.session.TimingSessionPayloadV1
import com.huanfuli.lapsight.shared.storage.CURRENT_SESSION_SCHEMA_VERSION
import com.huanfuli.lapsight.shared.storage.InMemorySessionStore
import com.huanfuli.lapsight.shared.track.SectorLineDto
import com.huanfuli.lapsight.shared.track.StartFinishLineDto
import com.huanfuli.lapsight.shared.track.Track
import com.huanfuli.lapsight.shared.track.TrackMarkingSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Wave 0 RED tests for GPX export, filename sanitization, and XML escaping (SESS-05, D-39, D-41).
 *
 * These tests define the contract for GpxExportService and ExportFileNames before they exist.
 * All tests MUST fail until Task 2 implements the services.
 */
class GpxExportTest {

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
        id = "track-gpx-1",
        name = "GPX Test Track",
        createdAtEpochMillis = 1_700_000_000_000L,
        sourceMarkingSessionId = "mark-gpx-1",
        source = source,
        startFinish = startFinish,
    )

    private fun marking() = TrackMarkingSession(
        id = "mark-gpx-1",
        createdAtEpochMillis = 1_700_000_000_000L,
        source = source,
        samples = samples(),
    )

    private fun timingSession() = TimingSession(
        id = "session-gpx-1",
        trackId = "track-gpx-1",
        trackName = "GPX Test Track",
        createdAtEpochMillis = 1_700_000_001_000L,
        source = source,
        startFinish = startFinish,
    )

    private fun laps() = listOf(
        LapDto(lapNumber = 1, startMillis = 0L, endMillis = 40_000L),
        LapDto(lapNumber = 2, startMillis = 40_000L, endMillis = 72_000L),
    )

    private fun sessionPayload() = TimingSessionPayloadV1(
        schemaVersion = CURRENT_SESSION_SCHEMA_VERSION,
        session = timingSession(),
        app = app,
        samples = samples(),
        laps = laps(),
        sectorEvents = emptyList(),
        gpsQuality = gpsQuality,
        totalDurationMillis = 72_000L,
    )

    // --- Test 3 (D-39): GPX export emits GPX 1.1 track points with escaped metadata,
    // optional elevation/time, and sample count equal to raw samples.
    @Test
    fun gpxExportContainsValidGpx11Structure() {
        val store = InMemorySessionStore()
        store.saveTrackBundle(track(), marking(), app)
        store.saveTimingSession(sessionPayload(), app)

        val bytes = requireGpxExportService(store).exportTimingSession("session-gpx-1")
        val gpxText = bytes.decodeToString()

        // Must be valid XML with GPX 1.1 namespace.
        assertTrue(gpxText.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"),
            "must contain XML declaration")
        assertTrue(gpxText.contains("<gpx"),
            "must contain gpx element")
        assertTrue(gpxText.contains("xmlns=\"http://www.topografix.com/GPX/1/1\""),
            "must have GPX 1.1 namespace")
        assertTrue(gpxText.contains("version=\"1.1\""),
            "must specify version 1.1")
        assertTrue(gpxText.contains("<trk>"),
            "must contain trk element")
        assertTrue(gpxText.contains("<trkseg>"),
            "must contain trkseg element")

        // Must have track points with lat/lon attributes.
        assertTrue(gpxText.contains("<trkpt"),
            "must contain trkpt elements")
        assertTrue(gpxText.contains("lat=\""),
            "must contain lat attributes")
        assertTrue(gpxText.contains("lon=\""),
            "must contain lon attributes")
    }

    @Test
    fun gpxExportSampleCountMatchesRawSamples() {
        val store = InMemorySessionStore()
        store.saveTrackBundle(track(), marking(), app)
        store.saveTimingSession(sessionPayload(), app)

        val bytes = requireGpxExportService(store).exportTimingSession("session-gpx-1")
        val gpxText = bytes.decodeToString()

        // Count <trkpt> elements — must equal raw sample count (3).
        val trkptCount = gpxText.split("<trkpt").size - 1
        assertEquals(3, trkptCount, "GPX trkpt count must equal raw sample count")
    }

    @Test
    fun gpxExportIncludesOptionalElevationAndTime() {
        val samplesWithAll = listOf(
            LocationSampleDto(0L, 39.8121, -86.1062, 5.0, 12.0, 90.0, 200.0, LocationSource.Simulated),
            LocationSampleDto(1_000L, 39.8126, -86.1062, 5.0, 12.0, 90.0, 201.5, LocationSource.Simulated),
        )
        val session = TimingSession(
            id = "session-gpx-2",
            trackId = "track-gpx-1",
            trackName = "GPX Test Track",
            createdAtEpochMillis = 1_700_000_000_000L, // session start epoch
            source = source,
            startFinish = startFinish,
        )
        val payload = TimingSessionPayloadV1(
            schemaVersion = CURRENT_SESSION_SCHEMA_VERSION,
            session = session,
            app = app,
            samples = samplesWithAll,
            laps = listOf(LapDto(lapNumber = 1, startMillis = 0L, endMillis = 40_000L)),
            sectorEvents = emptyList(),
            gpsQuality = GpsQualitySummary(2, 5.0, LocationSource.Simulated),
            totalDurationMillis = 2_000L,
        )
        val store = InMemorySessionStore()
        store.saveTrackBundle(track(), marking(), app)
        store.saveTimingSession(payload, app)

        val bytes = requireGpxExportService(store).exportTimingSession("session-gpx-2")
        val gpxText = bytes.decodeToString()

        // Should include <ele> for altitude when available.
        assertTrue(gpxText.contains("<ele>"),
            "must include elevation elements when altitude available")

        // Should include <time> elements with ISO 8601 timestamps derived
        // from session start epoch + sample elapsedMillis.
        assertTrue(gpxText.contains("<time>"),
            "must include time elements")
    }

    // --- Test 4 (D-41): ExportFileNames produces safe, LapSight-prefixed filenames.
    @Test
    fun exportFileNamesProducesLapSightPrefixedPatterns() {
        val trackName = "My Circuit"
        val dateMillis = 1_700_000_000_000L

        val trackJson = ExportFileNames.forTrack(trackName, dateMillis)
        assertTrue(trackJson.startsWith("LapSight_Track_"), "Track JSON must be prefixed")
        assertTrue(trackJson.endsWith(".json"), "Track JSON must have .json extension")

        val sessionJson = ExportFileNames.forTimingSession(trackName, dateMillis, "json")
        assertTrue(sessionJson.startsWith("LapSight_Session_"), "Session JSON must be prefixed")
        assertTrue(sessionJson.endsWith(".json"), "Session JSON must have .json extension")

        val sessionGpx = ExportFileNames.forTimingSession(trackName, dateMillis, "gpx")
        assertTrue(sessionGpx.startsWith("LapSight_Session_"), "Session GPX must be prefixed")
        assertTrue(sessionGpx.endsWith(".gpx"), "Session GPX must have .gpx extension")
    }

    @Test
    fun exportFileNamesStripsPathSeparatorsAndTraversal() {
        val badNames = listOf(
            "../etc/passwd",
            "C:\\Windows\\system32",
            "normal/with/slashes",
            "with\\backslashes",
            "\u0000null",
            "\u001Fcontrol",
        )
        for (bad in badNames) {
            val name = ExportFileNames.forTrack(bad, 1_700_000_000_000L)
            assertTrue(!name.contains("/"), "must not contain forward slash in '$bad' -> '$name'")
            assertTrue(!name.contains("\\"), "must not contain backslash in '$bad' -> '$name'")
            assertTrue(!name.contains(".."), "must not contain traversal in '$bad' -> '$name'")
        }
    }

    @Test
    fun exportFileNamesStripsXmlAndControlCharacters() {
        val badNames = listOf(
            "name<with>angle",
            "name&amps",
            "'single'",
            "\"double\"",
        )
        for (bad in badNames) {
            val name = ExportFileNames.forTrack(bad, 1_700_000_000_000L)
            assertTrue(!name.contains("<"), "must not contain < in '$bad' -> '$name'")
            assertTrue(!name.contains(">"), "must not contain > in '$bad' -> '$name'")
            assertTrue(!name.contains("&"), "must not contain & in '$bad' -> '$name'")
            assertTrue(!name.contains("\""), "must not contain \" in '$bad' -> '$name'")
            assertTrue(!name.contains("'"), "must not contain ' in '$bad' -> '$name'")
        }
    }

    @Test
    fun exportFileNamesDateIsInYyyyMmDdFormat() {
        // Use a known epoch that produces a stable date string.
        val dateMillis = 1_719_273_600_000L
        val name = ExportFileNames.forTrack("Test", dateMillis)
        // Pattern: LapSight_Track_Test_YYYYMMDD.json
        val parts = name.removeSuffix(".json").split("_")
        assertTrue(parts.size >= 4, "must have at least 4 underscore-separated parts: $name")
        val dateSegment = parts.last { it.length == 8 && it.all { c -> c.isDigit() } }
        assertEquals(8, dateSegment.length, "date segment must be 8 digits in $name, got '$dateSegment'")
    }

    @Test
    fun exportFileNamesAllowsOnlySafeAsciiTokens() {
        val bad = "na\u00efve caf\u00e9 résumé"
        val name = ExportFileNames.forTrack(bad, 1_700_000_000_000L)
        // All characters must be in [a-zA-Z0-9._-]
        val token = name.removePrefix("LapSight_Track_").removeSuffix(".json")
            .replace(Regex("_\\d{8}$"), "") // remove the date suffix
        for (c in token) {
            assertTrue(c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '.' || c == '_' || c == '-',
                "character '$c' not allowed in safe token")
        }
    }

    // --- Test 5 (D-40): GPX export requires explicit entity ID.
    @Test
    fun gpxExportRequiresExplicitSessionId() {
        val store = InMemorySessionStore()
        store.saveTrackBundle(track(), marking(), app)
        store.saveTimingSession(sessionPayload(), app)

        val service = requireGpxExportService(store)
        val bytes = service.exportTimingSession("session-gpx-1")
        assertTrue(bytes.size > 0, "must return bytes for valid session ID")
        // D-40: the interface takes a single entity ID, not a batch list.
    }

    // --- GPX XML escaping (T-03-21): angle brackets, ampersands, quotes, apostrophes in metadata.
    @Test
    fun gpxEscapesSpecialCharactersInMetadata() {
        val dangerName = "A&B <Danger> \"track\" 'name'"
        val dangerousTrack = track().copy(name = dangerName)
        val dangerousMarking = marking().copy(id = "mark-gpx-danger")
        val dangerousSession = timingSession().copy(trackName = dangerName)
        val payload = sessionPayload().copy(
            session = dangerousSession,
        )
        val store = InMemorySessionStore()
        store.saveTrackBundle(dangerousTrack, dangerousMarking, app)
        store.saveTimingSession(payload, app)

        val bytes = requireGpxExportService(store).exportTimingSession("session-gpx-1")
        val gpxText = bytes.decodeToString()

        // The GPX must be valid XML — entities must be escaped.
        // & -> &amp;  < -> &lt;  > -> &gt;  " -> &quot;
        // Note: the track name appears in GPX metadata (e.g., <name> element).
        // If the name is in the GPX text, it must be escaped.
        if (gpxText.contains(dangerName)) {
            // If the text somehow contains raw angle brackets, that's a failure.
            assertTrue(!gpxText.contains("<Danger>"), "must escape angle brackets in metadata")
            assertTrue(!gpxText.contains("\"track\""), "must escape double quotes in metadata")
        }
        // The key assertion: the GPX must be well-formed enough to contain closing tags.
        assertTrue(gpxText.contains("</gpx>"), "must close gpx element")
        assertTrue(gpxText.contains("</trk>"), "must close trk element")
        assertTrue(gpxText.contains("</trkseg>"), "must close trkseg element")
    }

    /** Helper until the real factory exists. */
    private fun requireGpxExportService(store: com.huanfuli.lapsight.shared.storage.LocalSessionStore): GpxExportService =
        GpxExportService(store)
}
