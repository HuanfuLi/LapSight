package com.huanfuli.lapsight.shared.storage

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.session.GhostReferencePayloadV1
import com.huanfuli.lapsight.shared.session.LocationSampleDto
import com.huanfuli.lapsight.shared.session.ProgressPointDto
import com.huanfuli.lapsight.shared.session.SourceMetadata
import com.huanfuli.lapsight.shared.session.toDto
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Wave 0 coverage for full ghost reference-lap storage (Plan 04-02 Task 1).
 *
 * Asserts the product invariants D-03 (reference scoped by Track), D-04/D-24
 * (simulated references isolated from real Track ghost state), D-05 (persisted
 * references carry raw best-lap samples AND a precomputed progress curve), and
 * the storage threats T-04-04 (source/Track boundary) and T-04-05 (versioned
 * schema, typed corrupt-load result).
 *
 * File I/O runs against an injected in-memory [FileSystem] built on the approved
 * Okio core artifact (the `okio-fakefilesystem` test artifact is intentionally
 * not added — mirrors `FileSessionStoreTest`).
 */
class GhostReferenceStoreTest {

    private val root = "/lapsight".toPath()
    private val app = AppMetadata(appVersion = "0.4.0", buildNumber = "1", platform = "test")

    private fun realSource() = SourceMetadata(
        source = LocationSource.PhoneGps,
        isSimulated = false,
    )

    private fun simSource() = SourceMetadata(
        source = LocationSource.Simulated,
        isSimulated = true,
        label = "Demo",
    )

    private fun samples(): List<LocationSampleDto> = listOf(
        LocationSample(0L, 39.8121, -86.1062, 5.0, 12.0, 90.0, 200.0, LocationSource.PhoneGps).toDto(),
        LocationSample(1_000L, 39.8126, -86.1062, 5.0, 12.0, 90.0, 200.0, LocationSource.PhoneGps).toDto(),
    )

    private fun progressPoints(): List<ProgressPointDto> = listOf(
        ProgressPointDto(
            elapsedMillis = 0L,
            progressMeters = 0.0,
            normalizedProgress = 0.0,
            latitude = 39.8121,
            longitude = -86.1062,
            localX = 0.0,
            localY = 0.0,
            speedMetersPerSecond = 12.0,
            headingDegrees = 90.0,
            horizontalAccuracyMeters = 5.0,
        ),
        ProgressPointDto(
            elapsedMillis = 1_000L,
            progressMeters = 100.0,
            normalizedProgress = 1.0,
            latitude = 39.8126,
            longitude = -86.1062,
            localX = 0.0,
            localY = 100.0,
            speedMetersPerSecond = 12.0,
            headingDegrees = 90.0,
            horizontalAccuracyMeters = 5.0,
        ),
    )

    private fun payload(
        trackId: String = "track-1",
        sessionId: String = "session-1",
        source: SourceMetadata = realSource(),
    ) = GhostReferencePayloadV1(
        trackId = trackId,
        sessionId = sessionId,
        lapNumber = 1,
        durationMillis = 1_000L,
        source = source,
        totalDistanceMeters = 100.0,
        samples = samples(),
        progressPoints = progressPoints(),
        app = app,
    )

    // D-05: full payload round trip preserves raw samples AND progress curve.
    @Test
    fun fileStoreReferenceRoundTripPreservesSamplesAndProgressPoints() {
        val fs = GhostInMemoryFileSystem()
        val store = FileSessionStore(fs, root)

        store.saveReferenceLap(payload(), app)
        val loaded = store.loadReferenceLap("track-1", isSimulated = false)

        assertIs<LoadResult.Loaded<GhostReferencePayloadV1>>(loaded)
        assertEquals("track-1", loaded.value.trackId)
        assertEquals(1, loaded.value.lapNumber)
        assertEquals(1_000L, loaded.value.durationMillis)
        assertEquals(100.0, loaded.value.totalDistanceMeters)
        assertEquals(2, loaded.value.samples.size, "raw best-lap samples must survive the round trip")
        assertEquals(2, loaded.value.progressPoints.size, "precomputed progress curve must survive the round trip")
        assertEquals(1.0, loaded.value.progressPoints.last().normalizedProgress)
        assertFalse(loaded.value.source.isSimulated)
    }

    @Test
    fun inMemoryStoreReferenceRoundTripPreservesSamplesAndProgressPoints() {
        val store = InMemorySessionStore()

        store.saveReferenceLap(payload(), app)
        val loaded = store.loadReferenceLap("track-1", isSimulated = false)

        assertIs<LoadResult.Loaded<GhostReferencePayloadV1>>(loaded)
        assertEquals(2, loaded.value.samples.size)
        assertEquals(2, loaded.value.progressPoints.size)
    }

    // T-04-05: a missing reference is a typed NotFound, never a crash.
    @Test
    fun missingReferenceReturnsNotFound() {
        val store = FileSessionStore(GhostInMemoryFileSystem(), root)
        assertIs<LoadResult.NotFound>(store.loadReferenceLap("no-such-track", isSimulated = false))
        assertIs<LoadResult.NotFound>(InMemorySessionStore().loadReferenceLap("no-such-track", isSimulated = false))
    }

    // T-04-05: corrupt JSON is a typed Corrupt result, never a crash.
    @Test
    fun corruptReferenceReturnsTypedCorrupt() {
        val fs = GhostInMemoryFileSystem()
        val store = FileSessionStore(fs, root)

        val path = root / "references" / "track-1__real.json"
        fs.createDirectories(path.parent!!)
        fs.write(path) { writeUtf8("{ this is not valid json ]") }

        assertIs<LoadResult.Corrupt>(store.loadReferenceLap("track-1", isSimulated = false))
    }

    // D-04/D-24, T-04-04: a real reference lookup must ignore simulated payloads.
    @Test
    fun realLookupIgnoresSimulatedReferenceInFileStore() {
        val fs = GhostInMemoryFileSystem()
        val store = FileSessionStore(fs, root)

        store.saveReferenceLap(payload(source = simSource()), app)

        assertIs<LoadResult.NotFound>(
            store.loadReferenceLap("track-1", isSimulated = false),
        )
        assertIs<LoadResult.Loaded<GhostReferencePayloadV1>>(
            store.loadReferenceLap("track-1", isSimulated = true),
        )
    }

    @Test
    fun realLookupIgnoresSimulatedReferenceInMemoryStore() {
        val store = InMemorySessionStore()

        store.saveReferenceLap(payload(source = simSource()), app)

        assertIs<LoadResult.NotFound>(store.loadReferenceLap("track-1", isSimulated = false))
        assertIs<LoadResult.Loaded<GhostReferencePayloadV1>>(store.loadReferenceLap("track-1", isSimulated = true))
    }

    // D-03: references are scoped per Track; one Track's reference is not another's.
    @Test
    fun referencesAreScopedByTrack() {
        val store = InMemorySessionStore()
        store.saveReferenceLap(payload(trackId = "track-a"), app)

        assertIs<LoadResult.Loaded<GhostReferencePayloadV1>>(store.loadReferenceLap("track-a", isSimulated = false))
        assertIs<LoadResult.NotFound>(store.loadReferenceLap("track-b", isSimulated = false))
    }
}

/**
 * Minimal multiplatform in-memory [FileSystem] built on the approved Okio core
 * artifact (mirrors the helper in `FileSessionStoreTest`, which is file-private).
 * Supports only create-directories, atomic temp-write + move, read, exists, and
 * delete — the operations the store exercises.
 */
private class GhostInMemoryFileSystem : FileSystem() {

    private val files = LinkedHashMap<Path, okio.ByteString>()
    private val directories = LinkedHashSet<Path>()

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
