package com.huanfuli.lapsight.shared.ui

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.session.SourceMetadata
import com.huanfuli.lapsight.shared.session.GeoPointDto
import com.huanfuli.lapsight.shared.session.toDto
import com.huanfuli.lapsight.shared.storage.InMemorySessionStore
import com.huanfuli.lapsight.shared.storage.LoadResult
import com.huanfuli.lapsight.shared.storage.LocalSessionStore
import com.huanfuli.lapsight.shared.track.CourseSetup
import com.huanfuli.lapsight.shared.track.CurrentProfileResolution
import com.huanfuli.lapsight.shared.track.CurrentTrackSelection
import com.huanfuli.lapsight.shared.track.StartFinishLineDto
import com.huanfuli.lapsight.shared.track.Track
import com.huanfuli.lapsight.shared.track.TrackMarkingSession
import com.huanfuli.lapsight.shared.track.TrackPayloadV1
import com.huanfuli.lapsight.shared.track.TrackProfile
import com.huanfuli.lapsight.shared.track.TrackProfileController
import com.huanfuli.lapsight.shared.track.TrackReferenceLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Plan 05-08 Task 2 — Track-detail profile lifecycle exposure (SC-01; D-12 rename,
 * D-16 archive/duplicate; D-01/D-03 selection clearing).
 *
 * The Track-detail surface forwards to pure helpers ([renameTrack], [archiveTrack],
 * [duplicateTrack]); this suite drives those helpers over a real [InMemorySessionStore]
 * and asserts the user-visible lifecycle guarantees:
 * - rename changes only the name and never mutates revision history;
 * - archiving the current Track removes it from active selectors AND routes Drive to the
 *   explicit no-selection state instead of auto-selecting another Track;
 * - archiving a non-current Track leaves the selection untouched;
 * - duplicate forks an independent profile without disturbing the source;
 * - an old session's Review stays bound to the frozen V1 geometry even after the profile
 *   gains a new revision.
 */
class TrackProfileReviewTest {

    private val app = AppMetadata(appVersion = "0.5.0", platform = "test")

    // --- D-12: rename changes name only; immutable revisions preserved ----------

    @Test
    fun renameUpdatesNameWithoutMutatingRevisions() {
        val store = InMemorySessionStore()
        seedTrack(store, "track-a", "Alpha")
        val before = loadedProfileAfterPromotion(store, "track-a")

        val message = renameTrack(store, "track-a", "Mugello North")
        assertFalse(message.startsWith("Couldn't"), message)

        val after = loadedProfile(store, "track-a")
        assertEquals("Mugello North", after.name)
        assertEquals(before.revisions, after.revisions, "rename must not mutate immutable revisions")
    }

    @Test
    fun renameWithBlankNameIsReportedAndChangesNothing() {
        val store = InMemorySessionStore()
        seedTrack(store, "track-a", "Alpha")
        loadedProfileAfterPromotion(store, "track-a")

        val message = renameTrack(store, "track-a", "   ")
        assertTrue(message.startsWith("Couldn't"), message)
        assertEquals("Alpha", loadedProfile(store, "track-a").name)
    }

    // --- D-16 / D-01 / D-03: archive removes from selectors + clears current ----

    @Test
    fun archivingCurrentTrackClearsSelectionAndRoutesDriveToNoSelection() {
        val store = InMemorySessionStore()
        seedTrack(store, "track-a", "Alpha")
        seedTrack(store, "track-b", "Bravo")
        // Promote both and make Alpha the current selection.
        promote(store, "track-a")
        promote(store, "track-b")
        store.setCurrentSelection(CurrentTrackSelection(profileId = "track-a"))

        val message = archiveTrack(store, "track-a", now = { 5_000L })
        assertFalse(message.startsWith("Couldn't"), message)

        // Archived profile leaves active selectors immediately but its history is retained.
        assertTrue(store.listActiveProfiles().none { it.profileId == "track-a" })
        assertTrue(loadedProfile(store, "track-a").isArchived)
        assertEquals(1, loadedProfile(store, "track-a").revisions.size)

        // D-01/D-03: Drive resolves to no-selection — Bravo is NOT auto-selected.
        assertEquals(
            CurrentProfileResolution.None,
            TrackProfileController(store).resolveCurrent(),
        )
    }

    @Test
    fun archivingNonCurrentTrackLeavesSelectionUntouched() {
        val store = InMemorySessionStore()
        seedTrack(store, "track-a", "Alpha")
        seedTrack(store, "track-b", "Bravo")
        promote(store, "track-a")
        promote(store, "track-b")
        store.setCurrentSelection(CurrentTrackSelection(profileId = "track-a"))

        val message = archiveTrack(store, "track-b", now = { 6_000L })
        assertFalse(message.startsWith("Couldn't"), message)

        val selected = assertIs<CurrentProfileResolution.Selected>(
            TrackProfileController(store).resolveCurrent(),
        )
        assertEquals("track-a", selected.profile.profileId)
        assertTrue(store.listActiveProfiles().none { it.profileId == "track-b" })
    }

    // --- D-16: duplicate forks an independent profile ---------------------------

    @Test
    fun duplicateCreatesIndependentProfileAndLeavesSourceUnchanged() {
        val store = InMemorySessionStore()
        seedTrack(store, "track-a", "Alpha")
        val source = loadedProfileAfterPromotion(store, "track-a")

        val message = duplicateTrack(store, "track-a", now = { 42L })
        assertFalse(message.startsWith("Couldn't"), message)

        val duplicate = loadedProfile(store, "track-a-copy-42")
        assertEquals("Alpha copy", duplicate.name)
        // Fresh identities: no compatibility id is shared with the source (D-16).
        val sourceCompat = source.revisions.map { it.geometryCompatibilityId }.toSet()
        duplicate.revisions.forEach { revision ->
            assertFalse(revision.geometryCompatibilityId in sourceCompat)
        }
        assertNotEquals(source.profileId, duplicate.profileId)
        // The source profile is unchanged and still active alongside the duplicate.
        assertEquals(source, loadedProfile(store, "track-a"))
        assertTrue(store.listActiveProfiles().any { it.profileId == "track-a-copy-42" })
        assertTrue(
            store.readIndex().rows.any { it.id == "track-a-copy-42" },
            "duplicate must be visible in Review immediately",
        )
    }

    @Test
    fun duplicateProfileCanBeManagedWithoutLegacyTrackPayload() {
        val store = InMemorySessionStore()
        seedTrack(store, "track-a", "Alpha")
        loadedProfileAfterPromotion(store, "track-a")

        val duplicateMessage = duplicateTrack(store, "track-a", now = { 42L })
        assertFalse(duplicateMessage.startsWith("Couldn't"), duplicateMessage)
        assertIs<LoadResult.NotFound>(store.loadTrack("track-a-copy-42"))

        val renameMessage = renameTrack(store, "track-a-copy-42", "Alpha setup B")
        assertFalse(renameMessage.startsWith("Couldn't"), renameMessage)
        assertEquals("Alpha setup B", loadedProfile(store, "track-a-copy-42").name)
    }

    // --- D-14 (anti-pattern guard): old session geometry is frozen --------------

    @Test
    fun appendingRevisionDoesNotAlterFrozenV1GeometryUsedByOldSessionReview() {
        val store = InMemorySessionStore()
        seedTrack(store, "track-a", "Alpha")
        val originalTrack = loadedV1Track(store, "track-a")

        // Edit the profile: append a revision with moved start/finish geometry.
        val profile = loadedProfileAfterPromotion(store, "track-a")
        val movedStartFinish = StartFinishLineDto(
            pointA = GeoPointDto(latitude = 0.5, longitude = 0.5),
            pointB = GeoPointDto(latitude = 0.5, longitude = 0.501),
        )
        val appended = TrackProfileController(store).appendRevision(
            profileId = profile.profileId,
            referenceLine = referenceLine(),
            courseSetup = CourseSetup(startFinish = movedStartFinish),
            app = app,
            now = { 7_000L },
        )
        assertIs<com.huanfuli.lapsight.shared.track.AppendRevisionResult.Appended>(appended)

        // The profile history grew, but the frozen V1 Track payload that an old session's
        // Review renders is byte-for-byte unchanged (old geometry stays old geometry).
        assertEquals(2, loadedProfile(store, "track-a").revisions.size)
        assertEquals(originalTrack, loadedV1Track(store, "track-a"))
    }

    // --- Fixtures + helpers ----------------------------------------------------

    private val source = SourceMetadata(LocationSource.Simulated, isSimulated = true, label = "Demo")

    private fun referenceLine() = TrackReferenceLine(
        points = listOf(
            GeoPointDto(latitude = 0.0, longitude = 0.0),
            GeoPointDto(latitude = 0.0, longitude = 0.001),
            GeoPointDto(latitude = 0.001, longitude = 0.001),
            GeoPointDto(latitude = 0.0, longitude = 0.0),
        ),
        isClosed = true,
    )

    private fun startFinishLine() = StartFinishLineDto(
        pointA = GeoPointDto(latitude = 0.0, longitude = 0.0),
        pointB = GeoPointDto(latitude = 0.0, longitude = 0.001),
    )

    /** Saves a timing-ready V1 Track + marking so the Track-detail helpers can promote it. */
    private fun seedTrack(store: LocalSessionStore, id: String, name: String) {
        val marking = TrackMarkingSession(
            id = "$id-mark",
            createdAtEpochMillis = 1_000L,
            source = source,
            samples = listOf(
                LocationSample(0L, 0.0, 0.0, 5.0, 12.0, 90.0, 200.0, LocationSource.Simulated).toDto(),
                LocationSample(1_000L, 0.0, 0.001, 5.0, 12.0, 90.0, 200.0, LocationSource.Simulated).toDto(),
            ),
        )
        val track = Track(
            id = id,
            name = name,
            createdAtEpochMillis = 1_000L,
            sourceMarkingSessionId = marking.id,
            source = source,
            referenceLine = referenceLine(),
            startFinish = startFinishLine(),
        )
        store.saveTrackBundle(track, marking, app)
    }

    /** Promotes a V1 Track to its V2 profile via the public controller path. */
    private fun promote(store: LocalSessionStore, trackId: String) {
        val payload = (store.loadTrack(trackId) as LoadResult.Loaded<TrackPayloadV1>).value
        TrackProfileController(store).saveProfile(payload.track, payload.track.name, payload.app)
    }

    private fun loadedProfile(store: LocalSessionStore, profileId: String): TrackProfile =
        assertIs<LoadResult.Loaded<TrackProfile>>(store.loadProfile(profileId)).value

    /** Promotes (if needed) then returns the V2 profile. */
    private fun loadedProfileAfterPromotion(store: LocalSessionStore, trackId: String): TrackProfile {
        promote(store, trackId)
        return loadedProfile(store, trackId)
    }

    private fun loadedV1Track(store: LocalSessionStore, trackId: String): Track =
        assertIs<LoadResult.Loaded<TrackPayloadV1>>(store.loadTrack(trackId)).value.track
}
