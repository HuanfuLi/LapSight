package com.huanfuli.lapsight.shared.storage

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.session.LocationSampleDto
import com.huanfuli.lapsight.shared.session.SourceMetadata
import com.huanfuli.lapsight.shared.session.toDto
import com.huanfuli.lapsight.shared.track.ReviewEntryType
import com.huanfuli.lapsight.shared.track.Track
import com.huanfuli.lapsight.shared.track.TrackMarkingSession
import com.huanfuli.lapsight.shared.track.TrackPayloadV1
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
import kotlin.test.assertTrue

/**
 * Wave 0 coverage for the versioned local storage foundation (D-21 through D-25).
 *
 * All I/O runs against an injected in-memory [FileSystem] rooted at an app-private
 * path, so no real platform path or database/ORM is exercised. The in-memory file
 * system is implemented locally on top of the approved Okio core artifact (the
 * `okio-fakefilesystem` test artifact is intentionally not added — see SUMMARY).
 */
class FileSessionStoreTest {

    private val root = "/lapsight".toPath()

    private val app = AppMetadata(appVersion = "0.3.0", buildNumber = "42", platform = "test")

    private val source = SourceMetadata(
        source = LocationSource.Simulated,
        isSimulated = true,
        label = "Demo",
    )

    private fun samples(): List<LocationSampleDto> = listOf(
        LocationSample(0L, 39.8121, -86.1062, 5.0, 12.0, 90.0, 200.0, LocationSource.Simulated).toDto(),
        LocationSample(1_000L, 39.8126, -86.1062, 5.0, 12.0, 90.0, 200.0, LocationSource.Simulated).toDto(),
    )

    private fun marking() = TrackMarkingSession(
        id = "mark-1",
        createdAtEpochMillis = 1_000L,
        source = source,
        samples = samples(),
    )

    private fun track() = Track(
        id = "track-1",
        name = "Test Track",
        createdAtEpochMillis = 1_000L,
        sourceMarkingSessionId = "mark-1",
        source = source,
    )

    // Test 1 (D-21..D-25): payloads written before index rows; canonical metadata persisted.
    @Test
    fun saveWritesPayloadsBeforeIndexAndPersistsMetadata() {
        val fs = InMemoryFileSystem()
        val recording = RecordingFileSystem(fs)
        val store = FileSessionStore(recording, root)

        val result = store.saveTrackBundle(track(), marking(), app)
        assertIs<SaveResult.Saved>(result)

        // Payload files and index all exist after a successful save.
        assertTrue(fs.exists(root / "tracks" / "track-1.json"))
        assertTrue(fs.exists(root / "markings" / "mark-1.json"))
        assertTrue(fs.exists(root / "index.json"))

        // Ordering: both payloads are written before the index is touched.
        val markingIdx = recording.writes.indexOfFirst { it.contains("markings") }
        val trackIdx = recording.writes.indexOfFirst { it.contains("tracks") }
        val indexIdx = recording.writes.indexOfFirst { it.substringAfterLast('/').startsWith("index.json") }
        assertTrue(markingIdx >= 0 && trackIdx >= 0 && indexIdx >= 0)
        assertTrue(indexIdx > markingIdx, "index written before marking payload")
        assertTrue(indexIdx > trackIdx, "index written before track payload")

        // Canonical metadata round-trips: schemaVersion, stable IDs, created timestamp,
        // source metadata, and app/build metadata.
        val loaded = store.loadTrack("track-1")
        assertIs<LoadResult.Loaded<TrackPayloadV1>>(loaded)
        assertEquals(CURRENT_TRACK_SCHEMA_VERSION, loaded.value.schemaVersion)
        assertEquals("track-1", loaded.value.track.id)
        assertEquals(1_000L, loaded.value.track.createdAtEpochMillis)
        assertTrue(loaded.value.track.source.isSimulated)
        assertEquals(LocationSource.Simulated, loaded.value.track.source.source)
        assertEquals("0.3.0", loaded.value.app.appVersion)
        assertEquals("42", loaded.value.app.buildNumber)

        // Index carries both a track row and a marking row for Review.
        val index = store.readIndex()
        assertTrue(index.rows.any { it.id == "track-1" && it.type == ReviewEntryType.Track })
        assertTrue(index.rows.any { it.id == "mark-1" && it.type == ReviewEntryType.TrackMarking })
    }

    // Test 2 (D-22, D-23): an injected index-write failure leaves payloads recoverable
    // and never creates a dangling/partial index row.
    @Test
    fun indexWriteFailureLeavesPayloadsAndNoDanglingIndex() {
        val fs = InMemoryFileSystem()
        val failing = FailingWritesFileSystem(fs) { path ->
            path.name.startsWith("index.json")
        }
        val store = FileSessionStore(failing, root)

        assertFailsWith<IOException> {
            store.saveTrackBundle(track(), marking(), app)
        }

        // Payloads were written before the index attempt and survive the failure.
        assertTrue(fs.exists(root / "tracks" / "track-1.json"))
        assertTrue(fs.exists(root / "markings" / "mark-1.json"))
        // No index file (and therefore no dangling row) was committed.
        assertFalse(fs.exists(root / "index.json"))
    }

    // Test 3 (D-21): malformed JSON returns a typed corrupt result and does not crash.
    @Test
    fun corruptPayloadReturnsTypedResult() {
        val fs = InMemoryFileSystem()
        val store = FileSessionStore(fs, root)

        val path = root / "tracks" / "broken.json"
        fs.createDirectories(path.parent!!)
        fs.write(path) { writeUtf8("{ this is not valid json ]") }

        val corrupt = store.loadTrack("broken")
        assertIs<LoadResult.Corrupt>(corrupt)

        // Missing payload is a distinct typed state, not a crash.
        assertIs<LoadResult.NotFound>(store.loadTrack("does-not-exist"))
    }

    // Test 4 (D-21, D-25): all I/O stays under the injected app-private root and no
    // database/ORM schema files are produced.
    @Test
    fun allIoStaysUnderInjectedAppPrivateRoot() {
        val fs = InMemoryFileSystem()
        val store = FileSessionStore(fs, root)

        store.saveTrackBundle(track(), marking(), app)

        val all = fs.allFiles
        assertTrue(all.isNotEmpty())
        assertTrue(all.all { it.toString().startsWith(root.toString()) }, "all files under app-private root")
        assertTrue(
            all.none { it.name.endsWith(".db") || it.name.endsWith(".sqlite") || it.name.endsWith(".sqlite3") },
            "no database/ORM schema files written",
        )
    }
}

/** Records the order in which writable sinks are opened, for ordering assertions. */
private class RecordingFileSystem(
    delegate: FileSystem,
    val writes: MutableList<String> = mutableListOf(),
) : ForwardingFileSystem(delegate) {
    override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path {
        if (functionName == "sink" || functionName == "appendingSink") {
            writes += path.toString()
        }
        return path
    }

    override fun onPathResult(path: Path, functionName: String): Path = path
}

/** Fails writes to paths matching [shouldFail], to exercise atomic-write recovery. */
private class FailingWritesFileSystem(
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
 * artifact. Supports only the operations the store exercises (create directories,
 * atomic temp-write + move, read, exists). Avoids the separate
 * `okio-fakefilesystem` artifact, which is outside the approved coordinate set.
 */
private class InMemoryFileSystem : FileSystem() {

    private val files = LinkedHashMap<Path, okio.ByteString>()
    private val directories = LinkedHashSet<Path>()

    /** Regular files currently stored (used to assert app-private root isolation). */
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
