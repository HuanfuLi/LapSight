package com.huanfuli.lapsight.shared.review

import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.GpsQualitySummary as DashGpsQualitySummary
import com.huanfuli.lapsight.shared.session.GeoPointDto
import com.huanfuli.lapsight.shared.session.GpsQualitySummary as SessionGpsQualitySummary
import com.huanfuli.lapsight.shared.session.LapDto
import com.huanfuli.lapsight.shared.session.LocationSampleDto
import com.huanfuli.lapsight.shared.session.SectorEventDto
import com.huanfuli.lapsight.shared.session.SourceMetadata
import com.huanfuli.lapsight.shared.session.TimingSessionPayloadV1
import com.huanfuli.lapsight.shared.storage.LocalSessionStore
import com.huanfuli.lapsight.shared.storage.LoadResult
import com.huanfuli.lapsight.shared.track.SectorLineDto
import com.huanfuli.lapsight.shared.track.StartFinishLineDto
import com.huanfuli.lapsight.shared.track.TrackReferenceLine

/**
 * Review summary types for saved TimingSessions (SESS-02, D-32).
 *
 * These are pure shared Kotlin presentation models: Android and iOS render
 * identical summary strings. The summary is derived from a canonical
 * [TimingSessionPayloadV1] loaded from the local-first store.
 */

/**
 * One lap row in a Timing Session Review (SESS-02, D-32).
 */
data class ReviewLapRow(
    val lapNumber: Int,
    val durationMillis: Long,
)

/**
 * One LEGACY cumulative line split in a Timing Session Review (D-32).
 *
 * This is the V1 `LegacyCumulativeSplit`: a single cumulative [splitMillis] from
 * the lap start to one sector-LINE crossing. It is preserved for V1 history and is
 * NEVER inferred from or relabeled as a complete Sector interval; the complete-
 * interval V2 shape is [ReviewCompleteSector].
 */
data class ReviewSectorSplit(
    val sectorId: String,
    val sectorName: String,
    val sectorOrder: Int,
    val lapNumber: Int,
    val splitMillis: Long,
)

/**
 * One complete Sector INTERVAL in a Timing Session Review (D-06, D-11).
 *
 * Carries BOTH the adjacent-crossing [durationMillis] and the separate
 * [cumulativeSplitMillis] from the lap start, so V2 history renders complete
 * Sector coverage without recomputing it from line crossings.
 */
data class ReviewCompleteSector(
    val sectorId: String,
    val sectorName: String,
    val sectorOrder: Int,
    val lapNumber: Int,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val durationMillis: Long,
    val cumulativeSplitMillis: Long,
)

/**
 * Presentation-ready review summary for a saved TimingSession (SESS-02, D-32).
 *
 * Exposes: track name, date, total duration, best lap, lap list, sample count,
 * GPS quality summary, sector splits, and source/Demo badge. [newTrackBest] is
 * true when this session holds the per-Track fastest valid lap.
 */
data class TimingSessionReviewSummary(
    val sessionId: String,
    val trackId: String,
    val trackName: String,
    val createdAtEpochMillis: Long,
    val totalDurationMillis: Long,
    val bestLapMillis: Long?,
    val laps: List<ReviewLapRow>,
    /** V1 legacy cumulative line splits (`LegacyCumulativeSplit`); empty for V2-only data. */
    val sectorSplits: List<ReviewSectorSplit>,
    /** V2 complete Sector intervals with separate duration and cumulative split; empty for V1 history. */
    val completeSectors: List<ReviewCompleteSector>,
    val sampleCount: Int,
    val gpsQuality: SessionGpsQualitySummary,
    val source: LocationSource,
    val isDemo: Boolean,
    val newTrackBest: Boolean,
)

/**
 * Derives a [TimingSessionReviewSummary] from a saved timing-session payload in
 * the local-first store. Returns null when the session is missing/corrupt.
 */
object ReviewSummaries {
    fun fromTimingSession(store: LocalSessionStore, sessionId: String): TimingSessionReviewSummary? {
        val result = store.loadTimingSession(sessionId)
        if (result !is LoadResult.Loaded<*>) return null
        @Suppress("UNCHECKED_CAST")
        val payload = (result as LoadResult.Loaded<TimingSessionPayloadV1>).value
        return fromPayload(store, payload)
    }

    fun fromPayload(store: LocalSessionStore, payload: TimingSessionPayloadV1): TimingSessionReviewSummary {
        val laps = payload.laps.map { ReviewLapRow(it.lapNumber, it.durationMillis) }
        val bestLap = payload.laps.minByOrNull { it.durationMillis }?.durationMillis
        val sectors = payload.sectorEvents.map {
            ReviewSectorSplit(
                sectorId = it.sectorId,
                sectorName = it.sectorId,
                sectorOrder = it.sectorOrder,
                lapNumber = it.lapNumber,
                splitMillis = it.splitMillis,
            )
        }
        // V2 complete Sector intervals are rendered from the persisted snapshot;
        // they are NEVER inferred from the legacy line splits above (D-06, D-11).
        val completeSectors = payload.sectorResults.map {
            ReviewCompleteSector(
                sectorId = it.sectorId,
                sectorName = it.sectorId,
                sectorOrder = it.sectorOrder,
                lapNumber = it.lapNumber,
                startedAtMillis = it.startedAtMillis,
                endedAtMillis = it.endedAtMillis,
                durationMillis = it.durationMillis,
                cumulativeSplitMillis = it.cumulativeSplitMillis,
            )
        }
        val ghost = store.ghostCandidateForTrack(payload.session.trackId)
        val newTrackBest = ghost != null &&
            bestLap != null &&
            ghost.lapDurationMillis == bestLap &&
            ghost.sessionId == payload.session.id
        return TimingSessionReviewSummary(
            sessionId = payload.session.id,
            trackId = payload.session.trackId,
            trackName = payload.session.trackName,
            createdAtEpochMillis = payload.session.createdAtEpochMillis,
            totalDurationMillis = payload.totalDurationMillis,
            bestLapMillis = bestLap,
            laps = laps,
            sectorSplits = sectors,
            completeSectors = completeSectors,
            sampleCount = payload.samples.size,
            gpsQuality = payload.gpsQuality,
            source = payload.session.source.source,
            isDemo = payload.session.source.isSimulated,
            newTrackBest = newTrackBest,
        )
    }
}

/**
 * Build trace layers for the Track Review offline vector trace (D-35).
 *
 * Layers rendered in order (background first):
 * 1. Full marking trace (context) — muted #9AA8B8, 2px
 * 2. Reference line (highlighted baseline) — cyan #62E3FF, 3px
 * 3. Outlier sections — orange #FFB84D 50% alpha, 2px dashed
 * 4. Start/finish line — green #8CFF9B, 3px
 * 5. Sector lines — amber #FFD166, 2px
 *
 * @param markingSamples  raw marking session samples (full context trace).
 * @param referenceLine   extracted reference line, or null.
 * @param startFinish     start/finish line, or null.
 * @param sectors         sector lines.
 * @param outlierSamples  outlier/rejected sample points.
 * @param viewWidth       target canvas width.
 * @param viewHeight      target canvas height.
 * @param padding         fraction of smaller dimension reserved as padding.
 */
fun buildTrackTraceLayers(
    markingSamples: List<LocationSampleDto>,
    referenceLine: TrackReferenceLine?,
    startFinish: StartFinishLineDto?,
    sectors: List<SectorLineDto>,
    outlierSamples: List<LocationSampleDto>,
    viewWidth: Double,
    viewHeight: Double,
    padding: Double = 0.05,
): List<TraceLayer> {
    // Collect all geo-point layers needed for projection.
    val markingDtos = markingSamples.map { GeoPointDto(it.latitude, it.longitude) }
    val refDtos = referenceLine?.points ?: emptyList()
    val outlierDtos = outlierSamples.map { GeoPointDto(it.latitude, it.longitude) }
    val lineDtos: List<GeoPointDto> = buildList {
        if (startFinish != null) {
            add(startFinish.pointA)
            add(startFinish.pointB)
        }
        for (s in sectors) {
            add(s.pointA)
            add(s.pointB)
        }
    }

    val allLayerDtos = listOfNotNull(
        markingDtos.ifEmpty { null },
        refDtos.ifEmpty { null },
        outlierDtos.ifEmpty { null },
        lineDtos.ifEmpty { null },
    )

    if (allLayerDtos.isEmpty()) return emptyList()

    val projected = TraceProjection.project(allLayerDtos, viewWidth, viewHeight, padding)

    fun layer(
        name: String,
        color: Long,
        strokeWidth: Float,
        dashed: Boolean,
        points: List<TracePoint>,
    ): TraceLayer = TraceLayer(name = name, points = points, color = color, strokeWidth = strokeWidth, dashed = dashed)

    val layers = mutableListOf<TraceLayer>()
    var idx = 0

    // Layer 1: Full marking trace (context)
    if (markingDtos.isNotEmpty()) {
        layers += layer("Marking trace", 0xFF9AA8B8, 2f, false, projected[idx])
        idx++
    }

    // Layer 2: Reference line (highlighted baseline)
    if (refDtos.isNotEmpty()) {
        layers += layer("Reference line", 0xFF62E3FF, 3f, false, projected[idx])
        idx++
    }

    // Layer 3: Outlier sections
    if (outlierDtos.isNotEmpty()) {
        layers += layer("Outlier sections", 0x80FFB84D, 2f, true, projected[idx])
        idx++
    }

    // Layer 4-5: Start/finish and sector lines (share one projected layer)
    if (lineDtos.isNotEmpty()) {
        val linePoints = projected[idx]
        if (startFinish != null) {
            // First 2 points are start/finish
            val sf = linePoints.take(2)
            if (sf.size == 2) {
                layers += layer("Start/finish", 0xFF8CFF9B, 3f, false, sf)
            }
        }
        val sectorStarts = if (startFinish != null) 2 else 0
        if (sectors.isNotEmpty() && linePoints.size > sectorStarts) {
            val sectorPoints = linePoints.drop(sectorStarts)
            if (sectorPoints.size >= 2) {
                layers += layer("Sector lines", 0xFFFFD166, 2f, false, sectorPoints)
            }
        }
        idx++
    }

    return layers
}

/**
 * Build trace layers for the Timing Session Review offline vector trace (D-36).
 *
 * Layers rendered in order (background first):
 * 1. Reference line baseline — cyan #62E3FF, 3px
 * 2. Session trace — muted #9AA8B8, 2px
 * 3. Start/finish line — green #8CFF9B, 3px
 * 4. Sector lines — amber #FFD166, 2px
 * 5. Selected/best lap highlight — cyan #62E3FF, 4px (empty until a lap is selected)
 *
 * @param referenceLinePoints  track reference line points.
 * @param sessionSamples       timing session raw samples.
 * @param startFinish          start/finish line.
 * @param sectors              sector lines.
 * @param selectedLapStartMillis  start millis of the selected/best lap, or null.
 * @param selectedLapEndMillis    end millis of the selected/best lap, or null.
 * @param viewWidth            target canvas width.
 * @param viewHeight           target canvas height.
 * @param padding              fraction of smaller dimension reserved as padding.
 */
fun buildTimingTraceLayers(
    referenceLinePoints: List<GeoPointDto>,
    sessionSamples: List<LocationSampleDto>,
    startFinish: StartFinishLineDto?,
    sectors: List<SectorLineDto>,
    selectedLapStartMillis: Long?,
    selectedLapEndMillis: Long?,
    viewWidth: Double,
    viewHeight: Double,
    padding: Double = 0.05,
): List<TraceLayer> {
    val sessionDtos = sessionSamples.map { GeoPointDto(it.latitude, it.longitude) }
    val lineDtos: List<GeoPointDto> = buildList {
        if (startFinish != null) {
            add(startFinish.pointA)
            add(startFinish.pointB)
        }
        for (s in sectors) {
            add(s.pointA)
            add(s.pointB)
        }
    }
    val highlightDtos = if (selectedLapStartMillis != null && selectedLapEndMillis != null) {
        sessionSamples
            .filter { it.elapsedMillis in selectedLapStartMillis..selectedLapEndMillis }
            .map { GeoPointDto(it.latitude, it.longitude) }
    } else {
        emptyList()
    }

    val allLayerDtos = listOfNotNull(
        referenceLinePoints.ifEmpty { null },
        sessionDtos.ifEmpty { null },
        lineDtos.ifEmpty { null },
        highlightDtos.ifEmpty { null },
    )

    if (allLayerDtos.isEmpty()) return emptyList()

    val projected = TraceProjection.project(allLayerDtos, viewWidth, viewHeight, padding)

    fun layer(
        name: String,
        color: Long,
        strokeWidth: Float,
        dashed: Boolean,
        points: List<TracePoint>,
    ): TraceLayer = TraceLayer(name = name, points = points, color = color, strokeWidth = strokeWidth, dashed = dashed)

    val layers = mutableListOf<TraceLayer>()
    var idx = 0

    // Layer 1: Reference line baseline
    if (referenceLinePoints.isNotEmpty()) {
        layers += layer("Reference baseline", 0xFF62E3FF, 3f, false, projected[idx])
        idx++
    }

    // Layer 2: Session trace
    if (sessionDtos.isNotEmpty()) {
        layers += layer("Session trace", 0xFF9AA8B8, 2f, false, projected[idx])
        idx++
    }

    // Layer 3-4: Start/finish and sector lines (share one projected layer)
    if (lineDtos.isNotEmpty()) {
        val linePoints = projected[idx]
        if (startFinish != null) {
            val sf = linePoints.take(2)
            if (sf.size == 2) {
                layers += layer("Start/finish", 0xFF8CFF9B, 3f, false, sf)
            }
        }
        val sectorStarts = if (startFinish != null) 2 else 0
        if (sectors.isNotEmpty() && linePoints.size > sectorStarts) {
            val sectorPoints = linePoints.drop(sectorStarts)
            if (sectorPoints.size >= 2) {
                layers += layer("Sector lines", 0xFFFFD166, 2f, false, sectorPoints)
            }
        }
        idx++
    }

    // Layer 5: Selected/best lap highlight
    if (highlightDtos.isNotEmpty()) {
        layers += layer("Best lap highlight", 0xFF62E3FF, 4f, false, projected[idx])
        idx++
    }

    return layers
}
