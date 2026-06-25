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
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Wave 0 coverage for the versioned local storage foundation (D-21 through D-25).
 *
 * All I/O runs against an injected Okio [FakeFileSystem] rooted at an app-private
 * path, so no real platform path or database/ORM is exercised.
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
        val fs = FakeFileSystem()
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
        val fs = FakeFileSystem()
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
        val fs = FakeFileSystem()
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
        val fs = FakeFileSystem()
        val store = FileSessionStore(fs, root)

        store.saveTrackBundle(track(), marking(), app)

        val all = fs.allPaths
        assertTrue(all.isNotEmpty())
        assertTrue(all.all { it.toString().startsWith(root.toString()) }, "all paths under app-private root")
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
