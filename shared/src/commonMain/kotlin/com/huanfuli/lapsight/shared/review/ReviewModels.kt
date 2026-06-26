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
 * One sector split in a Timing Session Review (D-32).
 */
data class ReviewSectorSplit(
    val sectorId: String,
    val sectorName: String,
    val sectorOrder: Int,
    val lapNumber: Int,
    val splitMillis: Long,
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
    val sectorSplits: List<ReviewSectorSplit>,
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
    // Stub — real implementation in Plan 03-07 Task 2.
    return emptyList()
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
    // Stub — real implementation in Plan 03-07 Task 2.
    return emptyList()
}
