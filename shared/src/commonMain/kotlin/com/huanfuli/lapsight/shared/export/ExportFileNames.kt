package com.huanfuli.lapsight.shared.export

/**
 * Generates LapSight-prefixed, sanitized export filenames (D-41).
 *
 * All names are safe for filesystem use: only [a-zA-Z0-9._-] characters
 * are allowed in the name token. Path separators, traversal tokens,
 * XML/control characters are stripped or replaced. Dates are formatted
 * as yyyyMMdd UTC.
 */
object ExportFileNames {
    /**
     * Produces `LapSight_Track_<safeName>_<yyyyMMdd>.json`.
     */
    fun forTrack(trackName: String, createdAtEpochMillis: Long): String {
        val safe = sanitizeNameToken(trackName)
        val date = formatDate(createdAtEpochMillis)
        return "LapSight_Track_${safe}_$date.json"
    }

    /**
     * Produces `LapSight_Session_<safeName>_<yyyyMMdd>.json` or `.gpx`.
     */
    fun forTimingSession(trackName: String, createdAtEpochMillis: Long, extension: String): String {
        val safe = sanitizeNameToken(trackName)
        val date = formatDate(createdAtEpochMillis)
        val ext = when (extension.lowercase()) {
            "gpx" -> "gpx"
            else -> "json"
        }
        return "LapSight_Session_${safe}_$date.$ext"
    }

    /**
     * Produces `LapSight_Capture_<safeName>_<yyyyMMdd>.json`.
     */
    fun forTrackMarking(markingName: String, createdAtEpochMillis: Long): String {
        val safe = sanitizeNameToken(markingName)
        val date = formatDate(createdAtEpochMillis)
        return "LapSight_Capture_${safe}_$date.json"
    }

    /**
     * Sanitizes a user-controlled name to a conservative ASCII filename token.
     *
     * - Strips path separators (/ \), traversal (..), control chars, XML special chars
     * - Replaces any non-[a-zA-Z0-9._-] character with '_'
     * - Strips leading/trailing dots, spaces, dashes
     * - Truncates to ~50 characters
     */
    internal fun sanitizeNameToken(raw: String): String {
        // Replace explicit path separators, traversal, and XML specials with underscores.
        val cleaned = raw
            .replace("\\", "_")
            .replace("/", "_")
            .replace("..", "__")
            .replace("&", "_")
            .replace("<", "_")
            .replace(">", "_")
            .replace("\"", "_")
            .replace("'", "_")
            .replace(Regex("[\u0000-\u001F\u007F]"), "_")
        val safe = cleaned.map { c ->
            when {
                c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '.' || c == '_' || c == '-' -> c
                else -> '_'
            }
        }.joinToString("")
        // Strip leading/trailing dots, spaces, dashes, underscores.
        val trimmed = safe.trim { it == '.' || it == ' ' || it == '-' || it == '_' }
        return if (trimmed.length > 50) trimmed.take(50) else trimmed.ifEmpty { "unnamed" }
    }

    /**
     * Formats epoch millis as yyyyMMdd UTC.
     */
    internal fun formatDate(epochMillis: Long): String {
        val secondsTotal = epochMillis / 1000
        val daysTotal = secondsTotal / 86_400
        val (y, m, d) = gregorianFromEpochDays(daysTotal)
        return "$y${m.toString().padStart(2, '0')}${d.toString().padStart(2, '0')}"
    }

    /** Howard Hinnant's civil-from-days algorithm (public domain). */
    private fun gregorianFromEpochDays(daysSinceEpoch: Long): Triple<Int, Int, Int> {
        var z = daysSinceEpoch + 719468
        val era = if (z >= 0) z / 146097 else (z - 146096) / 146097
        val doe = (z - era * 146097).toInt()
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        var y = yoe + (era * 400).toInt()
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        if (m <= 2) y += 1
        return Triple(y, m, d)
    }
}
