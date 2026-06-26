package com.huanfuli.lapsight.shared.export

/**
 * Generates LapSight-prefixed, sanitized export filenames (D-41).
 *
 * STUB for RED phase — returns intentionally wrong names so ALL RED tests fail.
 * Full implementation in Task 2.
 */
object ExportFileNames {
    fun forTrack(trackName: String, createdAtEpochMillis: Long): String {
        // STUB: raw unsanitized name with ../ traversal — will fail RED tests.
        return "../$trackName.json"
    }

    fun forTimingSession(trackName: String, createdAtEpochMillis: Long, extension: String): String {
        // STUB: raw unsanitized name — will fail RED tests.
        return "$trackName.$extension"
    }
}
