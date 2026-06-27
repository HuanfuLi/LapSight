package com.huanfuli.lapsight.shared.track

import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.session.GeoPointDto
import com.huanfuli.lapsight.shared.session.SourceMetadata
import com.huanfuli.lapsight.shared.storage.InMemorySessionStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Plan 05-04 Task 1 - the brand-new-user create flow (SC-01, D-01..D-04, T-05-07).
 *
 * Starting from an EMPTY store this drives the create path end-to-end:
 *   completed marking -> name -> [TrackProfileController.saveProfile]
 *     -> [InMemorySessionStore.listActiveProfiles] -> setCurrentSelection -> resolve.
 *
 * The suite proves the security and selection contract of the create API itself:
 * opaque IDs come from the marking (never the name), unsafe/blank names write
 * nothing, and creating a profile NEVER silently makes it the current selection -
 * selection always stays an explicit, separate step.
 */
class CourseProfileCreateTest {

    private val app = AppMetadata(appVersion = "0.5.0", platform = "test")

    @Test
    fun emptyStoreBecomesOneSelectableNamedProfileAfterCreateThenExplicitSelect() {
        val store = InMemorySessionStore()
        val controller = TrackProfileController(store)

        // Brand-new user: nothing saved, nothing selected.
        assertTrue(store.listActiveProfiles().isEmpty(), "store must start empty")
        assertEquals(CurrentProfileResolution.None, controller.resolveCurrent())

        // Completed Track marking + a user-provided name -> create one profile.
        val result = controller.saveProfile(
            track = completedMarking(id = "track-1", createdAt = 1_000L),
            name = "Brands Hatch",
            app = app,
        )
        val created = assertIs<CreateProfileResult.Created>(result)

        // One logical profile with the user's name but an opaque, name-independent id.
        assertEquals("Brands Hatch", created.profile.name)
        assertEquals("track-1", created.profile.profileId)
        // The canonical id (which forms the storage path) never embeds the name.
        assertTrue("Brands" !in created.profile.profileId)

        // A single immutable first revision with fresh opaque geometry identities.
        assertEquals(1, created.profile.revisions.size)
        val revision = created.profile.revisions.single()
        assertEquals(1, revision.ordinal)
        assertEquals("track-1:r1", revision.revisionId)
        assertEquals("track-1:g1", revision.geometryCompatibilityId)
        assertTrue(revision.courseSetup.startFinish != null, "marking confirmed start/finish")

        // It is the one active, selectable profile.
        assertEquals(listOf("track-1"), store.listActiveProfiles().map { it.profileId })

        // Creating it did NOT auto-select it: selection stays explicit (D-01..D-04).
        assertEquals(CurrentProfileResolution.None, controller.resolveCurrent())

        // Explicitly select it -> it resolves for Timing.
        store.setCurrentSelection(CurrentTrackSelection(profileId = created.profile.profileId))
        val resolution = assertIs<CurrentProfileResolution.Selected>(controller.resolveCurrent())
        assertEquals("track-1", resolution.profile.profileId)
        assertEquals("track-1:r1", resolution.revision.revisionId)
    }

    @Test
    fun sameNameTwiceCreatesTwoIndependentProfilesNoOverwrite() {
        val store = InMemorySessionStore()
        val controller = TrackProfileController(store)

        val first = assertIs<CreateProfileResult.Created>(
            controller.saveProfile(completedMarking("track-a", 1_000L), name = "Paddock", app = app),
        )
        val second = assertIs<CreateProfileResult.Created>(
            controller.saveProfile(completedMarking("track-b", 2_000L), name = "Paddock", app = app),
        )

        // Same name, two distinct logical profiles - no overwrite, no id collision.
        assertEquals("Paddock", first.profile.name)
        assertEquals("Paddock", second.profile.name)
        assertNotEquals(first.profile.profileId, second.profile.profileId)
        assertNotEquals(
            first.profile.revisions.single().geometryCompatibilityId,
            second.profile.revisions.single().geometryCompatibilityId,
        )
        assertEquals(
            setOf("track-a", "track-b"),
            store.listActiveProfiles().map { it.profileId }.toSet(),
        )
    }

    @Test
    fun blankNameIsRejectedAndWritesNothing() {
        val store = InMemorySessionStore()
        val controller = TrackProfileController(store)

        val result = controller.saveProfile(completedMarking("track-1", 1_000L), name = "   ", app = app)

        assertIs<CreateProfileResult.Rejected>(result)
        assertTrue(store.listActiveProfiles().isEmpty(), "a rejected name must persist no profile")
        assertEquals(CurrentProfileResolution.None, controller.resolveCurrent())
    }

    @Test
    fun unsafeNameCharactersAreRejectedAndWriteNothing() {
        val store = InMemorySessionStore()
        val controller = TrackProfileController(store)

        // Path separators, parent-dir escapes, and a bell control char must all be rejected.
        val unsafeNames = listOf("../evil", "a/b", "back\\slash", "ctrl" + Char(7) + "name")
        for (unsafe in unsafeNames) {
            val result = controller.saveProfile(completedMarking("track-1", 1_000L), name = unsafe, app = app)
            assertIs<CreateProfileResult.Rejected>(result)
        }

        assertTrue(store.listActiveProfiles().isEmpty(), "no unsafe-named profile may be written")
    }

    // --- Fixtures: a completed Track marking ready to become a profile ----------

    private fun completedMarking(id: String, createdAt: Long): Track = Track(
        id = id,
        name = "ignored - saveProfile uses the explicit name argument",
        createdAtEpochMillis = createdAt,
        sourceMarkingSessionId = "mark-$id",
        source = SourceMetadata(LocationSource.Simulated, isSimulated = true, label = "Demo"),
        referenceLine = TrackReferenceLine(
            points = listOf(
                GeoPointDto(latitude = 0.0, longitude = 0.0),
                GeoPointDto(latitude = 0.0, longitude = 0.001),
                GeoPointDto(latitude = 0.001, longitude = 0.001),
                GeoPointDto(latitude = 0.0, longitude = 0.0),
            ),
            isClosed = true,
        ),
        startFinish = StartFinishLineDto(
            pointA = GeoPointDto(latitude = 0.0, longitude = 0.0),
            pointB = GeoPointDto(latitude = 0.0, longitude = 0.001),
        ),
    )
}
