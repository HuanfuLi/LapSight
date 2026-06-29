package com.huanfuli.lapsight.shared.session

import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.lap.LapEngineConfig
import com.huanfuli.lapsight.shared.lap.LocalProjection
import com.huanfuli.lapsight.shared.lap.ReplayFixtures
import com.huanfuli.lapsight.shared.lap.ReplayRunner
import com.huanfuli.lapsight.shared.storage.FileSessionStore
import com.huanfuli.lapsight.shared.track.CourseDirection
import com.huanfuli.lapsight.shared.track.SectorLineDto
import com.huanfuli.lapsight.shared.track.StartFinishLineDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Round-trip + bad-input coverage for [SessionReplayDecoder] (D-25, D-28,
 * threat T-5.1-01).
 *
 * Determinism is the spine of the Phase 5.1 Go / Hardening-Required / No-Go
 * decision: an exported field session must decode back into the SAME engine and
 * produce the SAME algorithmic output, and malformed bytes must be data
 * (Corrupt / NoCourse), never a thrown serialization exception. Pure
 * `kotlin.test` + shared domain types only — no Compose/platform/Okio.
 */
class SessionReplayDecoderTest {

    private val mpd = LocalProjection.METERS_PER_DEGREE
    private fun lat(northMeters: Double) = northMeters / mpd
    private fun lon(eastMeters: Double) = eastMeters / mpd

    /** A DTO course geometrically identical to [ReplayFixtures.DEMO_COURSE]. */
    private fun startFinishDto() = StartFinishLineDto(
        pointA = GeoPointDto(latitude = lat(-25.0), longitude = lon(0.0)),
        pointB = GeoPointDto(latitude = lat(25.0), longitude = lon(0.0)),
    )

    private fun sectorDtos() = listOf(
        SectorLineDto(
            id = "S1",
            name = "Sector 1",
            order = 0,
            pointA = GeoPointDto(latitude = lat(-25.0), longitude = lon(60.0)),
            pointB = GeoPointDto(latitude = lat(25.0), longitude = lon(60.0)),
        ),
        SectorLineDto(
            id = "S2",
            name = "Sector 2",
            order = 1,
            pointA = GeoPointDto(latitude = lat(-25.0), longitude = lon(120.0)),
            pointB = GeoPointDto(latitude = lat(25.0), longitude = lon(120.0)),
        ),
    )

    private fun payloadOf(
        startFinish: StartFinishLineDto,
        sectors: List<SectorLineDto>,
        sampleDtos: List<LocationSampleDto>,
    ): TimingSessionPayloadV1 {
        val session = TimingSession(
            id = "session-1",
            trackId = "track-1",
            trackName = "Decoder Round-Trip",
            createdAtEpochMillis = 0L,
            source = SourceMetadata(source = LocationSource.Simulated, isSimulated = true),
            startFinish = startFinish,
            sectors = sectors,
            direction = CourseDirection.Recorded,
        )
        return TimingSessionPayloadV1(
            session = session,
            app = AppMetadata(appVersion = "test"),
            samples = sampleDtos,
            laps = emptyList(),
            sectorEvents = emptyList(),
            gpsQuality = gpsQualitySummaryOf(sampleDtos, LocationSource.Simulated),
        )
    }

    private fun encode(payload: TimingSessionPayloadV1): ByteArray =
        FileSessionStore.canonicalJson
            .encodeToString(TimingSessionPayloadV1.serializer(), payload)
            .encodeToByteArray()

    @Test
    fun decodedReplayEqualsDirectReplayOfSameSamples() {
        // Use the same lenient config on BOTH paths so the comparison isolates the
        // decode path, not engine tuning.
        val config = LapEngineConfig.lenientForTests()
        val domainSamples = ReplayFixtures.multiLapLoop()
        val sampleDtos = domainSamples.map { it.toDto() }

        // Direct run rebuilds the course via the SAME helper the decoder uses.
        val course = courseFromTrack(startFinishDto(), sectorDtos(), CourseDirection.Recorded)
            ?: fail("expected a course from a confirmed start/finish")
        val direct = ReplayRunner(course, config).run(domainSamples)

        val bytes = encode(payloadOf(startFinishDto(), sectorDtos(), sampleDtos))
        val decoded = SessionReplayDecoder.decodeForReplay(bytes, config)

        val replay = (decoded as? ReplayDecodeResult.Decoded)
            ?: fail("expected Decoded, got $decoded")

        assertTrue(direct.finalState.lapCount > 0, "fixture must complete laps to be meaningful")
        assertEquals(
            direct.finalState.lapCount,
            replay.result.finalState.lapCount,
            "decoded lap count must equal a direct ReplayRunner run (D-25/D-28)",
        )
        assertEquals(
            direct.sectorEvents,
            replay.result.sectorEvents,
            "decoded sector events must structurally equal a direct run (D-26)",
        )
        assertEquals(
            direct.finalState,
            replay.result.finalState,
            "decoded final timing state must equal a direct run",
        )
    }

    @Test
    fun garbageBytesReturnCorruptNotException() {
        val result = SessionReplayDecoder.decodeForReplay("not-a-valid-payload".encodeToByteArray())
        assertTrue(
            result is ReplayDecodeResult.Corrupt,
            "malformed bytes must be data, not a thrown exception (T-5.1-01); got $result",
        )
    }

    @Test
    fun truncatedJsonReturnsCorrupt() {
        val bytes = encode(payloadOf(startFinishDto(), sectorDtos(), emptyList()))
        val truncated = bytes.copyOfRange(0, bytes.size / 2)
        val result = SessionReplayDecoder.decodeForReplay(truncated)
        assertTrue(
            result is ReplayDecodeResult.Corrupt,
            "truncated export must decode to Corrupt, not throw; got $result",
        )
    }

    @Test
    fun degenerateStartFinishReturnsNoCourse() {
        // pointA == pointB is a zero-length line: it cannot define a crossing, so
        // the export carries no confirmed course (D-19).
        val degenerate = StartFinishLineDto(
            pointA = GeoPointDto(latitude = lat(0.0), longitude = lon(0.0)),
            pointB = GeoPointDto(latitude = lat(0.0), longitude = lon(0.0)),
        )
        val bytes = encode(payloadOf(degenerate, emptyList(), emptyList()))
        val result = SessionReplayDecoder.decodeForReplay(bytes)
        assertEquals(
            ReplayDecodeResult.NoCourse,
            result,
            "a payload without a usable start/finish line must return NoCourse",
        )
    }
}
