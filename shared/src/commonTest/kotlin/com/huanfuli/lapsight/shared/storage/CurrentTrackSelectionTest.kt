package com.huanfuli.lapsight.shared.storage

import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.session.GeoPointDto
import com.huanfuli.lapsight.shared.session.SourceMetadata
import com.huanfuli.lapsight.shared.track.CourseDirection
import com.huanfuli.lapsight.shared.track.CourseSetup
import com.huanfuli.lapsight.shared.track.CurrentProfileResolution
import com.huanfuli.lapsight.shared.track.CurrentTrackSelection
import com.huanfuli.lapsight.shared.track.StartFinishLineDto
import com.huanfuli.lapsight.shared.track.TrackProfile
import com.huanfuli.lapsight.shared.track.TrackProfileController
import com.huanfuli.lapsight.shared.track.TrackReferenceLine
import com.huanfuli.lapsight.shared.track.TrackRevision
import okio.Buffer
import okio.FileHandle
import okio.FileMetadata
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.Timeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Plan 05-03 Task 1 — exact current-Track selection with NO newest-Track fallback
 * (D-01..D-04, SC-01, SC-03, threats T-05-05 / T-05-06).
 *
 * Every behavior is asserted against BOTH stores through a [Harness]:
 * - [FileHarness] proves true persistence: a brand-new [FileSessionStore] reading the
 *   same injected [FileSystem] is the relaunch-equivalent of a cold app start.
 * - [MemoryHarness] proves the in-memory mirror keeps an identical externally
 *   observable contract; its persistence boundary is the store instance itself, so a
 *   fresh controller over the same store is the relaunch-equivalent.
 *
 * The suite explicitly rejects any `maxByOrNull(createdAtEpochMillis)` newest-ready
 * fallback: creating a newer profile never steals an existing selection, and an
 * absent selection resolves to [CurrentProfileResolution.None] even when profiles
 * exist.
 */
class CurrentTrackSelectionTest {

    private val app = AppMetadata(appVersion = "0.5.0", platform = "test")

    // --- Behavior: empty/migrated storage resolves to None (Timing blocked) ----

    @Test
    fun emptyStorageResolvesToNone() = runOnBothStores { h ->
        // A profile exists, but nothing is selected: NO newest-ready fallback (D-03).
        h.store.saveProfile(timingReadyProfile("track-a", "Alpha", createdAt = 1_000L), app)

        val resolution = TrackProfileController(h.store).resolveCurrent()
        assertEquals(CurrentProfileResolution.None, resolution, h.label)
    }

    @Test
    fun migratedNullSelectionResolvesToNone() = runOnBothStores { h ->
        h.store.saveProfile(timingReadyProfile("track-a", "Alpha", createdAt = 1_000L), app)
        // Migration writes an explicit null-profile selection (D-01/D-04).
        h.store.setCurrentSelection(CurrentTrackSelection(profileId = null))

        val resolution = TrackProfileController(h.store).resolveCurrent()
        assertEquals(CurrentProfileResolution.None, resolution, h.label)
    }

    // --- Behavior: exact selection survives relaunch + ignores newer profiles --

    @Test
    fun exactSelectionSurvivesRelaunchAndIgnoresNewerProfile() = runOnBothStores { h ->
        h.store.saveProfile(timingReadyProfile("track-a", "Alpha", createdAt = 1_000L), app)
        h.store.setCurrentSelection(
            CurrentTrackSelection(profileId = "track-a", direction = CourseDirection.Reverse),
        )

        // A newer profile B is created AFTER the selection. The newest-ready heuristic
        // would switch to B; the explicit selection must not (D-04).
        h.store.saveProfile(timingReadyProfile("track-b", "Bravo", createdAt = 9_000L), app)

        // Relaunch-equivalent recreation: a fresh store + controller reading the same
        // persisted state.
        val reopened = h.reopen()
        val resolution = TrackProfileController(reopened).resolveCurrent()

        val selected = assertIs<CurrentProfileResolution.Selected>(resolution, h.label)
        assertEquals("track-a", selected.profile.profileId, h.label)
        assertEquals("Alpha", selected.profile.name, h.label)
        assertEquals(CourseDirection.Reverse, selected.direction, h.label)
        assertEquals("track-a:r1", selected.revision.revisionId, h.label)
    }

    // --- Behavior: stale selection (profile gone) is typed-unavailable ----------

    @Test
    fun staleSelectionResolvesToStaleNeverAnotherProfile() = runOnBothStores { h ->
        // Another profile exists so a fallback heuristic COULD pick it; it must not.
        h.store.saveProfile(timingReadyProfile("track-b", "Bravo", createdAt = 9_000L), app)
        h.store.setCurrentSelection(CurrentTrackSelection(profileId = "track-missing"))

        val resolution = TrackProfileController(h.reopen()).resolveCurrent()
        assertEquals(CurrentProfileResolution.Stale, resolution, h.label)
    }

    // --- Behavior: archived selection is typed-unavailable (D-16) ---------------

    @Test
    fun archivedSelectionResolvesToArchived() = runOnBothStores { h ->
        h.store.saveProfile(
            timingReadyProfile("track-a", "Alpha", createdAt = 1_000L, archivedAt = 5_000L),
            app,
        )
        h.store.setCurrentSelection(CurrentTrackSelection(profileId = "track-a"))

        val resolution = TrackProfileController(h.reopen()).resolveCurrent()
        assertEquals(CurrentProfileResolution.Archived, resolution, h.label)
    }

    // --- Behavior: not-timing-ready selection is typed-unavailable (D-05) -------

    @Test
    fun selectionWithoutConfirmedStartFinishResolvesToNotTimingReady() = runOnBothStores { h ->
        h.store.saveProfile(notTimingReadyProfile("track-a", "Alpha", createdAt = 1_000L), app)
        h.store.setCurrentSelection(CurrentTrackSelection(profileId = "track-a"))

        val resolution = TrackProfileController(h.reopen()).resolveCurrent()
        assertEquals(CurrentProfileResolution.NotTimingReady, resolution, h.label)
    }

    // --- Behavior: clearing the selection returns to None -----------------------

    @Test
    fun clearingSelectionResolvesBackToNone() = runOnBothStores { h ->
        h.store.saveProfile(timingReadyProfile("track-a", "Alpha", createdAt = 1_000L), app)
        h.store.setCurrentSelection(CurrentTrackSelection(profileId = "track-a"))
        assertIs<CurrentProfileResolution.Selected>(
            TrackProfileController(h.store).resolveCurrent(),
            h.label,
        )

        h.store.clearCurrentSelection()

        assertEquals(
            CurrentProfileResolution.None,
            TrackProfileController(h.reopen()).resolveCurrent(),
            h.label,
        )
    }

    // --- Behavior: invalid selection IDs cannot cause path traversal ------------

    @Test
    fun unsafeSelectionIdIsCorruptAndBuildsNoTraversalPath() {
        val fs = SelectionInMemoryFileSystem()
        val root = "/lapsight".toPath()
        // The dangerous id is injected directly into the persisted selection file,
        // bypassing setCurrentSelection's guard, to prove the load/resolve path is
        // also hardened (defense in depth, T-05-05).
        writeRaw(fs, root / "current-selection.json", """{ "profileId": "../evil", "direction": "Recorded" }""")

        val store = FileSessionStore(fs, root)
        val resolution = TrackProfileController(store).resolveCurrent()

        assertIs<CurrentProfileResolution.Corrupt>(resolution)
        // No `../`-escaped path was ever constructed; nothing escaped the root.
        assertTrue(fs.allFiles.all { it.toString().startsWith(root.toString()) })
    }

    @Test
    fun setCurrentSelectionRejectsUnsafeProfileId() = runOnBothStores { h ->
        var threw = false
        try {
            h.store.setCurrentSelection(CurrentTrackSelection(profileId = "../evil"))
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw, "${h.label}: unsafe profile id must be rejected before any write")
        assertEquals(
            CurrentProfileResolution.None,
            TrackProfileController(h.store).resolveCurrent(),
            h.label,
        )
    }

    // --- Harness wiring --------------------------------------------------------

    private fun runOnBothStores(block: (Harness) -> Unit) {
        block(FileHarness())
        block(MemoryHarness())
    }

    private interface Harness {
        val label: String
        val store: LocalSessionStore
        /** Returns a store reading the same persisted state (relaunch-equivalent). */
        fun reopen(): LocalSessionStore
    }

    private inner class FileHarness : Harness {
        override val label = "file"
        private val fs = SelectionInMemoryFileSystem()
        private val root = "/lapsight".toPath()
        override val store: LocalSessionStore = FileSessionStore(fs, root)
        // A brand-new FileSessionStore over the same filesystem = cold relaunch.
        override fun reopen(): LocalSessionStore = FileSessionStore(fs, root)
    }

    private inner class MemoryHarness : Harness {
        override val label = "memory"
        // The store IS the persistence boundary; a fresh controller over the same
        // store instance is the in-memory relaunch-equivalent.
        override val store: LocalSessionStore = InMemorySessionStore()
        override fun reopen(): LocalSessionStore = store
    }

    // --- Profile fixtures ------------------------------------------------------

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

    private fun timingReadyProfile(
        id: String,
        name: String,
        createdAt: Long,
        archivedAt: Long? = null,
    ): TrackProfile = TrackProfile(
        profileId = id,
        name = name,
        createdAtEpochMillis = createdAt,
        source = SourceMetadata(LocationSource.Simulated, isSimulated = true, label = "Demo"),
        revisions = listOf(
            TrackRevision(
                revisionId = "$id:r1",
                ordinal = 1,
                createdAtEpochMillis = createdAt,
                sourceMarkingSessionId = null,
                referenceLine = referenceLine(),
                courseSetup = CourseSetup(startFinish = startFinishLine()),
                geometryCompatibilityId = "$id:g1",
            ),
        ),
        archivedAtEpochMillis = archivedAt,
    )

    private fun notTimingReadyProfile(
        id: String,
        name: String,
        createdAt: Long,
    ): TrackProfile {
        val ready = timingReadyProfile(id, name, createdAt)
        return ready.copy(
            revisions = listOf(
                ready.revisions.single().copy(courseSetup = CourseSetup(startFinish = null)),
            ),
        )
    }

    private fun writeRaw(fs: FileSystem, path: Path, content: String) {
        path.parent?.let { fs.createDirectories(it) }
        fs.write(path) { writeUtf8(content) }
    }
}

/**
 * Minimal multiplatform in-memory [FileSystem] for selection persistence tests
 * (mirrors the helpers in the other storage tests; file-private here to avoid a
 * same-package top-level name clash).
 */
private class SelectionInMemoryFileSystem : FileSystem() {

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
