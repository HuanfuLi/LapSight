package com.huanfuli.lapsight.shared.track

import com.huanfuli.lapsight.shared.lap.GeoPoint
import com.huanfuli.lapsight.shared.lap.LocalPoint
import com.huanfuli.lapsight.shared.lap.LocalProjection
import com.huanfuli.lapsight.shared.session.GeoPointDto
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class RecommendedTimingLine(
    val line: StartFinishLineDto,
    val normalizedProgress: Double,
    val straightnessScore: Double,
    val usedFallback: Boolean,
)

data class PointToPointTimingLines(
    val start: RecommendedTimingLine,
    val finish: RecommendedTimingLine,
)

object StartFinishRecommender {
    private const val CANDIDATE_SPACING_METERS = 5.0
    private const val MIN_CANDIDATES = 32
    private const val MAX_CANDIDATES = 192
    private const val MIN_STRAIGHT_SCORE = 0.78
    private const val OPEN_END_SEARCH_FRACTION = 0.18
    private const val OPEN_END_SEARCH_MAX_METERS = 90.0

    fun recommendCircuit(referenceLine: TrackReferenceLine): RecommendedTimingLine? {
        if (!referenceLine.isClosed) return null
        val path = when (val loaded = ClosedReferencePath.fromReferenceLine(referenceLine)) {
            is ClosedReferencePathResult.Loaded -> loaded.path
            is ClosedReferencePathResult.Rejected -> return null
        }
        val count = (path.perimeter / CANDIDATE_SPACING_METERS)
            .toInt()
            .coerceIn(MIN_CANDIDATES, MAX_CANDIDATES)
        val window = (path.perimeter * 0.06).coerceIn(25.0, 100.0)
        val best = (0 until count)
            .map { i ->
                val progress = path.perimeter * i.toDouble() / count.toDouble()
                val score = straightnessScore(
                    samples = sampleClosedWindow(path, progress, window),
                ) - crossingPenalty(path, progress)
                progress to score
            }
            .maxByOrNull { it.second }
            ?: return null
        return RecommendedTimingLine(
            line = CourseGeometryBuilder.buildStartFinishLine(path, best.first),
            normalizedProgress = path.wrap(best.first) / path.perimeter,
            straightnessScore = best.second,
            usedFallback = best.second < MIN_STRAIGHT_SCORE,
        )
    }

    fun recommendPointToPoint(referenceLine: TrackReferenceLine): PointToPointTimingLines? {
        if (referenceLine.isClosed) return null
        val path = OpenReferencePath.from(referenceLine) ?: return null
        val search = min(path.length * OPEN_END_SEARCH_FRACTION, OPEN_END_SEARCH_MAX_METERS)
            .coerceAtLeast(0.0)
        val start = bestOpenLine(path, 0.0, search)
        val finish = bestOpenLine(path, (path.length - search).coerceAtLeast(0.0), path.length)
        return PointToPointTimingLines(start = start, finish = finish)
    }

    private fun bestOpenLine(
        path: OpenReferencePath,
        startProgress: Double,
        endProgress: Double,
    ): RecommendedTimingLine {
        val span = (endProgress - startProgress).coerceAtLeast(0.0)
        val count = (span / CANDIDATE_SPACING_METERS).toInt().coerceIn(1, MAX_CANDIDATES)
        val window = (path.length * 0.08).coerceIn(20.0, 80.0)
        val best = (0..count)
            .map { i ->
                val progress = if (count == 0) {
                    startProgress
                } else {
                    startProgress + span * i.toDouble() / count.toDouble()
                }.coerceIn(0.0, path.length)
                progress to straightnessScore(sampleOpenWindow(path, progress, window))
            }
            .maxByOrNull { it.second }
            ?: (startProgress to 0.0)
        return RecommendedTimingLine(
            line = buildLine(path, best.first),
            normalizedProgress = if (path.length > 0.0) best.first / path.length else 0.0,
            straightnessScore = best.second,
            usedFallback = best.second < MIN_STRAIGHT_SCORE,
        )
    }

    private fun crossingPenalty(path: ClosedReferencePath, progress: Double): Double =
        if (CourseGeometryBuilder.pathCrossingCount(path, progress) == 1) 0.0 else 2.0

    private fun sampleClosedWindow(
        path: ClosedReferencePath,
        center: Double,
        window: Double,
    ): List<LocalPoint> = List(9) { i ->
        val offset = -window + (2.0 * window * i.toDouble() / 8.0)
        path.pointAt(center + offset)
    }

    private fun sampleOpenWindow(
        path: OpenReferencePath,
        center: Double,
        window: Double,
    ): List<LocalPoint> = List(9) { i ->
        val offset = -window + (2.0 * window * i.toDouble() / 8.0)
        path.pointAt((center + offset).coerceIn(0.0, path.length))
    }.distinctBy { "${it.x}:${it.y}" }

    private fun straightnessScore(samples: List<LocalPoint>): Double {
        if (samples.size < 3) return 0.0
        val arc = pathDistance(samples)
        if (arc <= 0.0) return 0.0
        val chordRatio = (distance(samples.first(), samples.last()) / arc).coerceIn(0.0, 1.0)
        val headingPenalty = headingVariation(samples).coerceIn(0.0, PI) / PI
        return chordRatio - headingPenalty
    }

    private fun headingVariation(samples: List<LocalPoint>): Double {
        val headings = samples.zipWithNext()
            .mapNotNull { (a, b) ->
                val dx = b.x - a.x
                val dy = b.y - a.y
                if (hypot(dx, dy) <= 1e-6) null else atan2(dy, dx)
            }
        if (headings.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until headings.size) {
            total += kotlin.math.abs(angleDelta(headings[i - 1], headings[i]))
        }
        return total / (headings.size - 1).toDouble()
    }

    private fun angleDelta(a: Double, b: Double): Double {
        var d = b - a
        while (d > PI) d -= 2.0 * PI
        while (d < -PI) d += 2.0 * PI
        return d
    }

    private fun buildLine(path: OpenReferencePath, progress: Double): StartFinishLineDto {
        val center = path.pointAt(progress)
        val tangent = path.tangentAt(progress)
        val normal = LocalPoint(-tangent.y, tangent.x)
        val half = CourseGeometryThresholds.Default.boundaryLengthMeters / 2.0
        return StartFinishLineDto(
            pointA = path.toGeo(LocalPoint(center.x - normal.x * half, center.y - normal.y * half)),
            pointB = path.toGeo(LocalPoint(center.x + normal.x * half, center.y + normal.y * half)),
        )
    }

    private fun pathDistance(points: List<LocalPoint>): Double {
        var total = 0.0
        for (i in 1 until points.size) total += distance(points[i - 1], points[i])
        return total
    }

    private fun distance(a: LocalPoint, b: LocalPoint): Double = hypot(a.x - b.x, a.y - b.y)

    private class OpenReferencePath private constructor(
        private val projection: LocalProjection,
        private val points: List<LocalPoint>,
        private val cumulative: DoubleArray,
    ) {
        val length: Double get() = cumulative.last()

        fun pointAt(progress: Double): LocalPoint {
            val s = progress.coerceIn(0.0, length)
            var seg = 0
            while (seg < points.lastIndex - 1 && cumulative[seg + 1] < s) seg++
            val segLen = cumulative[seg + 1] - cumulative[seg]
            val t = if (segLen <= 0.0) 0.0 else (s - cumulative[seg]) / segLen
            val a = points[seg]
            val b = points[seg + 1]
            return LocalPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        }

        fun tangentAt(progress: Double): LocalPoint {
            val w = (length * 0.02).coerceIn(5.0, 20.0)
            val a = pointAt((progress - w).coerceIn(0.0, length))
            val b = pointAt((progress + w).coerceIn(0.0, length))
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len = hypot(dx, dy)
            return if (len <= 1e-6) LocalPoint(1.0, 0.0) else LocalPoint(dx / len, dy / len)
        }

        fun toGeo(local: LocalPoint): GeoPointDto {
            val geo = projection.toGeo(local)
            return GeoPointDto(latitude = geo.latitude, longitude = geo.longitude)
        }

        companion object {
            fun from(line: TrackReferenceLine): OpenReferencePath? {
                if (line.points.size < 2) return null
                val origin = line.points.first()
                val projection = LocalProjection(GeoPoint(origin.latitude, origin.longitude))
                val local = line.points.map { projection.toLocal(GeoPoint(it.latitude, it.longitude)) }
                    .dedupe()
                if (local.size < 2) return null
                val cumulative = DoubleArray(local.size)
                for (i in 1 until local.size) {
                    cumulative[i] = cumulative[i - 1] + distance(local[i - 1], local[i])
                }
                if (cumulative.last() <= 0.0) return null
                return OpenReferencePath(projection, local, cumulative)
            }

            private fun List<LocalPoint>.dedupe(): List<LocalPoint> {
                val out = ArrayList<LocalPoint>(size)
                for (point in this) {
                    val prev = out.lastOrNull()
                    if (prev == null || distance(prev, point) > 1e-6) out.add(point)
                }
                return out
            }
        }
    }
}
