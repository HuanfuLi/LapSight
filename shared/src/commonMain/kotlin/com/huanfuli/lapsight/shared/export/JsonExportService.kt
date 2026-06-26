package com.huanfuli.lapsight.shared.export

import com.huanfuli.lapsight.shared.storage.LocalSessionStore

/**
 * Canonical JSON export service for Track and TimingSession bundles (D-38).
 *
 * STUB for RED phase — throws NotImplementedError. Full implementation in Task 2.
 */
class JsonExportService(
    private val store: LocalSessionStore,
) {
    fun exportTrack(trackId: String): ByteArray =
        throw NotImplementedError("JsonExportService not implemented (RED phase)")

    fun exportTrackMarking(markingId: String): ByteArray =
        throw NotImplementedError("JsonExportService not implemented (RED phase)")

    fun exportTimingSession(sessionId: String): ByteArray =
        throw NotImplementedError("JsonExportService not implemented (RED phase)")
}
