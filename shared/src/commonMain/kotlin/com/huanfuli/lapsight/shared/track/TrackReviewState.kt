package com.huanfuli.lapsight.shared.track

import com.huanfuli.lapsight.shared.GpsQualitySummary
import com.huanfuli.lapsight.shared.review.TraceLayer
import com.huanfuli.lapsight.shared.review.TraceProjection
import com.huanfuli.lapsight.shared.session.GeoPointDto
import com.huanfuli.lapsight.shared.session.SourceMetadata

/**
 * The decision a user can take from the Track Review screen after a marking run
 * (D-12, D-31). Save is only offered when the capture is reference-ready.
 */
enum class TrackReviewDecision {
    Save,
    ReRecord,
    Discard,
}

/**
 * Presentation-ready domain state for the Track Review screen (D-12, D-31).
 *
 * Wraps a [ReferenceLineExtraction] with the information the user needs to
 * choose Save / Re-record / Discard: reference readiness, a GPS/capture quality
 * summary, the not-ready reasons to explain a non-saveable capture, and the
 * editable start/finish + sector inputs that Plan 03-05's Compose UI binds to.
 *
 * This is pure shared Kotlin — no Compose, platform, or file-system dependency —
 * so Android and iOS render identical readiness and quality information. It owns
 * no laps and performs no timing; it only reflects the extractor's output.
 *
 * @property trackName the user-entered name for the track being saved.
 * @property extraction the reference-line extraction result being reviewed.
 * @property startFinish optional start/finish line the user has placed/edited.
 * @property sectors optional sector lines the user has placed/edited.
 */
data class TrackReviewState(
    val trackName: String,
    val extraction: ReferenceLineExtraction,
    val startFinish: StartFinishLineDto? = null,
    val finishLine: StartFinishLineDto? = null,
    val sectors: List<SectorLineDto> = emptyList(),
) {
    /** True when the extractor derived a usable reference line. */
    val isReferenceReady: Boolean get() = extraction.isReady

    /** Capture-quality rollup for the marking run (D-22, D-31). */
    val quality: GpsQualitySummary get() = extraction.quality

    /** Why the capture is not reference-ready, for user-facing explanation. */
    val notReadyReasons: List<NotReadyReason> get() = extraction.notReadyReasons

    /** Number of raw marking samples preserved behind this review. */
    val rawSampleCount: Int get() = extraction.rawSamples.size

    /**
     * Save-ready iff the reference line is ready and present. A not-ready capture
     * can only be Re-recorded or Discarded — never silently promoted to a saved
     * track (D-31).
     */
    val canSave: Boolean
        get() = isReferenceReady && extraction.referenceLine != null

    /** The decisions offered to the user, gated by [canSave]. */
    val availableDecisions: List<TrackReviewDecision>
        get() = if (canSave) {
            listOf(TrackReviewDecision.Save, TrackReviewDecision.ReRecord, TrackReviewDecision.Discard)
        } else {
            listOf(TrackReviewDecision.ReRecord, TrackReviewDecision.Discard)
        }

    /**
     * Build the saveable [Track] for this review. Requires [canSave]; callers
     * must offer Re-record/Discard instead when the capture is not ready.
     */
    fun toTrack(
        id: String,
        createdAtEpochMillis: Long,
        source: SourceMetadata = extraction.markingSession.source,
    ): Track {
        require(canSave) { "Cannot save a track from a not-ready capture; re-record or discard instead." }
        return Track(
            id = id,
            name = trackName,
            createdAtEpochMillis = createdAtEpochMillis,
            sourceMarkingSessionId = extraction.markingSession.id,
            source = source,
            referenceLine = extraction.referenceLine,
            startFinish = startFinish,
            finishLine = finishLine,
            topology = extraction.topology,
            sectors = sectors,
        )
    }

    /**
     * Build the trace layers for Track Review offline vector rendering (D-35).
     *
     * Layers (render in this order, background first):
     * 1. Full marking trace (context) — muted `#9AA8B8`, 2px
     * 2. Reference line (highlighted baseline) — cyan `#62E3FF`, 3px
     * 3. Outlier sections — orange `#FFB84D` 50% alpha, 2px dashed
     * 4. Start/finish line — green `#8CFF9B`, 3px
     * 5. Sector lines — amber `#FFD166`, 2px
     */
    fun buildTraceLayers(
        viewWidth: Double,
        viewHeight: Double,
        padding: Double = 0.05,
    ): List<TraceLayer> {
        val markingSamples = extraction.markingSession.samples
        val refLine = extraction.referenceLine

        // Derive outlier samples from diagnostics: collect samples from loops
        // flagged as Outlier or Dropped.
        val outlierLoopIndices = extraction.diagnostics
            .filter { it.kind == DiagnosticKind.Outlier || it.kind == DiagnosticKind.Dropped }
            .map { it.loopIndex }
            .toSet()

        val outlierSamples: List<com.huanfuli.lapsight.shared.session.LocationSampleDto> =
            if (outlierLoopIndices.isNotEmpty() && extraction.detectedLoopCount > 0) {
                val samplesPerLoop = markingSamples.size / extraction.detectedLoopCount.coerceAtLeast(1)
                outlierLoopIndices.flatMap { loopIdx ->
                    val start = loopIdx * samplesPerLoop
                    val end = minOf((loopIdx + 1) * samplesPerLoop, markingSamples.size)
                    if (start < end) markingSamples.subList(start, end) else emptyList()
                }
            } else {
                emptyList()
            }

        val finishAsLine = finishLine?.let {
            listOf(SectorLineDto("finish", "Finish", 999, it.pointA, it.pointB))
        } ?: emptyList()
        return com.huanfuli.lapsight.shared.review.buildTrackTraceLayers(
            markingSamples = markingSamples,
            referenceLine = refLine,
            startFinish = startFinish,
            sectors = sectors + finishAsLine,
            outlierSamples = outlierSamples,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            padding = padding,
        )
    }

    companion object {
        /** Wrap an extraction result for review under a chosen [name]. */
        fun from(name: String, extraction: ReferenceLineExtraction): TrackReviewState =
            TrackReviewState(trackName = name, extraction = extraction)
    }
}
