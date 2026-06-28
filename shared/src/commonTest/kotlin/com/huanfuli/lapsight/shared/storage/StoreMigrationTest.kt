package com.huanfuli.lapsight.shared.storage

import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.session.GhostReferencePayloadV1
import com.huanfuli.lapsight.shared.session.TimingSessionPayloadV1
import com.huanfuli.lapsight.shared.track.CourseDirection
import com.huanfuli.lapsight.shared.track.TrackMarkingSession
import com.huanfuli.lapsight.shared.track.TrackPayloadV1
import com.huanfuli.lapsight.shared.track.TrackProfile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Plan 05-02 — idempotent, fault-recoverable side-by-side V1 → V2 store migration
 * (SC-01, SC-03, D-12..D-14, threats T-05-03 / T-05-04).
 *
 * Every V1 case is seeded from the LITERAL committed [LegacyDatasetFixture] members
 * (raw historical JSON on disk for the file store) so the migration is proven against
 * real persisted bytes, not reconstructed Kotlin objects. All I/O runs against an
 * injected in-memory [FileSystem]; the fault cases use a [MigrationFailingWritesFileSystem]
 * to interrupt the index write exactly between the V2 payloads and the commit point.
 */
class StoreMigrationTest {

    private val root = "/lapsight".toPath()
    private val app = AppMetadata(appVersion = "0.5.0", buildNumber = "60", platform = "test")

    private val fixture: JsonObject =
        SchemaMigrations.migrationJson.parseToJsonElement(LegacyDatasetFixture.JSON).jsonObject

    /** Returns one raw V1 payload member of the fixture as its own JSON document. */
    private fun member(name: String): String = fixture.getValue(name).toString()

    // --- Seeding helpers -------------------------------------------------------

    /** Writes a raw V1 payload document into its canonical V1 path. */
    private fun writeRaw(fs: FileSystem, path: Path, content: String) {
        path.parent?.let { fs.createDirectories(it) }
        fs.write(path) { writeUtf8(content) }
    }

    /** Seeds the canonical V1 layout (track + session + real ghost) from the fixture. */
    private fun seedV1Files(fs: FileSystem) {
        writeRaw(fs, root / "tracks" / "track-legacy-1.json", member("track"))
        writeRaw(fs, root / "sessions" / "sess-legacy-1.json", member("session"))
        writeRaw(fs, root / "references" / "track-legacy-1__real.json", member("ghost"))
    }

    private fun decodeV1Track(): TrackPayloadV1 =
        FileSessionStore.canonicalJson.decodeFromString(TrackPayloadV1.serializer(), member("track"))

    private fun decodeV1Session(): TimingSessionPayloadV1 =
        FileSessionStore.canonicalJson.decodeFromString(TimingSessionPayloadV1.serializer(), member("session"))

    private fun decodeV1Ghost(): GhostReferencePayloadV1 =
        FileSessionStore.canonicalJson.decodeFromString(GhostReferencePayloadV1.serializer(), member("ghost"))

    /** Seeds the in-memory store from the same fixture V1 objects. */
    private fun seedV1Objects(store: InMemorySessionStore) {
        val v1Track = decodeV1Track()
        store.saveTrackBundle(
            track = v1Track.track,
            marking = TrackMarkingSession(
                id = v1Track.track.sourceMarkingSessionId ?: "mark-legacy-1",
                createdAtEpochMillis = v1Track.track.createdAtEpochMillis,
                source = v1Track.track.source,
                samples = emptyList(),
            ),
            app = v1Track.app,
        )
        val v1Session = decodeV1Session()
        store.saveTimingSession(v1Session, v1Session.app)
        val v1Ghost = decodeV1Ghost()
        store.saveReferenceLap(v1Ghost, v1Ghost.app)
    }

    // --- Behavior 1: side-by-side V2 with retained V1 originals (SC-01) --------

    @Test
    fun migrationWritesV2SideBySideAndRetainsV1Originals() {
        val fs = MigrationInMemoryFileSystem()
        val store = FileSessionStore(fs, root)
        seedV1Files(fs)

        val result = store.migrate(app)
        assertEquals(1, result.profilesMigrated)
        assertEquals(1, result.sessionsMigrated)
        assertEquals(1, result.referencesMigrated)
        assertTrue(result.skipped.isEmpty())

        // V2 payloads written side-by-side.
        assertTrue(fs.exists(root / "profiles" / "track-legacy-1.json"))
        assertTrue(fs.exists(root / "sessions-v2" / "sess-legacy-1.json"))
        assertTrue(fs.exists(root / "references-v2" / "track-legacy-1__track-legacy-1~003ag1__Recorded__real.json"))

        // Every V1 original remains present AND still loads as V1 (D-13).
        assertTrue(fs.exists(root / "tracks" / "track-legacy-1.json"))
        assertTrue(fs.exists(root / "sessions" / "sess-legacy-1.json"))
        assertTrue(fs.exists(root / "references" / "track-legacy-1__real.json"))
        assertIs<LoadResult.Loaded<TrackPayloadV1>>(store.loadTrack("track-legacy-1"))

        // The migrated profile carries the deterministic V2 identities (D-15).
        val loaded = assertIs<LoadResult.Loaded<TrackProfile>>(store.loadProfile("track-legacy-1"))
        assertEquals("track-legacy-1", loaded.value.profileId)
        assertEquals(1, loaded.value.revisions.size)
        val revision = assertNotNull(loaded.value.latestRevision)
        assertEquals("track-legacy-1:r1", revision.revisionId)
        assertEquals("track-legacy-1:g1", revision.geometryCompatibilityId)
        assertEquals(CourseDirection.Recorded, loaded.value.preferredDirection)

        assertEquals(1, store.listActiveProfiles().size)
        assertEquals("track-legacy-1", store.listActiveProfiles().single().profileId)
    }

    // --- Behavior 2: migration never writes a current selection (D-01/D-04) ----

    @Test
    fun migrationNeverWritesCurrentSelection() {
        val fs = MigrationInMemoryFileSystem()
        val store = FileSessionStore(fs, root)
        seedV1Files(fs)

        store.migrate(app)

        // No current-selection artifact is ever produced by migration.
        assertFalse(fs.allFiles.any { it.name.contains("current-selection") })
    }

    // --- Behavior 3: idempotent + lossless re-run (SC-01) ----------------------

    @Test
    fun migrationIsIdempotentAcrossRepeatedRuns() {
        val fs = MigrationInMemoryFileSystem()
        val store = FileSessionStore(fs, root)
        seedV1Files(fs)

        val first = store.migrate(app)
        val firstProfile = assertIs<LoadResult.Loaded<TrackProfile>>(store.loadProfile("track-legacy-1")).value

        val second = store.migrate(app)
        val secondProfile = assertIs<LoadResult.Loaded<TrackProfile>>(store.loadProfile("track-legacy-1")).value

        // Re-running yields exactly one logical profile with one first revision.
        assertEquals(1, store.listActiveProfiles().size)
        assertEquals(1, secondProfile.revisions.size)
        // Deterministic mapping: the aggregate is byte-for-byte identical across runs.
        assertEquals(firstProfile, secondProfile)
        assertEquals(first.profilesMigrated, second.profilesMigrated)
        assertEquals(first.sessionsMigrated, second.sessionsMigrated)
        assertEquals(first.referencesMigrated, second.referencesMigrated)
    }

    // --- Behavior 4: fault between payload and index is recoverable (T-05-04) --

    @Test
    fun faultBetweenPayloadAndIndexLeavesOriginalsAndIsRecoverable() {
        val fs = MigrationInMemoryFileSystem()
        // Fail only the profile-index commit, exactly between the V2 payload writes
        // and the index write.
        val failing = MigrationFailingWritesFileSystem(fs) { it.name.startsWith("profiles-index") }
        val failStore = FileSessionStore(failing, root)
        seedV1Files(fs)

        assertFailsWith<IOException> { failStore.migrate(app) }

        // V1 originals survive the interrupted migration and remain readable.
        assertTrue(fs.exists(root / "tracks" / "track-legacy-1.json"))
        assertIs<LoadResult.Loaded<TrackPayloadV1>>(failStore.loadTrack("track-legacy-1"))
        // The V2 profile payload was written, but the commit (index) never landed,
        // so the profile is not yet listable.
        assertTrue(fs.exists(root / "profiles" / "track-legacy-1.json"))
        assertFalse(fs.exists(root / "profiles-index.json"))
        assertTrue(failStore.listActiveProfiles().isEmpty())

        // Retry over the same backing store (no injected fault) completes the commit.
        val healthyStore = FileSessionStore(fs, root)
        healthyStore.migrate(app)
        assertEquals(1, healthyStore.listActiveProfiles().size)
    }

    // --- Behavior 5: unsafe opaque ids are rejected before a path is built -----

    @Test
    fun unsafeProfileIdIsSkippedBeforeAnyPathIsBuilt() {
        val fs = MigrationInMemoryFileSystem()
        val store = FileSessionStore(fs, root)
        // Safe filename on disk; the dangerous id lives INSIDE the payload.
        writeRaw(fs, root / "tracks" / "evil.json", UNSAFE_ID_TRACK_V1)

        val result = store.migrate(app)

        assertEquals(0, result.profilesMigrated)
        assertEquals(1, result.skipped.size)
        assertTrue(result.skipped.single().reason.contains("unsafe"))
        assertTrue(store.listActiveProfiles().isEmpty())
        // Nothing escaped the app-private root (no `../` path was ever constructed).
        assertTrue(fs.allFiles.all { it.toString().startsWith(root.toString()) })
    }

    // --- Behavior 6: file and in-memory stores match externally ----------------

    @Test
    fun fileAndInMemoryStoresExposeEquivalentMigrationResults() {
        val fs = MigrationInMemoryFileSystem()
        val fileStore = FileSessionStore(fs, root)
        seedV1Files(fs)
        val fileResult = fileStore.migrate(app)

        val memStore = InMemorySessionStore()
        seedV1Objects(memStore)
        val memResult = memStore.migrate(app)

        // Identical externally observable counts.
        assertEquals(fileResult.profilesMigrated, memResult.profilesMigrated)
        assertEquals(fileResult.sessionsMigrated, memResult.sessionsMigrated)
        assertEquals(fileResult.referencesMigrated, memResult.referencesMigrated)

        // Identical migrated profile aggregate and active listing.
        val fromFile = assertIs<LoadResult.Loaded<TrackProfile>>(fileStore.loadProfile("track-legacy-1")).value
        val fromMem = assertIs<LoadResult.Loaded<TrackProfile>>(memStore.loadProfile("track-legacy-1")).value
        assertEquals(fromFile, fromMem)
        assertEquals(
            fileStore.listActiveProfiles().map { it.profileId },
            memStore.listActiveProfiles().map { it.profileId },
        )
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
    }
}

// --- Injected in-memory file system + fault injector (Plan 05 test pattern) ----
//
// These mirror the helpers in FileSessionStoreTest; they are file-private there, so
// they are redeclared here (file-scoped `private`) rather than shared.

/** Fails writes to paths matching [shouldFail], to exercise atomic-write recovery. */
private class MigrationFailingWritesFileSystem(
    delegate: FileSystem,
    private val shouldFail: (Path) -> Boolean,
) : ForwardingFileSystem(delegate) {
    override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path = path
    override fun onPathResult(path: Path, functionName: String): Path = path
    override fun sink(file: Path, mustCreate: Boolean): Sink {
        if (shouldFail(file)) throw IOException("injected write failure: $file")
        return super.sink(file, mustCreate)
    }
}

/**
 * Minimal multiplatform in-memory [FileSystem] built on the approved Okio core
 * artifact (no `okio-fakefilesystem`). Supports only the operations the store
 * exercises: create directories, atomic temp-write + move, read, exists, list.
 */
private class MigrationInMemoryFileSystem : FileSystem() {

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
