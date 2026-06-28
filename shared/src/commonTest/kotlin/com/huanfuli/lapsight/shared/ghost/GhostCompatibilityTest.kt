package com.huanfuli.lapsight.shared.ghost

import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.session.GeoPointDto
import com.huanfuli.lapsight.shared.session.GhostReferencePayloadV1
import com.huanfuli.lapsight.shared.session.LocationSampleDto
import com.huanfuli.lapsight.shared.session.ProgressPointDto
import com.huanfuli.lapsight.shared.session.SourceMetadata
import com.huanfuli.lapsight.shared.session.toReferenceLap
import com.huanfuli.lapsight.shared.track.CourseDirection
import com.huanfuli.lapsight.shared.track.CourseSnapshot
import com.huanfuli.lapsight.shared.track.GhostReferencePayloadV2
import com.huanfuli.lapsight.shared.track.StartFinishLineDto
import com.huanfuli.lapsight.shared.track.TrackReferenceLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Phase 5 Plan 10 compatibility gate.
 *
 * This is intentionally wider than storage lookup: it proves the pure reference
 * identity contract before persistence and course matching switch to V2 slots.
 */
class GhostCompatibilityTest {

    @Test
    fun sectorOnlyRevisionSharesKeyButGeometryEditAndDuplicateDoNot() {
        val referenceLine = referenceLine()
        val recorded = snapshot(
            profileId = "profile-a",
            revisionId = "profile-a:r1",
            geometryCompatibilityId = "profile-a:g1",
            referenceLine = referenceLine,
        )
        val sectorOnly = snapshot(
            profileId = "profile-a",
            revisionId = "profile-a:r2",
            geometryCompatibilityId = "profile-a:g1",
            referenceLine = referenceLine,
        )
        val geometryEdit = snapshot(
            profileId = "profile-a",
            revisionId = "profile-a:r3",
            geometryCompatibilityId = "profile-a:g2",
            referenceLine = referenceLine,
        )
        val duplicate = snapshot(
            profileId = "profile-copy",
            revisionId = "profile-copy:r1",
            geometryCompatibilityId = "profile-copy:g1",
            referenceLine = referenceLine,
        )

        val key = recorded.compatibilityKey(isSimulated = false)
        assertEquals(key, sectorOnly.compatibilityKey(isSimulated = false))
        assertNotEquals(key, geometryEdit.compatibilityKey(isSimulated = false))
        assertNotEquals(key, duplicate.compatibilityKey(isSimulated = false))
    }

    @Test
    fun fasterOfOnlyComparesReferencesWithEqualCompatibilityKey() {
        val base = key()
        val incumbent = reference(durationMillis = 40_000L, compatibilityKey = base)

        assertEquals(
            reference(durationMillis = 32_000L, compatibilityKey = base, sessionId = "faster-same-key"),
            ReferenceLapSelector.fasterOf(
                incumbent,
                reference(durationMillis = 32_000L, compatibilityKey = base, sessionId = "faster-same-key"),
            ),
            "strictly faster references may replace the incumbent only inside the same key",
        )

        val equalDuration = reference(durationMillis = 40_000L, compatibilityKey = base, sessionId = "tie")
        assertTrue(
            ReferenceLapSelector.fasterOf(incumbent, equalDuration) === incumbent,
            "equal-duration candidate must preserve incumbent when keys match",
        )

        val fasterMismatches = listOf(
            reference(
                durationMillis = 20_000L,
                compatibilityKey = base.copy(profileId = "profile-duplicate"),
                sessionId = "profile-mismatch",
            ),
            reference(
                durationMillis = 20_000L,
                compatibilityKey = base.copy(geometryCompatibilityId = "profile-a:g2"),
                sessionId = "geometry-mismatch",
            ),
            reference(
                durationMillis = 20_000L,
                compatibilityKey = base.copy(direction = CourseDirection.Reverse),
                sessionId = "direction-mismatch",
            ),
            reference(
                durationMillis = 20_000L,
                compatibilityKey = base.copy(isSimulated = true),
                sessionId = "source-mismatch",
            ),
        )

        fasterMismatches.forEach { mismatch ->
            assertTrue(
                ReferenceLapSelector.fasterOf(incumbent, mismatch) === incumbent,
                "a faster ${mismatch.sessionId} candidate must not cross compatibility keys",
            )
        }
    }

    @Test
    fun validationRejectsUnsafeIdsAndCrossFieldMismatches() {
        val snapshot = snapshot(
            profileId = "profile-a",
            revisionId = "profile-a:r1",
            geometryCompatibilityId = "profile-a:g1",
            direction = CourseDirection.Recorded,
        )
        val validKey = snapshot.compatibilityKey(isSimulated = false)

        assertEquals(
            CourseCompatibilityValidation.Valid,
            GhostCompatibility.validateSnapshotKey(snapshot, validKey),
        )
        assertIs<CourseCompatibilityValidation.Invalid>(
            GhostCompatibility.validateSnapshotKey(snapshot, validKey.copy(profileId = "other-profile")),
        )
        assertIs<CourseCompatibilityValidation.Invalid>(
            GhostCompatibility.validateSnapshotKey(snapshot, validKey.copy(geometryCompatibilityId = "profile-a:g2")),
        )
        assertIs<CourseCompatibilityValidation.Invalid>(
            GhostCompatibility.validateSnapshotKey(snapshot, validKey.copy(direction = CourseDirection.Reverse)),
        )
        assertIs<CourseCompatibilityValidation.Invalid>(
            GhostCompatibility.validateKey(validKey.copy(profileId = "../profile")),
        )
        assertIs<CourseCompatibilityValidation.Invalid>(
            GhostCompatibility.validateKey(validKey.copy(geometryCompatibilityId = "bad/geometry")),
        )

        val sourceMismatchPayload = referencePayloadV2(
            compatibilityKey = validKey,
            source = SourceMetadata(LocationSource.Simulated, isSimulated = true, label = "Demo"),
        )
        assertIs<CourseCompatibilityValidation.Invalid>(
            GhostCompatibility.validateReferencePayload(sourceMismatchPayload),
        )
    }

    @Test
    fun v1ReferenceMigrationUsesRecordedDirectionAndOriginalSourceSlot() {
        val real = referencePayloadV1(
            trackId = "track-legacy",
            source = SourceMetadata(LocationSource.PhoneGps, isSimulated = false),
        ).toReferenceLap()
        val simulated = referencePayloadV1(
            trackId = "track-legacy",
            source = SourceMetadata(LocationSource.Simulated, isSimulated = true, label = "Demo"),
        ).toReferenceLap()

        assertEquals(
            CourseCompatibilityKey(
                profileId = "track-legacy",
                geometryCompatibilityId = "track-legacy:g1",
                direction = CourseDirection.Recorded,
                isSimulated = false,
            ),
            real.compatibilityKey,
        )
        assertEquals(
            CourseCompatibilityKey(
                profileId = "track-legacy",
                geometryCompatibilityId = "track-legacy:g1",
                direction = CourseDirection.Recorded,
                isSimulated = true,
            ),
            simulated.compatibilityKey,
        )
    }

    private fun key(
        profileId: String = "profile-a",
        geometryCompatibilityId: String = "profile-a:g1",
        direction: CourseDirection = CourseDirection.Recorded,
        isSimulated: Boolean = false,
    ): CourseCompatibilityKey = CourseCompatibilityKey(
        profileId = profileId,
        geometryCompatibilityId = geometryCompatibilityId,
        direction = direction,
        isSimulated = isSimulated,
    )

    private fun reference(
        durationMillis: Long,
        compatibilityKey: CourseCompatibilityKey,
        sessionId: String = "session-$durationMillis",
    ): ReferenceLap = ReferenceLap(
        trackId = compatibilityKey.profileId,
        sessionId = sessionId,
        lapNumber = 1,
        durationMillis = durationMillis,
        isSimulated = compatibilityKey.isSimulated,
        rawSamples = emptyList(),
        progressCurve = progressCurve(),
        compatibilityKey = compatibilityKey,
    )

    private fun snapshot(
        profileId: String,
        revisionId: String,
        geometryCompatibilityId: String,
        direction: CourseDirection = CourseDirection.Recorded,
        referenceLine: TrackReferenceLine = referenceLine(),
    ): CourseSnapshot = CourseSnapshot(
        profileId = profileId,
        revisionId = revisionId,
        geometryCompatibilityId = geometryCompatibilityId,
        direction = direction,
        referenceLine = referenceLine,
        startFinish = startFinish(),
    )

    private fun referencePayloadV2(
        compatibilityKey: CourseCompatibilityKey,
        source: SourceMetadata = SourceMetadata(LocationSource.PhoneGps, isSimulated = false),
    ): GhostReferencePayloadV2 = GhostReferencePayloadV2(
        compatibilityKey = compatibilityKey,
        sessionId = "session-1",
        lapNumber = 1,
        durationMillis = 40_000L,
        source = source,
        totalDistanceMeters = 100.0,
        samples = emptyList(),
        progressPoints = progressPointDtos(),
        app = app,
    )

    private fun referencePayloadV1(trackId: String, source: SourceMetadata): GhostReferencePayloadV1 =
        GhostReferencePayloadV1(
            trackId = trackId,
            sessionId = "session-1",
            lapNumber = 1,
            durationMillis = 40_000L,
            source = source,
            totalDistanceMeters = 100.0,
            samples = listOf(
                LocationSampleDto(0L, 39.0, -86.0, source = source.source),
                LocationSampleDto(1_000L, 39.0001, -86.0, source = source.source),
            ),
            progressPoints = progressPointDtos(),
            app = app,
        )

    private fun progressCurve(): ProgressCurve = ProgressCurve(
        totalDistanceMeters = 100.0,
        points = listOf(
            ProgressPoint(
                elapsedMillis = 0L,
                progressMeters = 0.0,
                normalizedProgress = 0.0,
                latitude = 39.0,
                longitude = -86.0,
                localX = 0.0,
                localY = 0.0,
                speedMetersPerSecond = null,
                headingDegrees = null,
                horizontalAccuracyMeters = null,
            ),
            ProgressPoint(
                elapsedMillis = 40_000L,
                progressMeters = 100.0,
                normalizedProgress = 1.0,
                latitude = 39.0001,
                longitude = -86.0,
                localX = 0.0,
                localY = 100.0,
                speedMetersPerSecond = null,
                headingDegrees = null,
                horizontalAccuracyMeters = null,
            ),
        ),
    )

    private fun progressPointDtos(): List<ProgressPointDto> = progressCurve().points.map {
        ProgressPointDto(
            elapsedMillis = it.elapsedMillis,
            progressMeters = it.progressMeters,
            normalizedProgress = it.normalizedProgress,
            latitude = it.latitude,
            longitude = it.longitude,
            localX = it.localX,
            localY = it.localY,
        )
    }

    private fun referenceLine(): TrackReferenceLine = TrackReferenceLine(
        points = listOf(
            GeoPointDto(39.0, -86.0),
            GeoPointDto(39.0001, -86.0),
            GeoPointDto(39.0001, -86.0001),
            GeoPointDto(39.0, -86.0001),
        ),
        isClosed = true,
    )

    private fun startFinish(): StartFinishLineDto = StartFinishLineDto(
        pointA = GeoPointDto(39.0, -86.0),
        pointB = GeoPointDto(39.0, -86.0001),
    )

    private companion object {
        val app: AppMetadata = AppMetadata(appVersion = "0.5.0", platform = "test")
    }
}
