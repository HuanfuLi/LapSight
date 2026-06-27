package com.huanfuli.lapsight.shared.storage

import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.session.GeoPointDto
import com.huanfuli.lapsight.shared.session.SourceMetadata
import com.huanfuli.lapsight.shared.track.ArchiveProfileResult
import com.huanfuli.lapsight.shared.track.AppendRevisionResult
import com.huanfuli.lapsight.shared.track.CourseDirection
import com.huanfuli.lapsight.shared.track.CourseSetup
import com.huanfuli.lapsight.shared.track.CurrentProfileResolution
import com.huanfuli.lapsight.shared.track.CurrentTrackSelection
import com.huanfuli.lapsight.shared.track.DuplicateProfileResult
import com.huanfuli.lapsight.shared.track.ProfileHistoryResult
import com.huanfuli.lapsight.shared.track.RenameProfileResult
import com.huanfuli.lapsight.shared.track.StartFinishLineDto
import com.huanfuli.lapsight.shared.track.TrackProfile
import com.huanfuli.lapsight.shared.track.TrackProfileController
import com.huanfuli.lapsight.shared.track.TrackReferenceLine
import com.huanfuli.lapsight.shared.track.TrackRevision
import okio.Buffer
import okio.FileHandle
import okio.FileMetadata
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.Timeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plan 05-08 Task 1 — profile lifecycle: rename, immutable revision history, archive,
 * and independent duplicate (D-12 through D-16; threats T-05-16 / T-05-17 / T-05-18).
 *
 * Every behavior is asserted against BOTH stores through a [Harness], mirroring
 * `CurrentTrackSelectionTest`:
 * - [FileHarness] proves true persistence: a brand-new [FileSessionStore] over the same
 *   injected [FileSystem] is the relaunch-equivalent of a cold app start.
 * - [MemoryHarness] proves the in-memory mirror keeps an identical externally observable
 *   contract.
 *
 * Lifecycle operations live on [TrackProfileController] and are built on top of the
 * store primitives (`loadProfile` / `saveProfile` / current-selection), so file and
 * in-memory stores produce the same lifecycle result by construction. The suite proves
 * the lifecycle invariants: rename never mutates a revision (D-12), geometry edits
 * append immutable revisions with D-15 compatibility carry-forward/regeneration, archive
 * preserves history while clearing ONLY a matching current selection (D-16, D-01/D-03),
 * and duplicate forks an independent profile whose compatibility can never collide with
 * its source (D-16).
 */
class TrackRevisionStoreTest {

    private val app = AppMetadata(appVersion = "0.5.0", platform = "test")

    // --- D-12: rename changes metadata only; revisions are immutable -------------

    @Test
    fun renameChangesNameOnlyAndPreservesImmutableRevisions() = runOnBothStores { h ->
        h.store.saveProfile(profile("track-a", "Alpha"), app)
        val before = loaded(h.store, "track-a")

        val result = TrackProfileController(h.store).renameProfile("track-a", "  Renamed Track  ", app)
        val renamed = assertIs<RenameProfileResult.Renamed>(result, h.label)
        assertEquals("Renamed Track", renamed.profile.name, h.label)

        // Relaunch-equivalent: the new name persisted and the revisions are untouched.
        val after = loaded(h.reopen(), "track-a")
        assertEquals("Renamed Track", after.name, h.label)
        assertEquals(before.revisions, after.revisions, "${h.label}: revisions must be immutable across rename")
        assertEquals(before.createdAtEpochMillis, after.createdAtEpochMillis, h.label)
    }

    @Test
    fun renameRejectsBlankOrUnsafeNameAndWritesNothing() = runOnBothStores { h ->
        h.store.saveProfile(profile("track-a", "Alpha"), app)
        val controller = TrackProfileController(h.store)

        assertIs<RenameProfileResult.Rejected>(controller.renameProfile("track-a", "   ", app), h.label)
        assertIs<RenameProfileResult.Rejected>(controller.renameProfile("track-a", "../evil", app), h.label)
        assertIs<RenameProfileResult.Rejected>(controller.renameProfile("track-a", "bad/name", app), h.label)

        // The original name is intact; nothing was persisted by the rejected renames.
        assertEquals("Alpha", loaded(h.reopen(), "track-a").name, h.label)
    }

    @Test
    fun renameOfMissingProfileIsRejected() = runOnBothStores { h ->
        val result = TrackProfileController(h.store).renameProfile("track-missing", "Whatever", app)
        assertIs<RenameProfileResult.Rejected>(result, h.label)
    }

    // --- D-13 / D-15: geometry edits append immutable revisions ------------------

    @Test
    fun sectorOnlyEditCarriesCompatibilityForwardAndPreservesPriorRevision() = runOnBothStores { h ->
        h.store.saveProfile(profile("track-a", "Alpha"), app)
        val controller = TrackProfileController(h.store)
        val baseCompat = loaded(h.store, "track-a").latestRevision!!.geometryCompatibilityId

        // Same reference line + same start/finish, only sectors change -> sector-only edit.
        val result = controller.appendRevision(
            profileId = "track-a",
            referenceLine = referenceLine(),
            courseSetup = CourseSetup(startFinish = startFinishLine(), sectorsEnabled = true, sectorCount = 2),
            app = app,
            now = { 2_000L },
        )
        val appended = assertIs<AppendRevisionResult.Appended>(result, h.label)
        assertEquals(2, appended.revision.ordinal, h.label)
        assertEquals(baseCompat, appended.revision.geometryCompatibilityId, "${h.label}: sector-only edit carries compatibility forward (D-15)")

        // The prior revision is preserved byte-for-byte (immutable history, D-13).
        val reopened = loaded(h.reopen(), "track-a")
        assertEquals(2, reopened.revisions.size, h.label)
        assertEquals(1, reopened.revisions.first().ordinal, h.label)
        assertEquals(baseCompat, reopened.revisions.first().geometryCompatibilityId, h.label)
    }

    @Test
    fun referenceLineEditRegeneratesCompatibility() = runOnBothStores { h ->
        h.store.saveProfile(profile("track-a", "Alpha"), app)
        val controller = TrackProfileController(h.store)
        val baseCompat = loaded(h.store, "track-a").latestRevision!!.geometryCompatibilityId

        val movedReference = TrackReferenceLine(
            points = referenceLine().points.map { it.copy(latitude = it.latitude + 0.01) },
            isClosed = true,
        )
        val result = controller.appendRevision(
            profileId = "track-a",
            referenceLine = movedReference,
            courseSetup = CourseSetup(startFinish = startFinishLine()),
            app = app,
            now = { 3_000L },
        )
        val appended = assertIs<AppendRevisionResult.Appended>(result, h.label)
        assertNotEquals(baseCompat, appended.revision.geometryCompatibilityId, "${h.label}: reference-line change regenerates compatibility (D-15)")
    }

    @Test
    fun startFinishEditRegeneratesCompatibility() = runOnBothStores { h ->
        h.store.saveProfile(profile("track-a", "Alpha"), app)
        val controller = TrackProfileController(h.store)
        val baseCompat = loaded(h.store, "track-a").latestRevision!!.geometryCompatibilityId

        val movedStartFinish = StartFinishLineDto(
            pointA = GeoPointDto(latitude = 0.5, longitude = 0.5),
            pointB = GeoPointDto(latitude = 0.5, longitude = 0.501),
        )
        val result = controller.appendRevision(
            profileId = "track-a",
            referenceLine = referenceLine(),
            courseSetup = CourseSetup(startFinish = movedStartFinish),
            app = app,
            now = { 4_000L },
        )
        val appended = assertIs<AppendRevisionResult.Appended>(result, h.label)
        assertNotEquals(baseCompat, appended.revision.geometryCompatibilityId, h.label)
    }

    @Test
    fun appendWithoutConfirmedStartFinishIsRejected() = runOnBothStores { h ->
        h.store.saveProfile(profile("track-a", "Alpha"), app)
        val result = TrackProfileController(h.store).appendRevision(
            profileId = "track-a",
            referenceLine = referenceLine(),
            courseSetup = CourseSetup(startFinish = null),
            app = app,
            now = { 5_000L },
        )
        assertIs<AppendRevisionResult.Rejected>(result, h.label)
        // Nothing was appended.
        assertEquals(1, loaded(h.reopen(), "track-a").revisions.size, h.label)
    }

    @Test
    fun appendRejectsCorruptNonIncreasingRevisionHistory() = runOnBothStores { h ->
        // A profile whose revisions are NOT strictly increasing by ordinal is corrupt;
        // appending onto it must be refused rather than silently producing a colliding
        // ordinal (defense in depth on the append-only invariant).
        val r1 = revision("track-a", 1)
        val r2 = revision("track-a", 2)
        h.store.saveProfile(profile("track-a", "Alpha").copy(revisions = listOf(r2, r1)), app)

        val result = TrackProfileController(h.store).appendRevision(
            profileId = "track-a",
            referenceLine = referenceLine(),
            courseSetup = CourseSetup(startFinish = startFinishLine()),
            app = app,
            now = { 6_000L },
        )
        assertIs<AppendRevisionResult.Rejected>(result, h.label)
    }

    @Test
    fun appendIsRejectedWhenRevisionHistoryIsFull() = runOnBothStores { h ->
        val capped = (1..TrackProfileController.MAX_REVISIONS_PER_PROFILE).map { revision("track-a", it) }
        h.store.saveProfile(profile("track-a", "Alpha").copy(revisions = capped), app)

        val result = TrackProfileController(h.store).appendRevision(
            profileId = "track-a",
            referenceLine = referenceLine(),
            courseSetup = CourseSetup(startFinish = startFinishLine()),
            app = app,
            now = { 7_000L },
        )
        assertIs<AppendRevisionResult.Rejected>(result, h.label)
        assertEquals(
            TrackProfileController.MAX_REVISIONS_PER_PROFILE,
            loaded(h.reopen(), "track-a").revisions.size,
            h.label,
        )
    }

    // --- D-16 / D-01 / D-03: archive preserves history and clears matching selection

    @Test
    fun archiveCurrentClearsSelectionPreservesHistoryAndNeverPicksReplacement() = runOnBothStores { h ->
        h.store.saveProfile(profile("track-a", "Alpha"), app)
        h.store.saveProfile(profile("track-b", "Bravo"), app)
        h.store.setCurrentSelection(CurrentTrackSelection(profileId = "track-a"))

        val result = TrackProfileController(h.store).archiveProfile("track-a", app, now = { 8_000L })
        val archived = assertIs<ArchiveProfileResult.Archived>(result, h.label)
        assertTrue(archived.clearedCurrentSelection, h.label)
        assertEquals(8_000L, archived.profile.archivedAtEpochMillis, h.label)

        val reopened = h.reopen()
        // Archived profile leaves active selectors but its payload + history are retained.
        assertTrue(reopened.listActiveProfiles().none { it.profileId == "track-a" }, h.label)
        val stillThere = loaded(reopened, "track-a")
        assertTrue(stillThere.isArchived, h.label)
        assertEquals(1, stillThere.revisions.size, "${h.label}: archive must never delete revisions")

        // D-01/D-03: archiving the current Track clears selection and never selects a
        // replacement (Bravo is NOT auto-selected).
        assertEquals(
            CurrentProfileResolution.None,
            TrackProfileController(reopened).resolveCurrent(),
            h.label,
        )
    }

    @Test
    fun archivingNonCurrentProfileLeavesSelectionUntouched() = runOnBothStores { h ->
        h.store.saveProfile(profile("track-a", "Alpha"), app)
        h.store.saveProfile(profile("track-b", "Bravo"), app)
        h.store.setCurrentSelection(CurrentTrackSelection(profileId = "track-a"))

        val result = TrackProfileController(h.store).archiveProfile("track-b", app, now = { 8_500L })
        val archived = assertIs<ArchiveProfileResult.Archived>(result, h.label)
        assertFalse(archived.clearedCurrentSelection, h.label)

        // The unrelated current selection still resolves to Alpha.
        val selected = assertIs<CurrentProfileResolution.Selected>(
            TrackProfileController(h.reopen()).resolveCurrent(),
            h.label,
        )
        assertEquals("track-a", selected.profile.profileId, h.label)
    }

    @Test
    fun archiveIsIdempotentAndKeepsOriginalTimestamp() = runOnBothStores { h ->
        h.store.saveProfile(profile("track-a", "Alpha"), app)
        val controller = TrackProfileController(h.store)
        assertIs<ArchiveProfileResult.Archived>(controller.archiveProfile("track-a", app, now = { 9_000L }), h.label)

        val second = controller.archiveProfile("track-a", app, now = { 99_000L })
        val archived = assertIs<ArchiveProfileResult.Archived>(second, h.label)
        // Re-archiving does not move the archive timestamp.
        assertEquals(9_000L, archived.profile.archivedAtEpochMillis, h.label)
    }

    @Test
    fun archiveOfMissingProfileIsRejected() = runOnBothStores { h ->
        val result = TrackProfileController(h.store).archiveProfile("track-missing", app, now = { 1L })
        assertIs<ArchiveProfileResult.Rejected>(result, h.label)
    }

    // --- D-16: duplicate forks an independent profile ---------------------------

    @Test
    fun duplicateCreatesIndependentProfileWithFreshIdentitiesAndLeavesSourceUnchanged() = runOnBothStores { h ->
        h.store.saveProfile(profile("track-a", "Alpha"), app)
        val source = loaded(h.store, "track-a")

        val result = TrackProfileController(h.store).duplicateProfile(
            profileId = "track-a",
            newName = "Alpha Copy",
            newProfileId = "track-a-copy",
            app = app,
            now = { 10_000L },
        )
        val dup = assertIs<DuplicateProfileResult.Duplicated>(result, h.label)
        assertEquals("track-a-copy", dup.duplicate.profileId, h.label)
        assertEquals("Alpha Copy", dup.duplicate.name, h.label)

        // Fresh, independent identities: no revision id or compatibility id is shared
        // with the source, so a copied Ghost reference can never collide (D-16).
        val sourceCompat = source.revisions.map { it.geometryCompatibilityId }.toSet()
        val sourceRevIds = source.revisions.map { it.revisionId }.toSet()
        for (rev in dup.duplicate.revisions) {
            assertFalse(rev.geometryCompatibilityId in sourceCompat, "${h.label}: duplicate compatibility must not collide with source")
            assertFalse(rev.revisionId in sourceRevIds, "${h.label}: duplicate revision id must not collide with source")
        }
        assertNull(dup.duplicate.archivedAtEpochMillis, h.label)

        val reopened = h.reopen()
        // The source profile is unchanged after duplication.
        assertEquals(source, loaded(reopened, "track-a"), h.label)
        // The duplicate is an active, independently loadable profile.
        assertTrue(reopened.listActiveProfiles().any { it.profileId == "track-a-copy" }, h.label)
        assertEquals(source.revisions.size, loaded(reopened, "track-a-copy").revisions.size, h.label)
    }

    @Test
    fun duplicatePreservesInternalSectorOnlySharingButIsolatesFromSource() = runOnBothStores { h ->
        // Source has two revisions that SHARE one geometryCompatibilityId (a sector-only
        // edit). The duplicate must keep that internal sharing while using a fresh id.
        val r1 = revision("track-a", 1).copy(geometryCompatibilityId = "track-a:g1")
        val r2 = revision("track-a", 2).copy(geometryCompatibilityId = "track-a:g1")
        h.store.saveProfile(profile("track-a", "Alpha").copy(revisions = listOf(r1, r2)), app)

        val result = TrackProfileController(h.store).duplicateProfile(
            profileId = "track-a",
            newName = "Copy",
            newProfileId = "track-a-copy",
            app = app,
            now = { 11_000L },
        )
        val dup = assertIs<DuplicateProfileResult.Duplicated>(result, h.label)
        val compatIds = dup.duplicate.revisions.map { it.geometryCompatibilityId }
        // Internal sharing preserved (both copied revisions share ONE fresh id)...
        assertEquals(1, compatIds.toSet().size, h.label)
        // ...and that id is isolated from the source's "track-a:g1".
        assertNotEquals("track-a:g1", compatIds.first(), h.label)
    }

    @Test
    fun duplicateRejectsUnsafeNewIdOrName() = runOnBothStores { h ->
        h.store.saveProfile(profile("track-a", "Alpha"), app)
        val controller = TrackProfileController(h.store)

        assertIs<DuplicateProfileResult.Rejected>(
            controller.duplicateProfile("track-a", "Copy", "../evil", app, now = { 1L }),
            h.label,
        )
        assertIs<DuplicateProfileResult.Rejected>(
            controller.duplicateProfile("track-a", "bad/name", "track-a-copy", app, now = { 1L }),
            h.label,
        )
    }

    @Test
    fun duplicateOfMissingProfileIsRejected() = runOnBothStores { h ->
        val result = TrackProfileController(h.store).duplicateProfile(
            "track-missing", "Copy", "track-copy", app, now = { 1L },
        )
        assertIs<DuplicateProfileResult.Rejected>(result, h.label)
    }

    // --- History/exact load includes archived; active list excludes it ----------

    @Test
    fun listHistoryReturnsAllRevisionsInOrderEvenWhenArchived() = runOnBothStores { h ->
        h.store.saveProfile(profile("track-a", "Alpha"), app)
        val controller = TrackProfileController(h.store)
        controller.appendRevision(
            profileId = "track-a",
            referenceLine = referenceLine(),
            courseSetup = CourseSetup(startFinish = startFinishLine(), sectorsEnabled = true, sectorCount = 2),
            app = app,
            now = { 2_000L },
        )
        controller.archiveProfile("track-a", app, now = { 3_000L })

        val history = assertIs<ProfileHistoryResult.History>(controller.listHistory("track-a"), h.label)
        assertEquals(listOf(1, 2), history.revisions.map { it.ordinal }, h.label)
        // Active selectors exclude the archived profile though history remains navigable.
        assertTrue(h.reopen().listActiveProfiles().none { it.profileId == "track-a" }, h.label)
    }

    @Test
    fun listHistoryOfMissingProfileIsNotFound() = runOnBothStores { h ->
        assertIs<ProfileHistoryResult.NotFound>(
            TrackProfileController(h.store).listHistory("track-missing"),
            h.label,
        )
    }

    // --- File-only: an interrupted lifecycle write leaves prior state intact -----

    @Test
    fun interruptedRenameWriteLeavesPriorProfileAndSelectionIntact() {
        val fs = RevisionInMemoryFileSystem()
        val root = "/lapsight".toPath()
        FileSessionStore(fs, root).also { seed ->
            seed.saveProfile(profile("track-a", "Alpha"), app)
            seed.setCurrentSelection(CurrentTrackSelection(profileId = "track-a"))
        }

        // Fail every write into the profiles directory: the atomic temp-write throws
        // before the atomicMove, so the canonical profile is never torn.
        val failing = FailingProfileWritesFileSystem(fs)
        val store = FileSessionStore(failing, root)
        assertFailsWith<IOException> {
            TrackProfileController(store).renameProfile("track-a", "Renamed", app)
        }

        // Cold relaunch: the original name and selection survived the interrupted write.
        val reopened = FileSessionStore(fs, root)
        assertEquals("Alpha", loaded(reopened, "track-a").name)
        val selected = assertIs<CurrentProfileResolution.Selected>(
            TrackProfileController(reopened).resolveCurrent(),
        )
        assertEquals("track-a", selected.profile.profileId)
    }

    @Test
    fun unsafeProfileIdNeverBuildsTraversalPathOnLifecycleOps() {
        val fs = RevisionInMemoryFileSystem()
        val root = "/lapsight".toPath()
        val controller = TrackProfileController(FileSessionStore(fs, root))

        // None of these unsafe-id lifecycle calls may construct an escaping path.
        controller.renameProfile("../evil", "X", app)
        controller.archiveProfile("../evil", app, now = { 1L })
        controller.duplicateProfile("track-a", "X", "../evil", app, now = { 1L })

        assertTrue(fs.allFiles.all { it.toString().startsWith(root.toString()) })
    }

    // --- Harness wiring --------------------------------------------------------

    private fun runOnBothStores(block: (Harness) -> Unit) {
        block(FileHarness())
        block(MemoryHarness())
    }

    private interface Harness {
        val label: String
        val store: LocalSessionStore
        fun reopen(): LocalSessionStore
    }

    private inner class FileHarness : Harness {
        override val label = "file"
        private val fs = RevisionInMemoryFileSystem()
        private val root = "/lapsight".toPath()
        override val store: LocalSessionStore = FileSessionStore(fs, root)
        override fun reopen(): LocalSessionStore = FileSessionStore(fs, root)
    }

    private inner class MemoryHarness : Harness {
        override val label = "memory"
        override val store: LocalSessionStore = InMemorySessionStore()
        override fun reopen(): LocalSessionStore = store
    }

    private fun loaded(store: LocalSessionStore, profileId: String): TrackProfile =
        assertIs<LoadResult.Loaded<TrackProfile>>(store.loadProfile(profileId)).value

    // --- Fixtures --------------------------------------------------------------

    private fun startFinishLine() = StartFinishLineDto(
        pointA = GeoPointDto(latitude = 0.0, longitude = 0.0),
        pointB = GeoPointDto(latitude = 0.0, longitude = 0.001),
    )

    private fun referenceLine() = TrackReferenceLine(
        points = listOf(
            GeoPointDto(latitude = 0.0, longitude = 0.0),
            GeoPointDto(latitude = 0.0, longitude = 0.001),
            GeoPointDto(latitude = 0.001, longitude = 0.001),
            GeoPointDto(latitude = 0.0, longitude = 0.0),
        ),
        isClosed = true,
    )

    private fun revision(profileId: String, ordinal: Int) = TrackRevision(
        revisionId = "$profileId:r$ordinal",
        ordinal = ordinal,
        createdAtEpochMillis = ordinal.toLong(),
        sourceMarkingSessionId = null,
        referenceLine = referenceLine(),
        courseSetup = CourseSetup(startFinish = startFinishLine()),
        geometryCompatibilityId = "$profileId:g$ordinal",
    )

    private fun profile(id: String, name: String) = TrackProfile(
        profileId = id,
        name = name,
        createdAtEpochMillis = 1_000L,
        source = SourceMetadata(LocationSource.Simulated, isSimulated = true, label = "Demo"),
        revisions = listOf(revision(id, 1)),
        preferredDirection = CourseDirection.Recorded,
    )
}

/** Fails every write into the `profiles/` directory to exercise atomic-write recovery. */
private class FailingProfileWritesFileSystem(
    delegate: FileSystem,
) : ForwardingFileSystem(delegate) {
    override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path = path
    override fun onPathResult(path: Path, functionName: String): Path = path
    override fun sink(file: Path, mustCreate: Boolean): Sink {
        if (file.toString().contains("profiles")) throw IOException("injected write failure: $file")
        return super.sink(file, mustCreate)
    }
}

/**
 * Minimal multiplatform in-memory [FileSystem] for lifecycle persistence tests
 * (file-private to avoid a same-package top-level name clash with the other storage
 * test file systems).
 */
private class RevisionInMemoryFileSystem : FileSystem() {

    private val files = LinkedHashMap<Path, okio.ByteString>()
    private val directories = LinkedHashSet<Path>()

    val allFiles: Set<Path> get() = files.keys.toSet()

    override fun canonicalize(path: Path): Path = path

    override fun metadataOrNull(path: Path): FileMetadata? = when {
        files.containsKey(path) -> FileMetadata(
            isRegularFile = true,
            isDirectory = false,
            size = files.getValue(path).size.toLong(),
        )
        directories.contains(path) -> FileMetadata(isRegularFile = false, isDirectory = true)
        else -> null
    }

    override fun list(dir: Path): List<Path> =
        (files.keys + directories).filter { it.parent == dir }

    override fun listOrNull(dir: Path): List<Path>? =
        if (directories.contains(dir)) list(dir) else null

    override fun source(file: Path): Source {
        val bytes = files[file] ?: throw IOException("no such file: $file")
        return Buffer().apply { write(bytes) }
    }

    override fun sink(file: Path, mustCreate: Boolean): Sink {
        if (mustCreate && files.containsKey(file)) throw IOException("file already exists: $file")
        return StoringSink(file)
    }

    override fun appendingSink(file: Path, mustExist: Boolean): Sink {
        if (mustExist && !files.containsKey(file)) throw IOException("no such file: $file")
        return StoringSink(file, initial = files[file])
    }

    override fun createDirectory(dir: Path, mustCreate: Boolean) {
        if (mustCreate && directories.contains(dir)) throw IOException("directory already exists: $dir")
        directories.add(dir)
    }

    override fun atomicMove(source: Path, target: Path) {
        val bytes = files[source] ?: throw IOException("no such file: $source")
        files[target] = bytes
        files.remove(source)
    }

    override fun delete(path: Path, mustExist: Boolean) {
        val removed = (files.remove(path) != null) || directories.remove(path)
        if (mustExist && !removed) throw IOException("no such file: $path")
    }

    override fun createSymlink(source: Path, target: Path) {
        throw UnsupportedOperationException("symlinks are not supported in tests")
    }

    override fun openReadOnly(file: Path): FileHandle =
        throw UnsupportedOperationException("file handles are not supported in tests")

    override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle =
        throw UnsupportedOperationException("file handles are not supported in tests")

    private inner class StoringSink(
        private val path: Path,
        initial: okio.ByteString? = null,
    ) : Sink {
        private val buffer = Buffer().apply { if (initial != null) write(initial) }
        private var closed = false

        override fun write(source: Buffer, byteCount: Long) {
            buffer.write(source, byteCount)
        }

        override fun flush() {}

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() {
            if (closed) return
            closed = true
            files[path] = buffer.readByteString()
        }
    }
}
