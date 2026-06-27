package com.huanfuli.lapsight.shared.storage

import com.huanfuli.lapsight.shared.session.GhostReferencePayloadV1
import com.huanfuli.lapsight.shared.session.TimingSessionPayloadV1
import com.huanfuli.lapsight.shared.track.CourseDirection
import com.huanfuli.lapsight.shared.track.TrackPayloadV1
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 5 Plan 01 — frozen V1 dispatch + V2 contract gate (T-05-01, T-05-02).
 *
 * Every V1 case decodes a LITERAL committed JSON member from [LegacyDatasetFixture]
 * through [SchemaMigrations]' explicit `schemaVersion` dispatch. No test
 * reconstructs a V1 Kotlin object to feed the decoder, so the frozen serializers
 * are proven against real historical bytes.
 */
class SchemaMigrationTest {

    private val fixture: JsonObject =
        SchemaMigrations.migrationJson.parseToJsonElement(LegacyDatasetFixture.JSON).jsonObject

    /** Returns one raw V1 payload member as its own JSON document. */
    private fun member(name: String): String = fixture.getValue(name).toString()

    private fun memberObject(name: String): JsonObject = fixture.getValue(name).jsonObject

    // --- Frozen-version constants ---------------------------------------------

    @Test
    fun frozenVersionConstantsAreDistinctLiterals() {
        // The V1 shape is pinned to 1 and the V2 shape to 2; neither can emit the
        // other's number. Dispatch branches on exactly these values.
        assertEquals(1, SCHEMA_VERSION_V1)
        assertEquals(2, SCHEMA_VERSION_V2)
    }

    // --- All five V1 artifacts decode through dispatch -------------------------

    @Test
    fun trackMemberDecodesThroughVersionDispatch() {
        val result = SchemaMigrations.decodeTrackProfile(member("track"))
        val profile = assertIs<LoadResult.Loaded<*>>(result).value as com.huanfuli.lapsight.shared.track.TrackProfile

        assertEquals("track-legacy-1", profile.profileId)
        assertEquals("Legacy Oval", profile.name)
        assertEquals(1, profile.revisions.size)

        val revision = assertNotNull(profile.latestRevision)
        // Two legacy intermediate lines => three complete Sectors (D-06).
        assertEquals(3, revision.courseSetup.sectorCount)
        assertEquals(2, revision.courseSetup.boundaries.size)
        assertTrue(revision.courseSetup.sectorsEnabled)
        assertNotNull(revision.courseSetup.startFinish)
        // Reference geometry is carried over verbatim.
        assertEquals(4, revision.referenceLine.points.size)
    }

    @Test
    fun reviewIndexMemberDecodesAndPinsRowMeaning() {
        val result = SchemaMigrations.decodeReviewIndex(member("reviewIndex"))
        val index = assertIs<LoadResult.Loaded<*>>(result).value as com.huanfuli.lapsight.shared.track.ReviewIndex

        assertEquals(2, index.rows.size)
        assertTrue(index.rows.any { it.id == "track-legacy-1" && it.type.name == "Track" })
        assertTrue(index.rows.any { it.id == "sess-legacy-1" && it.type.name == "TimingSession" })
    }

    @Test
    fun savedSessionMemberDecodesPreservingHistory() {
        val result = SchemaMigrations.decodeTimingSession(member("session"))
        val payload = assertIs<LoadResult.Loaded<*>>(result).value as com.huanfuli.lapsight.shared.track.TimingSessionPayloadV2

        assertEquals("sess-legacy-1", payload.session.id)
        assertEquals("track-legacy-1", payload.session.profileId)
        // Raw evidence preserved.
        assertEquals(3, payload.samples.size)
        assertEquals(1, payload.laps.size)

        val snapshot = payload.session.courseSnapshot
        assertTrue(snapshot.isLegacyMigrated)
        // Legacy cumulative splits are preserved verbatim, never relabeled.
        assertEquals(2, snapshot.legacySplits.size)
        assertEquals(20000L, snapshot.legacySplits[0].cumulativeSplitMillis)
        assertEquals(40000L, snapshot.legacySplits[1].cumulativeSplitMillis)
    }

    @Test
    fun draftMemberDecodesAsInProgressSession() {
        val result = SchemaMigrations.decodeTimingSession(member("draft"))
        val payload = assertIs<LoadResult.Loaded<*>>(result).value as com.huanfuli.lapsight.shared.track.TimingSessionPayloadV2

        assertEquals("draft-legacy-1", payload.session.id)
        assertTrue(payload.laps.isEmpty())
        assertTrue(payload.session.courseSnapshot.boundaries.isEmpty())
        assertTrue(payload.session.courseSnapshot.legacySplits.isEmpty())
    }

    @Test
    fun ghostMemberDecodesWithMigratedCompatibilityKey() {
        val result = SchemaMigrations.decodeGhostReference(member("ghost"))
        val payload = assertIs<LoadResult.Loaded<*>>(result).value as com.huanfuli.lapsight.shared.track.GhostReferencePayloadV2

        val key = payload.compatibilityKey
        assertEquals("track-legacy-1", key.profileId)
        assertEquals("track-legacy-1:g1", key.geometryCompatibilityId)
        assertEquals(CourseDirection.Recorded, key.direction)
        assertFalse(key.isSimulated)
        assertEquals(2, payload.progressPoints.size)
    }

    // --- Version dispatch fails closed ----------------------------------------

    @Test
    fun unknownFutureVersionFailsClosed() {
        val bumped = JsonObject(
            memberObject("track").toMutableMap().apply {
                put("schemaVersion", JsonPrimitive(99))
            },
        )
        val result = SchemaMigrations.decodeTrackProfile(bumped.toString())
        val corrupt = assertIs<LoadResult.Corrupt>(result)
        assertTrue(corrupt.reason.contains("unsupported schemaVersion"))
    }

    @Test
    fun missingSchemaVersionIsTypedCorrupt() {
        val result = SchemaMigrations.decodeTrackProfile("""{ "track": { "id": "x" } }""")
        val corrupt = assertIs<LoadResult.Corrupt>(result)
        assertTrue(corrupt.reason.contains("schemaVersion"))
    }

    @Test
    fun malformedJsonIsTypedCorruptNotThrown() {
        val result = SchemaMigrations.decodeTrackProfile("{ this is not valid json ]")
        assertIs<LoadResult.Corrupt>(result)
    }

    // --- Defense in depth: unsafe ids and bad geometry ------------------------

    @Test
    fun unsafeTrackIdIsTypedCorrupt() {
        val result = SchemaMigrations.decodeTrackProfile(UNSAFE_ID_TRACK_V1)
        val corrupt = assertIs<LoadResult.Corrupt>(result)
        assertTrue(corrupt.reason.contains("unsafe"))
    }

    @Test
    fun oversizedCoordinateIsTypedCorrupt() {
        val result = SchemaMigrations.decodeTrackProfile(NON_FINITE_TRACK_V1)
        val corrupt = assertIs<LoadResult.Corrupt>(result)
        assertTrue(corrupt.reason.contains("non-finite"))
    }

    @Test
    fun emptyGhostProgressCurveIsTypedCorrupt() {
        val result = SchemaMigrations.decodeGhostReference(SHORT_GHOST_V1)
        val corrupt = assertIs<LoadResult.Corrupt>(result)
        assertTrue(corrupt.reason.contains("too short"))
    }

    // --- V2 contracts validate and round-trip ---------------------------------

    @Test
    fun v2ProfileDecodesThroughTheVersionTwoPath() {
        val result = SchemaMigrations.decodeTrackProfile(VALID_V2_PROFILE)
        val profile = assertIs<LoadResult.Loaded<*>>(result).value as com.huanfuli.lapsight.shared.track.TrackProfile

        assertEquals("profile-1", profile.profileId)
        assertEquals(1, profile.revisions.size)
        assertEquals("profile-1:g1", profile.latestRevision?.geometryCompatibilityId)
        assertNull(profile.archivedAtEpochMillis)
    }

    @Test
    fun v2ProfileWithNonIncreasingOrdinalsIsCorrupt() {
        // A second revision with an ordinal <= the first violates append ordering.
        val result = SchemaMigrations.decodeTrackProfile(V2_PROFILE_BAD_ORDINALS)
        val corrupt = assertIs<LoadResult.Corrupt>(result)
        assertTrue(corrupt.reason.contains("ordinal"))
    }

    @Test
    fun v2ProfileWithWrongBoundaryCountIsCorrupt() {
        val result = SchemaMigrations.decodeTrackProfile(V2_PROFILE_BAD_BOUNDARY_COUNT)
        val corrupt = assertIs<LoadResult.Corrupt>(result)
        assertTrue(corrupt.reason.contains("boundary count"))
    }

    // --- Task 2: pure V1 -> V2 migration-correctness gates (T-05-02) ----------

    @Test
    fun trackMigrationIdentitiesAreDeterministicAndNotHashed() {
        // Identities are explicit string formulas, never a geometry hash (D-15).
        assertEquals("track-legacy-1:r1", SchemaMigrations.migratedRevisionId("track-legacy-1"))
        assertEquals("track-legacy-1:g1", SchemaMigrations.migratedGeometryCompatibilityId("track-legacy-1"))

        val v1 = SchemaMigrations.migrationJson.decodeFromString<TrackPayloadV1>(member("track"))
        val first = SchemaMigrations.migrateTrack(v1)
        val second = SchemaMigrations.migrateTrack(v1)
        // Pure + deterministic: same input maps to an identical aggregate every time.
        assertEquals(first, second)

        val revision = assertNotNull(first.latestRevision)
        assertEquals("track-legacy-1", first.profileId)
        assertEquals("track-legacy-1:r1", revision.revisionId)
        assertEquals("track-legacy-1:g1", revision.geometryCompatibilityId)
        assertEquals(1, revision.ordinal)
        // D-01/D-04: never derive a non-Recorded preferred direction at migration.
        assertEquals(CourseDirection.Recorded, first.preferredDirection)
        assertNull(first.archivedAtEpochMillis)
    }

    @Test
    fun legacySplitsArePreservedWithoutInferringAFinalSector() {
        val v1 = SchemaMigrations.migrationJson.decodeFromString<TimingSessionPayloadV1>(member("session"))
        val v2 = SchemaMigrations.migrateTimingSession(v1)
        val snapshot = v2.session.courseSnapshot

        // Every legacy cumulative line split survives verbatim — same count, same values.
        assertEquals(v1.sectorEvents.size, snapshot.legacySplits.size)
        assertEquals(
            v1.sectorEvents.map { it.splitMillis },
            snapshot.legacySplits.map { it.cumulativeSplitMillis },
        )
        // Intermediate boundaries equal the source line count (N-1); migration never
        // inflates them into N complete Sector results (D-06).
        assertEquals(v1.session.sectors.size, snapshot.boundaries.size)
        assertTrue(snapshot.isLegacyMigrated)
        // Deterministic migrated identities flow into the immutable snapshot.
        assertEquals("track-legacy-1:r1", snapshot.revisionId)
        assertEquals("track-legacy-1:g1", snapshot.geometryCompatibilityId)
        assertEquals(CourseDirection.Recorded, snapshot.direction)
    }

    @Test
    fun migrationMapsCurrentSelectionToNull() {
        val selection = SchemaMigrations.migrateCurrentSelection()
        assertNull(selection.profileId)
        assertEquals(CourseDirection.Recorded, selection.direction)
    }

    @Test
    fun ghostMigrationKeepsSourceSlotAndNeverReverses() {
        val v1 = SchemaMigrations.migrationJson.decodeFromString<GhostReferencePayloadV1>(member("ghost"))
        val v2 = SchemaMigrations.migrateGhostReference(v1)

        assertEquals(v1.source.isSimulated, v2.compatibilityKey.isSimulated)
        // Legacy references only ever attach to the Recorded direction (Pattern 2).
        assertEquals(CourseDirection.Recorded, v2.compatibilityKey.direction)
        assertEquals("track-legacy-1:g1", v2.compatibilityKey.geometryCompatibilityId)
        // Raw best-lap evidence and the precomputed curve are preserved for replay.
        assertEquals(v1.samples.size, v2.samples.size)
        assertEquals(v1.progressPoints.size, v2.progressPoints.size)
    }

    private companion object {
        const val UNSAFE_ID_TRACK_V1 = """
{
  "schemaVersion": 1,
  "track": {
    "id": "../evil",
    "name": "Bad",
    "createdAtEpochMillis": 1,
    "sourceMarkingSessionId": null,
    "source": { "source": "PhoneGps", "isSimulated": false }
  },
  "app": { "appVersion": "0.4.0" }
}
"""

        const val NON_FINITE_TRACK_V1 = """
{
  "schemaVersion": 1,
  "track": {
    "id": "track-x",
    "name": "Bad Geo",
    "createdAtEpochMillis": 1,
    "sourceMarkingSessionId": null,
    "source": { "source": "PhoneGps", "isSimulated": false },
    "referenceLine": {
      "points": [ { "latitude": 91.0, "longitude": -86.23 } ],
      "isClosed": true
    }
  },
  "app": { "appVersion": "0.4.0" }
}
"""

        const val SHORT_GHOST_V1 = """
{
  "schemaVersion": 1,
  "trackId": "track-legacy-1",
  "sessionId": "sess-legacy-1",
  "lapNumber": 1,
  "durationMillis": 60000,
  "source": { "source": "PhoneGps", "isSimulated": false },
  "totalDistanceMeters": 400.0,
  "samples": [],
  "progressPoints": [
    { "elapsedMillis": 0, "progressMeters": 0.0, "normalizedProgress": 0.0, "latitude": 39.79, "longitude": -86.23, "localX": 0.0, "localY": 0.0 }
  ],
  "app": { "appVersion": "0.4.0" }
}
"""

        const val VALID_V2_PROFILE = """
{
  "schemaVersion": 2,
  "profile": {
    "profileId": "profile-1",
    "name": "Editor Oval",
    "createdAtEpochMillis": 1700000300000,
    "source": { "source": "PhoneGps", "isSimulated": false },
    "revisions": [
      {
        "revisionId": "profile-1:r1",
        "ordinal": 1,
        "createdAtEpochMillis": 1700000300000,
        "sourceMarkingSessionId": "mark-2",
        "referenceLine": {
          "points": [
            { "latitude": 39.7900, "longitude": -86.2300 },
            { "latitude": 39.7910, "longitude": -86.2300 }
          ],
          "isClosed": true
        },
        "courseSetup": {
          "startFinish": {
            "pointA": { "latitude": 39.7900, "longitude": -86.2300 },
            "pointB": { "latitude": 39.7900, "longitude": -86.2295 }
          },
          "sectorsEnabled": true,
          "sectorCount": 3,
          "boundaries": [
            { "id": "b1", "order": 1, "pointA": { "latitude": 39.7910, "longitude": -86.2300 }, "pointB": { "latitude": 39.7910, "longitude": -86.2295 }, "normalizedProgress": 0.33 },
            { "id": "b2", "order": 2, "pointA": { "latitude": 39.7905, "longitude": -86.2290 }, "pointB": { "latitude": 39.7905, "longitude": -86.2295 }, "normalizedProgress": 0.66 }
          ]
        },
        "geometryCompatibilityId": "profile-1:g1"
      }
    ],
    "preferredDirection": "Recorded"
  },
  "app": { "appVersion": "0.5.0" }
}
"""

        const val V2_PROFILE_BAD_ORDINALS = """
{
  "schemaVersion": 2,
  "profile": {
    "profileId": "profile-2",
    "name": "Bad Ordinals",
    "createdAtEpochMillis": 1,
    "source": { "source": "PhoneGps", "isSimulated": false },
    "revisions": [
      {
        "revisionId": "profile-2:r1",
        "ordinal": 1,
        "createdAtEpochMillis": 1,
        "sourceMarkingSessionId": null,
        "referenceLine": { "points": [], "isClosed": true },
        "courseSetup": { "sectorsEnabled": false, "sectorCount": 0, "boundaries": [] },
        "geometryCompatibilityId": "profile-2:g1"
      },
      {
        "revisionId": "profile-2:r2",
        "ordinal": 1,
        "createdAtEpochMillis": 2,
        "sourceMarkingSessionId": null,
        "referenceLine": { "points": [], "isClosed": true },
        "courseSetup": { "sectorsEnabled": false, "sectorCount": 0, "boundaries": [] },
        "geometryCompatibilityId": "profile-2:g2"
      }
    ]
  },
  "app": { "appVersion": "0.5.0" }
}
"""

        const val V2_PROFILE_BAD_BOUNDARY_COUNT = """
{
  "schemaVersion": 2,
  "profile": {
    "profileId": "profile-3",
    "name": "Bad Boundaries",
    "createdAtEpochMillis": 1,
    "source": { "source": "PhoneGps", "isSimulated": false },
    "revisions": [
      {
        "revisionId": "profile-3:r1",
        "ordinal": 1,
        "createdAtEpochMillis": 1,
        "sourceMarkingSessionId": null,
        "referenceLine": { "points": [], "isClosed": true },
        "courseSetup": {
          "startFinish": {
            "pointA": { "latitude": 39.79, "longitude": -86.23 },
            "pointB": { "latitude": 39.79, "longitude": -86.22 }
          },
          "sectorsEnabled": true,
          "sectorCount": 3,
          "boundaries": [
            { "id": "b1", "order": 1, "pointA": { "latitude": 39.791, "longitude": -86.23 }, "pointB": { "latitude": 39.791, "longitude": -86.22 } }
          ]
        },
        "geometryCompatibilityId": "profile-3:g1"
      }
    ]
  },
  "app": { "appVersion": "0.5.0" }
}
"""
    }
}
