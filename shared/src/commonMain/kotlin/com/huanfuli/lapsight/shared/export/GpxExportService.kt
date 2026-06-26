package com.huanfuli.lapsight.shared.export

import com.huanfuli.lapsight.shared.storage.LocalSessionStore

/**
 * GPX 1.1 compatibility export service for TimingSessions (D-39).
 *
 * STUB for RED phase — throws NotImplementedError. Full implementation in Task 2.
 */
class GpxExportService(
    private val store: LocalSessionStore,
) {
    fun exportTimingSession(sessionId: String): ByteArray =
        throw NotImplementedError("GpxExportService not implemented (RED phase)")
}
