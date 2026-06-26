package com.huanfuli.lapsight.shared.export

import com.huanfuli.lapsight.shared.storage.LoadResult
import com.huanfuli.lapsight.shared.storage.LocalSessionStore

/**
 * GPX 1.1 compatibility export service for TimingSessions (D-39, T-03-21).
 *
 * Emits standards-compatible GPS track points with optional elevation and time,
 * XML-escaped metadata, and expected raw-sample count. No LapSight custom
 * extensions are added — this produces clean GPX that external tools can read.
 *
 * Timestamps are derived from the session's recorded start epoch plus each
 * sample's relative elapsed millis.
 */
class GpxExportService(
    private val store: LocalSessionStore,
) {
    /**
     * Exports the TimingSession identified by [sessionId] as GPX 1.1 XML bytes.
     *
     * @throws ExportNotFoundException if the session does not exist.
     * @throws ExportFailedException if the payload is corrupt.
     */
    fun exportTimingSession(sessionId: String): ByteArray {
        val result = store.loadTimingSession(sessionId)
        val payload = when (result) {
            is LoadResult.Loaded -> result.value
            is LoadResult.NotFound -> throw ExportNotFoundException("Session not found: $sessionId")
            is LoadResult.Corrupt -> throw ExportFailedException("Session payload corrupt: ${result.reason}")
        }

        val session = payload.session
        val trackName = escapeXml(session.trackName)
        val sessionStartEpoch = session.createdAtEpochMillis

        return buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<gpx version=\"1.1\" creator=\"LapSight\"")
            appendLine("  xmlns=\"http://www.topografix.com/GPX/1/1\">")
            appendLine("  <trk>")
            appendLine("    <name>$trackName</name>")
            appendLine("    <trkseg>")

            for (sample in payload.samples) {
                val lat = sample.latitude
                val lon = sample.longitude
                val ele = sample.altitudeMeters
                // GPX time = session start epoch + sample relative elapsed millis
                val absMillis = sessionStartEpoch + sample.elapsedMillis
                val timeIso = epochMillisToIso8601(absMillis)

                append("      <trkpt lat=\"$lat\" lon=\"$lon\">")
                if (ele != null) {
                    append("<ele>$ele</ele>")
                }
                if (timeIso != null) {
                    append("<time>$timeIso</time>")
                }
                appendLine("</trkpt>")
            }

            appendLine("    </trkseg>")
            appendLine("  </trk>")
            appendLine("</gpx>")
        }.encodeToByteArray()
    }

    /**
     * Escapes XML special characters in text and attribute values (T-03-21).
     */
    private fun escapeXml(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    /**
     * Converts epoch millis to ISO 8601 UTC string: `yyyy-MM-ddTHH:mm:ss.SSSZ`.
     * Returns null for invalid/negative inputs.
     */
    private fun epochMillisToIso8601(epochMillis: Long): String? {
        if (epochMillis < 0) return null
        val totalSeconds = epochMillis / 1000
        val millis = (epochMillis % 1000)
        val daysTotal = totalSeconds / 86_400
        val secsOfDay = totalSeconds % 86_400
        val (y, mo, d) = gregorianFromEpochDays(daysTotal)
        val h = (secsOfDay / 3600)
        val m = ((secsOfDay % 3600) / 60)
        val s = (secsOfDay % 60)
        return "$y-${mo.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}" +
            "T${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:" +
            "${s.toString().padStart(2, '0')}.${millis.toString().padStart(3, '0')}Z"
    }

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
