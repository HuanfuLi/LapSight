package com.huanfuli.lapsight.shared.track

import com.huanfuli.lapsight.shared.GpsQualitySummary
import com.huanfuli.lapsight.shared.session.GeoPointDto
import com.huanfuli.lapsight.shared.session.LocationSampleDto
import com.huanfuli.lapsight.shared.session.toModel
import com.huanfuli.lapsight.shared.lap.GeoPoint
import com.huanfuli.lapsight.shared.lap.LocalPoint
import com.huanfuli.lapsight.shared.lap.LocalProjection
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Why a not-ready reason exists, so Track Review can explain a non-saveable
 * capture instead of silently dropping it (D-12, D-31).
 */
enum class NotReadyReason {
    /** Too few complete loops were found to derive a reliable reference. */
    InsufficientLoops,

    /** Accepted loops disagree too much (GPS noise / drift). */
    InconsistentLoops,

    /** Sampling is too sparse / dropped to resolve the loop shape. */
    SparseSampling,

    /** No repeated closed structure could be detected at all. */
    NoStructure,
}

/** Classification of a recorded extraction diagnostic — never a lap (D-06..D-09). */
enum class DiagnosticKind {
    /** A loop rejected for jumping far off the consensus shape. */
    Outlier,

    /** A loop kept-but-flagged or rejected for excessive scatter. */
    Noisy,

    /** A loop rejected/flagged for too few samples. */
    Dropped,
}

/**
 * A non-lap note about a section of the marking run. These record *why* a part
 * of a continuous capture was rejected or flagged; they are explicitly NOT
 * [com.huanfuli.lapsight.shared.lap.LapEvent]s. Marking is continuous capture,
 * not lap timing (D-06, D-07).
 *
 * @property loopIndex the zero-based detected loop this note refers to.
 * @property kind the category of issue.
 * @property rmsDeviationMeters RMS positional deviation from the consensus loop.
 * @property detail human-readable explanation.
 */
data class ExtractionDiagnostic(
    val loopIndex: Int,
    val kind: DiagnosticKind,
    val rmsDeviationMeters: Double,
    val detail: String,
)

/**
 * Result of running [ReferenceLineExtractor] over one continuous marking run.
 *
 * Holds the derived (or absent) reference line, readiness, per-loop diagnostics,
 * a capture-quality rollup, and — crucially — a link to the *unmodified* source
 * [TrackMarkingSession] so a future, better algorithm can recompute without
 * losing original evidence (D-04, D-10, D-18).
 *
 * It never carries laps: outlier/noisy/dropped sections live in [diagnostics].
 */
data class ReferenceLineExtraction(
    val markingSession: TrackMarkingSession,
    val referenceLine: TrackReferenceLine?,
    val isReady: Boolean,
    val detectedLoopCount: Int,
    val acceptedLoopCount: Int,
    val rejectedLoopCount: Int,
    val diagnostics: List<ExtractionDiagnostic>,
    val quality: GpsQualitySummary,
    val notReadyReasons: List<NotReadyReason>,
) {
    /** The preserved raw marking samples, unchanged from capture. */
    val rawSamples: List<LocationSampleDto> get() = markingSession.samples
}

/**
 * Pure, platform-free service that turns a continuous track-marking run into a
 * [TrackReferenceLine] by finding repeated spatial structure (D-18).
 *
 * The algorithm, per RESEARCH A1 and D-06..D-12:
 *  1. project lat/lon to a local meter plane around the first sample;
 *  2. segment the continuous path into complete loops by angular winding around
 *     the path centroid (robust to sparse sampling and short excursions);
 *  3. resample each loop to a fixed count by normalized arc length;
 *  4. take a per-index *median* loop as an outlier-robust consensus;
 *  5. reject loops whose deviation from the consensus exceeds a floor (the bad
 *     off-track loop) — recorded as [ExtractionDiagnostic]s, never laps;
 *  6. average the accepted loops into the reference line;
 *  7. gate readiness on enough consistent, well-sampled loops, degrading to an
 *     explicit not-ready result (never an exception) on poor captures.
 *
 * It does not call `LapEngine` and never constructs a `LapEvent`.
 */
object ReferenceLineExtractor {

    /**
     * Tunable thresholds. Defaults are calibrated against the Phase 3 fixtures
     * (D-04): clean/minimum/outlier captures extract; noise/drift and
     * dropped/low-frequency captures degrade to not-ready.
     */
    data class Config(
        val resampleCount: Int = 64,
        val minLoops: Int = 3,
        val minMedianPointsPerLoop: Int = 16,
        val maxConsensusRmsMeters: Double = 3.0,
        val outlierRmsFloorMeters: Double = 30.0,
    ) {
        init {
            require(resampleCount >= 8) { "resampleCount must be >= 8" }
            require(minLoops >= 1) { "minLoops must be >= 1" }
            require(minMedianPointsPerLoop >= 1) { "minMedianPointsPerLoop must be >= 1" }
            require(maxConsensusRmsMeters > 0) { "maxConsensusRmsMeters must be > 0" }
            require(outlierRmsFloorMeters > 0) { "outlierRmsFloorMeters must be > 0" }
        }
    }

    /** Absolute floor below which no repeated structure can be found. */
    private const val MIN_SAMPLES = 12

    /** A single detected loop: its raw sample slice plus local-meter points. */
    private data class Loop(val index: Int, val local: List<LocalPoint>)

    fun extract(marking: TrackMarkingSession, config: Config = Config()): ReferenceLineExtraction {
        val domainSamples = marking.samples.map { it.toModel() }
        val quality = GpsQualitySummary.from(domainSamples)

        // Too little data to find any repeated structure at all.
        if (marking.samples.size < MIN_SAMPLES) {
            return notReady(marking, quality, NotReadyReason.NoStructure)
        }

        val origin = GeoPoint(marking.samples.first().latitude, marking.samples.first().longitude)
        val projection = LocalProjection(origin)
        val local = marking.samples.map {
            projection.toLocal(GeoPoint(it.latitude, it.longitude))
        }

        val loops = segmentLoops(local)
        if (loops.size < config.minLoops) {
            return notReady(marking, quality, NotReadyReason.NoStructure, detected = loops.size)
        }

        // Resample every loop onto a common phase by anchoring each loop's start
        // to the track point nearest the capture's first sample, then walking a
        // fixed number of equal arc-length steps. This phase-aligns identical
        // loops so consensus deviation reflects real GPS scatter, not sampling
        // offset.
        val anchor = local.first()
        val resampled = loops.map { resampleAnchored(it.local, config.resampleCount, anchor) }
        val consensus = medianLoop(resampled, config.resampleCount)

        // Reject loops that jump far off the consensus shape (e.g. the bad loop).
        val diagnostics = mutableListOf<ExtractionDiagnostic>()
        val accepted = mutableListOf<List<LocalPoint>>()
        val acceptedRawCounts = mutableListOf<Int>()
        var rejected = 0
        resampled.forEachIndexed { i, loop ->
            val rms = rmsDeviation(loop, consensus)
            if (rms > config.outlierRmsFloorMeters) {
                rejected++
                diagnostics += ExtractionDiagnostic(
                    loopIndex = loops[i].index,
                    kind = DiagnosticKind.Outlier,
                    rmsDeviationMeters = rms,
                    detail = "Loop deviates ${rms.toInt()}m from the consensus shape; rejected as an outlier.",
                )
            } else {
                accepted += loop
                acceptedRawCounts += loops[i].local.size
            }
        }

        val notReadyReasons = mutableListOf<NotReadyReason>()
        if (accepted.size < config.minLoops) {
            notReadyReasons += NotReadyReason.InsufficientLoops
        }

        // Sparse / dropped capture: too few raw samples per accepted loop.
        val medianPointsPerLoop = medianOf(acceptedRawCounts.map { it.toDouble() })
        if (medianPointsPerLoop < config.minMedianPointsPerLoop) {
            notReadyReasons += NotReadyReason.SparseSampling
            diagnostics += ExtractionDiagnostic(
                loopIndex = -1,
                kind = DiagnosticKind.Dropped,
                rmsDeviationMeters = 0.0,
                detail = "Median ${medianPointsPerLoop.toInt()} samples/loop is below the resolution needed for a reliable reference.",
            )
        }

        // Consistency of the accepted loops (catches GPS noise / drift).
        val meanLoop = meanLoop(accepted, config.resampleCount)
        val consensusRms = if (accepted.isEmpty()) {
            Double.MAX_VALUE
        } else {
            accepted.map { rmsDeviation(it, meanLoop) }.average()
        }
        if (accepted.isNotEmpty() && consensusRms > config.maxConsensusRmsMeters) {
            notReadyReasons += NotReadyReason.InconsistentLoops
            diagnostics += ExtractionDiagnostic(
                loopIndex = -1,
                kind = DiagnosticKind.Noisy,
                rmsDeviationMeters = consensusRms,
                detail = "Accepted loops disagree by ${consensusRms.toInt()}m RMS (GPS noise/drift); reference not reliable.",
            )
        }

        val isReady = notReadyReasons.isEmpty()
        val referenceLine = if (isReady) {
            TrackReferenceLine(
                points = meanLoop.map {
                    val geo = projection.toGeo(it)
                    GeoPointDto(latitude = geo.latitude, longitude = geo.longitude)
                },
                isClosed = true,
            )
        } else {
            null
        }

        return ReferenceLineExtraction(
            markingSession = marking,
            referenceLine = referenceLine,
            isReady = isReady,
            detectedLoopCount = loops.size,
            acceptedLoopCount = accepted.size,
            rejectedLoopCount = rejected,
            diagnostics = diagnostics,
            quality = quality,
            notReadyReasons = notReadyReasons,
        )
    }

    // --- Loop segmentation by angular winding ---------------------------------

    /**
     * Split the continuous path into complete loops by accumulating the turning
     * angle around the path centroid. Each crossing of a full 2π revolution is a
     * loop boundary. This is robust to sparse sampling and to short off-track
     * excursions (which contribute little net winding), unlike start-proximity
     * detection which is fragile at low sample rates.
     */
    private fun segmentLoops(local: List<LocalPoint>): List<Loop> {
        if (local.size < 3) return emptyList()
        val cx = local.sumOf { it.x } / local.size
        val cy = local.sumOf { it.y } / local.size

        val cumulative = DoubleArray(local.size)
        var prevAngle = atan2(local[0].y - cy, local[0].x - cx)
        for (i in 1 until local.size) {
            val angle = atan2(local[i].y - cy, local[i].x - cx)
            var delta = angle - prevAngle
            while (delta > PI) delta -= 2.0 * PI
            while (delta < -PI) delta += 2.0 * PI
            cumulative[i] = cumulative[i - 1] + delta
            prevAngle = angle
        }

        val total = cumulative.last()
        val direction = if (total >= 0.0) 1.0 else -1.0
        val completedLoops = floor((total * direction) / (2.0 * PI)).toInt()
        if (completedLoops < 1) return emptyList()

        val buckets = MutableList(completedLoops) { mutableListOf<LocalPoint>() }
        for (i in local.indices) {
            val revolution = floor((cumulative[i] * direction) / (2.0 * PI)).toInt()
            if (revolution in 0 until completedLoops) {
                buckets[revolution] += local[i]
            }
        }
        return buckets
            .mapIndexed { index, pts -> Loop(index, pts) }
            .filter { it.local.size >= 3 }
    }

    // --- Geometry helpers -----------------------------------------------------

    /**
     * Resample a loop to [count] points by equal arc-length steps around the
     * *closed* loop, starting at the loop's point nearest [anchor]. Anchoring the
     * start phase-aligns every loop so corresponding indices map to the same
     * geometric place on the track, independent of where the raw samples fell.
     */
    private fun resampleAnchored(points: List<LocalPoint>, count: Int, anchor: LocalPoint): List<LocalPoint> {
        if (points.size <= 1) return List(count) { points.firstOrNull() ?: LocalPoint(0.0, 0.0) }
        val n = points.size
        // Closed cumulative arc length: cum[i] = length up to vertex i, cum[n] = perimeter.
        val cum = DoubleArray(n + 1)
        for (i in 1..n) {
            cum[i] = cum[i - 1] + distance(points[i - 1], points[i % n])
        }
        val perimeter = cum[n]
        if (perimeter <= 0.0) return List(count) { points.first() }

        // Continuous arc-length position of the closed-loop point nearest the anchor.
        var bestDist = Double.MAX_VALUE
        var startS = 0.0
        for (j in 0 until n) {
            val a = points[j]
            val b = points[(j + 1) % n]
            val t = projectionParameter(anchor, a, b)
            val px = a.x + (b.x - a.x) * t
            val py = a.y + (b.y - a.y) * t
            val d = distance(anchor, LocalPoint(px, py))
            if (d < bestDist) {
                bestDist = d
                startS = cum[j] + t * (cum[j + 1] - cum[j])
            }
        }

        return List(count) { k ->
            val target = (startS + perimeter * (k.toDouble() / count)) % perimeter
            pointAtArcLength(points, cum, target)
        }
    }

    /** Clamped parameter t in [0,1] of the projection of [p] onto segment a->b. */
    private fun projectionParameter(p: LocalPoint, a: LocalPoint, b: LocalPoint): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lenSq = dx * dx + dy * dy
        if (lenSq <= 0.0) return 0.0
        val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq
        return t.coerceIn(0.0, 1.0)
    }

    /** The closed-loop point at cumulative arc length [s] (s in [0, perimeter)). */
    private fun pointAtArcLength(points: List<LocalPoint>, cum: DoubleArray, s: Double): LocalPoint {
        val n = points.size
        var seg = 0
        while (seg < n - 1 && cum[seg + 1] <= s) seg++
        val segLen = cum[seg + 1] - cum[seg]
        val t = if (segLen <= 0.0) 0.0 else (s - cum[seg]) / segLen
        val a = points[seg]
        val b = points[(seg + 1) % n]
        return LocalPoint(
            x = a.x + (b.x - a.x) * t,
            y = a.y + (b.y - a.y) * t,
        )
    }

    /** Per-index median across loops — outlier-robust consensus shape. */
    private fun medianLoop(loops: List<List<LocalPoint>>, count: Int): List<LocalPoint> {
        return List(count) { i ->
            val xs = loops.map { it[i].x }
            val ys = loops.map { it[i].y }
            LocalPoint(medianOf(xs), medianOf(ys))
        }
    }

    /** Per-index mean across loops — the averaged reference geometry. */
    private fun meanLoop(loops: List<List<LocalPoint>>, count: Int): List<LocalPoint> {
        if (loops.isEmpty()) return emptyList()
        return List(count) { i ->
            val n = loops.size
            LocalPoint(
                x = loops.sumOf { it[i].x } / n,
                y = loops.sumOf { it[i].y } / n,
            )
        }
    }

    /** RMS point-to-point distance between two equal-length loops. */
    private fun rmsDeviation(a: List<LocalPoint>, b: List<LocalPoint>): Double {
        if (a.isEmpty() || a.size != b.size) return Double.MAX_VALUE
        var sumSq = 0.0
        for (i in a.indices) {
            val d = distance(a[i], b[i])
            sumSq += d * d
        }
        return sqrt(sumSq / a.size)
    }

    private fun distance(a: LocalPoint, b: LocalPoint): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun medianOf(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        }
    }

    private fun notReady(
        marking: TrackMarkingSession,
        quality: GpsQualitySummary,
        reason: NotReadyReason,
        detected: Int = 0,
    ): ReferenceLineExtraction = ReferenceLineExtraction(
        markingSession = marking,
        referenceLine = null,
        isReady = false,
        detectedLoopCount = detected,
        acceptedLoopCount = 0,
        rejectedLoopCount = 0,
        diagnostics = emptyList(),
        quality = quality,
        notReadyReasons = listOf(reason),
    )
}
