package com.huanfuli.lapsight.shared.session

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.LocationSource
import kotlinx.serialization.Serializable

/**
 * Base canonical DTOs shared by saved tracks and timing sessions (D-17, D-24, D-25).
 *
 * These are the serializable mirrors of the in-memory domain types. They live in
 * the session package because tracks and timing sessions both persist the same
 * raw sample/metadata shapes, and internal saved JSON and exported JSON share the
 * same versioned schema where practical (D-24).
 */

/** Serializable geographic point in WGS84-style decimal degrees. */
@Serializable
data class GeoPointDto(
    val latitude: Double,
    val longitude: Double,
)

/**
 * Serializable mirror of [LocationSample]. Kept separate from the Phase 2 domain
 * model so the lap engine's input type stays free of serialization concerns while
 * saved payloads remain canonical and versioned.
 */
@Serializable
data class LocationSampleDto(
    val elapsedMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val horizontalAccuracyMeters: Double? = null,
    val speedMetersPerSecond: Double? = null,
    val headingDegrees: Double? = null,
    val altitudeMeters: Double? = null,
    val source: LocationSource,
)

/** Source/provenance metadata so demo/simulated data stays visibly labeled (D-25, D-42). */
@Serializable
data class SourceMetadata(
    val source: LocationSource,
    val isSimulated: Boolean,
    val label: String? = null,
)

/** App/build metadata captured at save time where available (D-25). */
@Serializable
data class AppMetadata(
    val appVersion: String,
    val buildNumber: String? = null,
    val platform: String? = null,
)

/** Lightweight GPS-quality rollup used in index summaries and review (D-22). */
@Serializable
data class GpsQualitySummary(
    val sampleCount: Int,
    val averageAccuracyMeters: Double? = null,
    val source: LocationSource,
)

/** Maps a domain [LocationSample] to its serializable DTO. */
fun LocationSample.toDto(): LocationSampleDto = LocationSampleDto(
    elapsedMillis = elapsedMillis,
    latitude = latitude,
    longitude = longitude,
    horizontalAccuracyMeters = horizontalAccuracyMeters,
    speedMetersPerSecond = speedMetersPerSecond,
    headingDegrees = headingDegrees,
    altitudeMeters = altitudeMeters,
    source = source,
)

/** Maps a [LocationSampleDto] back to the domain [LocationSample]. */
fun LocationSampleDto.toModel(): LocationSample = LocationSample(
    elapsedMillis = elapsedMillis,
    latitude = latitude,
    longitude = longitude,
    horizontalAccuracyMeters = horizontalAccuracyMeters,
    speedMetersPerSecond = speedMetersPerSecond,
    headingDegrees = headingDegrees,
    altitudeMeters = altitudeMeters,
    source = source,
)

/** Derives a [GpsQualitySummary] from a captured sample list. */
fun gpsQualitySummaryOf(samples: List<LocationSampleDto>, source: LocationSource): GpsQualitySummary {
    val accuracies = samples.mapNotNull { it.horizontalAccuracyMeters }
    val average = if (accuracies.isEmpty()) null else accuracies.sum() / accuracies.size
    return GpsQualitySummary(
        sampleCount = samples.size,
        averageAccuracyMeters = average,
        source = source,
    )
}
