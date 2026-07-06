package com.huanfuli.lapsight.shared.track

import com.huanfuli.lapsight.shared.fixtures.GpsFixtureLibrary
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StartFinishRecommenderTest {

    @Test
    fun circuitRecommendationPrefersStraightOverCorners() {
        val line = GpsFixtureLibrary.courseMatchReferenceLine()
        val path = (ClosedReferencePath.fromReferenceLine(line) as ClosedReferencePathResult.Loaded).path

        val recommendation = assertNotNull(StartFinishRecommender.recommendCircuit(line))
        val progress = recommendation.normalizedProgress * path.perimeter
        val cornerProgresses = listOf(0.0, 120.0, 200.0, 320.0)

        assertTrue(
            cornerProgresses.all { corner -> cyclicDistance(progress, corner, path.perimeter) > 15.0 },
            "recommended start/finish should not sit on a corner",
        )
        assertTrue(
            CourseGeometryBuilder.pathCrossingCount(path, progress) == 1,
            "recommended line should cross the circuit once",
        )
    }

    @Test
    fun pointToPointRecommendationPlacesStartAndFinishNearEnds() {
        val extraction = ReferenceLineExtractor.extract(
            TrackMarkingSession(
                id = "open",
                createdAtEpochMillis = 0L,
                source = com.huanfuli.lapsight.shared.session.SourceMetadata(
                    source = com.huanfuli.lapsight.shared.LocationSource.Simulated,
                    isSimulated = true,
                    label = "fixture",
                ),
                samples = GpsFixtureLibrary.pointToPointRun().map {
                    com.huanfuli.lapsight.shared.session.LocationSampleDto(
                        elapsedMillis = it.elapsedMillis,
                        latitude = it.latitude,
                        longitude = it.longitude,
                        horizontalAccuracyMeters = it.horizontalAccuracyMeters,
                        speedMetersPerSecond = it.speedMetersPerSecond,
                        headingDegrees = it.headingDegrees,
                        altitudeMeters = it.altitudeMeters,
                        source = it.source,
                    )
                },
            ),
            topology = CourseTopology.PointToPoint,
        )
        val reference = assertNotNull(extraction.referenceLine)

        val recommendation = assertNotNull(StartFinishRecommender.recommendPointToPoint(reference))

        assertTrue(recommendation.start.normalizedProgress < 0.25)
        assertTrue(recommendation.finish.normalizedProgress > 0.75)
    }

    private fun cyclicDistance(a: Double, b: Double, length: Double): Double {
        val raw = abs(a - b)
        return minOf(raw, length - raw)
    }
}
