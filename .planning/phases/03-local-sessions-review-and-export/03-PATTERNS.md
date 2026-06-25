# Phase 3: Local Sessions, Review, and Export - Pattern Map

**Mapped:** 2026-06-25
**Files analyzed:** 43 new/modified files inferred from CONTEXT.md, RESEARCH.md, UI-SPEC.md, and VALIDATION.md
**Analogs found:** 34 / 43

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `gradle/libs.versions.toml` | config | dependency resolution | `gradle/libs.versions.toml` | exact |
| `shared/build.gradle.kts` | config | dependency resolution | `shared/build.gradle.kts` | exact |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/LocationSampleProvider.kt` | service | streaming | `GpsProbeModels.kt`, `DemoLapSession.kt` | role-match |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/SimulatedGpsProvider.kt` | service | streaming | `GpsProbeSimulator`, `DemoLapSession.kt`, `ReplayFixtures.kt` | role-match |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/GpsQualitySummary.kt` | utility/model | transform | `GpsProbeState`, `LapDashState.kt` | role-match |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/fixtures/GpsFixtureLibrary.kt` | utility | batch/transform | `ReplayFixtures.kt` | exact |
| `shared/src/commonMain/composeResources/files/fixtures/clean-10-loop.json` | fixture | file-I/O | none | no-analog |
| `shared/src/commonMain/composeResources/files/fixtures/minimum-5-loop.json` | fixture | file-I/O | none | no-analog |
| `shared/src/commonMain/composeResources/files/fixtures/one-outlier-loop.json` | fixture | file-I/O | none | no-analog |
| `shared/src/commonMain/composeResources/files/fixtures/noise-drift.json` | fixture | file-I/O | none | no-analog |
| `shared/src/commonMain/composeResources/files/fixtures/dropped-low-frequency.json` | fixture | file-I/O | none | no-analog |
| `shared/src/commonMain/composeResources/files/fixtures/multi-session-best-candidate.json` | fixture | file-I/O | none | no-analog |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/track/TrackModels.kt` | model | CRUD | `TimingLines.kt`, `LapModels.kt` | role-match |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/track/ReferenceLineExtractor.kt` | service | batch/transform | `LocalProjection.kt`, `SegmentGeometry.kt`, `CrossingDetector.kt` | partial |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/track/TrackReviewState.kt` | model | transform | `LapDashState.kt` | role-match |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionModels.kt` | model | CRUD | `LapModels.kt`, `TimingLines.kt` | role-match |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionController.kt` | service | event-driven | `DemoLapSession.kt`, `LapEngine.kt` | role-match |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/TimingSessionRecorder.kt` | service | streaming | `LapEngine.kt`, `ReplayRunner.kt` | exact |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/LocalSessionStore.kt` | service/interface | CRUD | `OrientationController.kt` | partial |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/FileSessionStore.kt` | service | file-I/O | none | no-analog |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/StoragePaths.kt` | config/platform boundary | file-I/O | `OrientationController.kt` | partial |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/SchemaVersions.kt` | config | transform | `LapEngineConfig.kt` | role-match |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/export/JsonExportService.kt` | service | transform/file-I/O | none | no-analog |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/export/GpxExportService.kt` | service | transform/file-I/O | none | no-analog |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/export/ExportFileNames.kt` | utility | transform | `LapDashState.kt`, `GpsProbeModels.kt` | role-match |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/review/ReviewModels.kt` | model | CRUD/transform | `LapDashState.kt`, `LapModels.kt` | role-match |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/review/TraceProjection.kt` | utility | transform | `LocalProjection.kt`, `GeometryTest.kt` | exact |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/AppShell.kt` | component | event-driven | `App.kt` | exact |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/DriveScreen.kt` | component | event-driven/streaming | `App.kt` | exact |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/ReviewScreen.kt` | component | request-response/CRUD | `App.kt` | role-match |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/TraceView.kt` | component | transform | `App.kt`, `LocalProjection.kt` | partial |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/App.kt` | component | event-driven | `App.kt` | exact |
| `shared/src/androidMain/kotlin/com/huanfuli/lapsight/shared/storage/StoragePaths.android.kt` | config/platform boundary | file-I/O | `MainActivity.kt` | role-match |
| `shared/src/iosMain/kotlin/com/huanfuli/lapsight/shared/storage/StoragePaths.ios.kt` | config/platform boundary | file-I/O | `MainViewController.kt`, `ContentView.swift` | role-match |
| `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/session/SessionControllerTest.kt` | test | event-driven | `LapEngineTest.kt`, `LapDashStateTest.kt` | role-match |
| `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/session/DraftRecoveryTest.kt` | test | file-I/O/event-driven | `LapEngineTest.kt` | role-match |
| `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/storage/FileSessionStoreTest.kt` | test | file-I/O | `LapEngineTest.kt` | role-match |
| `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/track/ReferenceLineExtractorTest.kt` | test | batch/transform | `GeometryTest.kt`, `ReplayTest.kt` | exact |
| `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/export/JsonExportTest.kt` | test | transform/file-I/O | `LapModelsTest.kt` | role-match |
| `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/export/GpxExportTest.kt` | test | transform/file-I/O | `LapModelsTest.kt`, `GeometryTest.kt` | role-match |
| `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/review/ReviewSummaryTest.kt` | test | transform | `LapDashStateTest.kt` | exact |
| `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/review/TraceProjectionTest.kt` | test | transform | `GeometryTest.kt` | exact |
| `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/SimulatedGpsProviderTest.kt` | test | streaming | `ReplayTest.kt`, `LapDashStateTest.kt` | role-match |

## Pattern Assignments

### Build Configuration: `gradle/libs.versions.toml`, `shared/build.gradle.kts`

**Analogs:** `gradle/libs.versions.toml`, `shared/build.gradle.kts`

**Version catalog pattern** (`gradle/libs.versions.toml` lines 1-16, 18-36, 38-43):
```toml
[versions]
agp = "9.0.1"
android-compileSdk = "36"
android-minSdk = "24"
android-targetSdk = "36"
composeMultiplatform = "1.11.1"
kotlin = "2.4.0"
kotlinx-coroutines = "1.11.0"
material3 = "1.11.0-alpha07"

[libraries]
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
compose-material3 = { module = "org.jetbrains.compose.material3:material3", version.ref = "material3" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }

[plugins]
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
```

**Shared source set dependency pattern** (`shared/build.gradle.kts` lines 37-54):
```kotlin
sourceSets {
    androidMain.dependencies {
        implementation(libs.compose.uiToolingPreview)
    }
    commonMain.dependencies {
        implementation(libs.compose.runtime)
        implementation(libs.compose.foundation)
        implementation(libs.compose.material3)
        implementation(libs.compose.ui)
        implementation(libs.compose.components.resources)
        implementation(libs.compose.uiToolingPreview)
        implementation(libs.androidx.lifecycle.viewmodelCompose)
        implementation(libs.androidx.lifecycle.runtimeCompose)
        implementation(libs.kotlinx.coroutines.core)
    }
    commonTest.dependencies {
        implementation(libs.kotlin.test)
    }
}
```

**Apply to:** add serialization plugin/runtime, Okio, and Material Icons through the same version-catalog pattern. Keep `kotlin.test` in `commonTest`.

---

### Provider and Fixtures: `LocationSampleProvider.kt`, `SimulatedGpsProvider.kt`, `GpsQualitySummary.kt`, `fixtures/GpsFixtureLibrary.kt`

**Analogs:** `GpsProbeModels.kt`, `ReplayFixtures.kt`, `ReplayRunner.kt`, `DemoLapSession.kt`

**Imports and canonical sample shape** (`GpsProbeModels.kt` lines 1-7, 8-32):
```kotlin
package com.huanfuli.lapsight.shared

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.roundToInt

enum class LocationSource {
    Simulated,
    PhoneGps,
    ExternalGnss,
}

data class LocationSample(
    val elapsedMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val horizontalAccuracyMeters: Double?,
    val speedMetersPerSecond: Double?,
    val headingDegrees: Double?,
    val altitudeMeters: Double?,
    val source: LocationSource,
)
```

**Provider-state label pattern** (`GpsProbeModels.kt` lines 34-58):
```kotlin
data class GpsProbeState(
    val isRunning: Boolean,
    val fixStatus: GpsFixStatus,
    val latestSample: LocationSample?,
    val sampleCount: Int,
    val elapsedMillis: Long,
    val updateRateHz: Double,
) {
    val speedKmhLabel: String
        get() = latestSample?.speedMetersPerSecond
            ?.let { (it * 3.6).roundToInt().toString() }
            ?: "--"

    val accuracyLabel: String
        get() = latestSample?.horizontalAccuracyMeters
            ?.let { max(0.0, it).roundToInt().toString() }
            ?: "--"

    fun stopped(): GpsProbeState = copy(isRunning = false)
}
```

**Deterministic simulated sample pattern** (`GpsProbeModels.kt` lines 77-101):
```kotlin
object GpsProbeSimulator {
    private const val BASE_LATITUDE = 39.8121
    private const val BASE_LONGITUDE = -86.1062

    fun next(previous: GpsProbeState, tick: Int): GpsProbeState {
        val elapsedMillis = tick.coerceAtLeast(0) * 1_000L
        val angle = tick * PI / 18.0
        val sample = LocationSample(
            elapsedMillis = elapsedMillis,
            latitude = BASE_LATITUDE + sin(angle) * 0.00045,
            longitude = BASE_LONGITUDE + kotlin.math.cos(angle) * 0.00045,
            horizontalAccuracyMeters = 5.0 + (tick % 5) * 1.7,
            speedMetersPerSecond = 8.0 + (tick % 8) * 0.9,
            headingDegrees = (tick * 12 % 360).toDouble(),
            altitudeMeters = 219.0,
            source = LocationSource.Simulated,
        )
        return previous.copy(
            isRunning = true,
            fixStatus = GpsFixStatus.Simulated,
            latestSample = sample,
            sampleCount = tick.coerceAtLeast(previous.sampleCount + 1),
            elapsedMillis = elapsedMillis,
            updateRateHz = 1.0,
        )
    }
}
```

**Fixture builder pattern** (`ReplayFixtures.kt` lines 21-65, 116-124):
```kotlin
object ReplayFixtures {

    private const val M_PER_DEG = LocalProjection.METERS_PER_DEGREE

    private fun lon(eastMeters: Double): Double = eastMeters / M_PER_DEG
    private fun lat(northMeters: Double): Double = northMeters / M_PER_DEG

    private fun sample(
        elapsedMillis: Long,
        eastMeters: Double,
        northMeters: Double,
        accuracy: Double = 6.0,
        speed: Double = 15.0,
        heading: Double = 90.0,
    ): LocationSample = LocationSample(
        elapsedMillis = elapsedMillis,
        latitude = lat(northMeters),
        longitude = lon(eastMeters),
        horizontalAccuracyMeters = accuracy,
        speedMetersPerSecond = speed,
        headingDegrees = heading,
        altitudeMeters = 210.0,
        source = LocationSource.Simulated,
    )

    fun multiLapLoop(lapDurations: List<Long> = listOf(40_000, 32_000, 36_000)): List<LocationSample> {
        val samples = mutableListOf<LocationSample>()
        samples += openingCrossing()
        var lapStart = 1_000L
        for (dur in lapDurations) {
            samples += lapBody(startMillis = lapStart, lapMillis = dur)
            lapStart += dur
        }
        return samples
    }
}
```

**Streaming lifecycle pattern to evolve away from session-owned replay** (`DemoLapSession.kt` lines 18-31, 40-49, 65-82):
```kotlin
class DemoLapSession(
    private val courseName: String = "Demo Course",
    private val course: CourseDefinition = ReplayFixtures.DEMO_COURSE,
    private val config: LapEngineConfig = ReplayFixtures.DEMO_CONFIG,
    private val samples: List<LocationSample> = ReplayFixtures.multiLapLoop(
        listOf(40_000, 32_000, 36_000),
    ),
) {
    private val engine = LapEngine(course, config)
    private var index = 0
    private var latestSample: LocationSample? = null
    private var running = false

    fun start() {
        running = true
        dashState = dashState.copy(isRunning = true, fixStatus = GpsFixStatus.Simulated)
    }

    fun stop() {
        running = false
        dashState = dashState.copy(isRunning = false)
    }

    fun tick(): LapDashState {
        if (!running || isFinished) return dashState
        val sample = samples[index]
        index += 1
        latestSample = sample
        val timing = engine.onSample(sample)
        dashState = LapDashState.from(
            isRunning = running && !isFinished,
            fixStatus = GpsFixStatus.Simulated,
            courseName = courseName,
            timing = timing,
            latestSample = sample,
        )
        return dashState
    }
}
```

**Apply to:** `SimulatedGpsProvider` should emit the existing `LocationSample` shape with `source = LocationSource.Simulated`. `LocationSampleProvider` should be the normal provider boundary; track marking and timing must consume it, not a separate demo workflow. `GpsQualitySummary` should compute labels/rollups from samples without platform dependencies.

---

### Track Domain and Reference Extraction: `TrackModels.kt`, `ReferenceLineExtractor.kt`, `TrackReviewState.kt`

**Analogs:** `TimingLines.kt`, `LapModels.kt`, `LocalProjection.kt`, `SegmentGeometry.kt`, `CrossingDetector.kt`, `LapEngineConfig.kt`

**Track line model pattern** (`TimingLines.kt` lines 10-42):
```kotlin
data class StartFinishLine(
    val pointA: GeoPoint,
    val pointB: GeoPoint,
)

data class SectorLine(
    val id: String,
    val name: String,
    val order: Int,
    val pointA: GeoPoint,
    val pointB: GeoPoint,
)

data class CourseDefinition(
    val startFinish: StartFinishLine,
    val sectors: List<SectorLine> = emptyList(),
) {
    val orderedSectors: List<SectorLine>
        get() = sectors.sortedBy { it.order }
}
```

**Observable result model pattern** (`LapModels.kt` lines 37-60, 100-123):
```kotlin
data class LapEvent(
    val lapNumber: Int,
    val startMillis: Long,
    val endMillis: Long,
) {
    val durationMillis: Long get() = endMillis - startMillis
}

data class SectorEvent(
    val lapNumber: Int,
    val sectorId: String,
    val sectorOrder: Int,
    val crossingMillis: Long,
    val splitMillis: Long,
)

data class LapTimingState(
    val phase: LapPhase = LapPhase.AwaitingStart,
    val lapCount: Int = 0,
    val currentLapNumber: Int? = null,
    val currentLapStartMillis: Long? = null,
    val currentLapElapsedMillis: Long? = null,
    val lastLapMillis: Long? = null,
    val bestLapMillis: Long? = null,
    val sectors: List<SectorTimingState> = emptyList(),
    val latestSector: SectorEvent? = null,
    val completedLaps: List<LapEvent> = emptyList(),
    val lastRejectReason: LapRejectReason? = null,
)
```

**Local projection pattern** (`LocalProjection.kt` lines 22-49):
```kotlin
class LocalProjection(val origin: GeoPoint) {

    private val metersPerDegLat = METERS_PER_DEGREE
    private val metersPerDegLon = METERS_PER_DEGREE * cos(origin.latitude.toRadians())

    fun toLocal(point: GeoPoint): LocalPoint = LocalPoint(
        x = (point.longitude - origin.longitude) * metersPerDegLon,
        y = (point.latitude - origin.latitude) * metersPerDegLat,
    )

    fun toGeo(point: LocalPoint): GeoPoint = GeoPoint(
        latitude = origin.latitude + point.y / metersPerDegLat,
        longitude = if (abs(metersPerDegLon) < MIN_METERS_PER_DEGREE_LON) {
            origin.longitude
        } else {
            origin.longitude + point.x / metersPerDegLon
        },
    )

    companion object {
        const val METERS_PER_DEGREE: Double = 111_320.0
    }
}
```

**Pure geometry/extractor primitive pattern** (`SegmentGeometry.kt` lines 62-99, 105-108):
```kotlin
fun intersectMovementWithLine(
    moveStart: LocalPoint,
    moveEnd: LocalPoint,
    lineA: LocalPoint,
    lineB: LocalPoint,
): SegmentCrossing? {
    val rx = moveEnd.x - moveStart.x
    val ry = moveEnd.y - moveStart.y
    val sx = lineB.x - lineA.x
    val sy = lineB.y - lineA.y

    val denom = rx * sy - ry * sx
    if (abs(denom) < EPSILON) {
        return null
    }

    val t = (qpx * sy - qpy * sx) / denom
    val u = (qpx * ry - qpy * rx) / denom

    if (t < -EPSILON || t > 1.0 + EPSILON) return null
    if (u < -EPSILON || u > 1.0 + EPSILON) return null

    val ratio = t.coerceIn(0.0, 1.0)
    return SegmentCrossing(
        crossingPoint = crossing,
        ratio = ratio,
        signedSideBefore = cross(lineA, lineB, moveStart),
    )
}

fun interpolateTimestamp(startMillis: Long, endMillis: Long, ratio: Double): Long? {
    if (ratio < 0.0 || ratio > 1.0) return null
    return startMillis + ((endMillis - startMillis).toDouble() * ratio).toLong()
}
```

**Fail-fast config pattern** (`LapEngineConfig.kt` lines 24-38, 40-53):
```kotlin
data class LapEngineConfig(
    val minLapDurationMillis: Long = DEFAULT_MIN_LAP_DURATION_MILLIS,
    val crossingCooldownMillis: Long = DEFAULT_CROSSING_COOLDOWN_MILLIS,
    val maxHorizontalAccuracyMeters: Double? = DEFAULT_MAX_HORIZONTAL_ACCURACY_METERS,
    val minSpeedMetersPerSecond: Double = DEFAULT_MIN_SPEED_METERS_PER_SECOND,
    val enforceDirection: Boolean = true,
) {
    init {
        require(minLapDurationMillis >= 0) { "minLapDurationMillis must be >= 0" }
        require(crossingCooldownMillis >= 0) { "crossingCooldownMillis must be >= 0" }
        require(minSpeedMetersPerSecond >= 0) { "minSpeedMetersPerSecond must be >= 0" }
        maxHorizontalAccuracyMeters?.let {
            require(it > 0) { "maxHorizontalAccuracyMeters must be > 0 when set" }
        }
    }

    companion object {
        const val DEFAULT_MIN_LAP_DURATION_MILLIS: Long = 8_000
        fun lenientForTests(): LapEngineConfig = LapEngineConfig(
            minLapDurationMillis = 0,
            crossingCooldownMillis = 0,
            maxHorizontalAccuracyMeters = null,
            minSpeedMetersPerSecond = 0.0,
            enforceDirection = false,
        )
    }
}
```

**Apply to:** keep `TrackMarkingSession`, `TrackReferenceLine`, and `Track` as separate data classes. Reference extraction must be a pure shared-domain service over raw `LocationSample`s and must not call `LapEngine` or split marking into laps before start/finish exists.

---

### Session Domain and Timing Recorder: `SessionModels.kt`, `SessionController.kt`, `TimingSessionRecorder.kt`

**Analogs:** `LapEngine.kt`, `ReplayRunner.kt`, `DemoLapSession.kt`

**Engine ownership and sample ingestion pattern** (`LapEngine.kt` lines 28-65, 88-118):
```kotlin
class LapEngine(
    private val course: CourseDefinition,
    private val config: LapEngineConfig = LapEngineConfig(),
) {
    private var projection: LocalProjection? = null
    private var detector: CrossingDetector? = null
    private var previous: LocationSample? = null

    var state: LapTimingState = LapTimingState.initial(course)
        private set

    fun reset() {
        projection = null
        detector = null
        previous = null
        state = LapTimingState.initial(course)
    }

    fun onSample(sample: LocationSample): LapTimingState {
        val proj = projection ?: LocalProjection(GeoPoint(sample.latitude, sample.longitude)).also {
            projection = it
            detector = CrossingDetector(it)
        }

        val crossings = mutableListOf<PendingCrossing>()
        det.detectStartFinish(course.startFinish, movement)?.let {
            crossings += PendingCrossing(it, sector = null)
        }
        for (sector in course.orderedSectors) {
            det.detectSector(sector, movement)?.let {
                crossings += PendingCrossing(it, sector = sector)
            }
        }
        crossings.sortedWith(
            compareBy({ it.candidate.crossingMillis }, { it.candidate.ratio }),
        ).forEach { pending ->
            if (pending.sector == null) {
                handleStartFinish(pending.candidate, movement)
            } else {
                handleSectorCrossing(pending.sector, pending.candidate, movement)
            }
        }

        previous = sample
        state = state.copy(currentLapElapsedMillis = liveElapsed(sample.elapsedMillis))
        return state
    }
}
```

**Completed lap persistence source pattern** (`LapEngine.kt` lines 176-192, 218-242):
```kotlin
val lapNumber = state.currentLapNumber ?: 1
val completed = LapEvent(lapNumber, lapStart, candidate.crossingMillis)
val best = state.bestLapMillis?.let { min(it, completed.durationMillis) }
    ?: completed.durationMillis

state = state.copy(
    lapCount = lapNumber,
    currentLapNumber = lapNumber + 1,
    currentLapStartMillis = candidate.crossingMillis,
    currentLapElapsedMillis = 0,
    lastLapMillis = completed.durationMillis,
    bestLapMillis = best,
    completedLaps = state.completedLaps + completed,
    sectors = resetSectors(),
    lastRejectReason = null,
)

val event = SectorEvent(
    lapNumber = lapNumber,
    sectorId = sector.id,
    sectorOrder = sector.order,
    crossingMillis = candidate.crossingMillis,
    splitMillis = splitMillis,
)
```

**Replay runner pattern for deterministic recorder tests** (`ReplayRunner.kt` lines 49-60):
```kotlin
class ReplayRunner(
    private val course: CourseDefinition,
    private val config: LapEngineConfig = LapEngineConfig(),
) {
    fun run(samples: List<LocationSample>): ReplayResult {
        val engine = LapEngine(course, config)
        val steps = samples.map { sample ->
            ReplayStep(sample = sample, state = engine.onSample(sample))
        }
        return ReplayResult(steps)
    }
}
```

**Apply to:** `TimingSessionRecorder` should own a `LapEngine` only after a saved `Track` supplies a `CourseDefinition`. `SessionController` should own explicit draft states and Save/Discard transitions; UI must not silently promote stopped drafts to saved history.

---

### Storage and Schema: `LocalSessionStore.kt`, `FileSessionStore.kt`, `StoragePaths.kt`, `SchemaVersions.kt`

**Analogs:** `OrientationController.kt`, `LapEngineConfig.kt`; no existing file store analog.

**Interface/platform hook pattern** (`OrientationController.kt` lines 3-24):
```kotlin
enum class DashOrientation { Portrait, Landscape }

interface OrientationController {
    fun apply(orientation: DashOrientation)
}

object NoOpOrientationController : OrientationController {
    override fun apply(orientation: DashOrientation) {}
}
```

**Config constants and validation pattern** (`LapEngineConfig.kt` lines 31-47):
```kotlin
init {
    require(minLapDurationMillis >= 0) { "minLapDurationMillis must be >= 0" }
    require(crossingCooldownMillis >= 0) { "crossingCooldownMillis must be >= 0" }
    require(minSpeedMetersPerSecond >= 0) { "minSpeedMetersPerSecond must be >= 0" }
    maxHorizontalAccuracyMeters?.let {
        require(it > 0) { "maxHorizontalAccuracyMeters must be > 0 when set" }
    }
}

companion object {
    const val DEFAULT_MIN_LAP_DURATION_MILLIS: Long = 8_000
    const val DEFAULT_CROSSING_COOLDOWN_MILLIS: Long = 3_000
    const val DEFAULT_MAX_HORIZONTAL_ACCURACY_METERS: Double = 25.0
    const val DEFAULT_MIN_SPEED_METERS_PER_SECOND: Double = 2.0
}
```

**Apply to:** expose a small storage API in common code, inject app-private roots from platform code, and define schema version constants up front. `FileSessionStore` has no close codebase analog; planner should use RESEARCH.md's Okio + atomic temp-write guidance rather than copying local code.

---

### Export: `JsonExportService.kt`, `GpxExportService.kt`, `ExportFileNames.kt`

**Analogs:** no JSON/GPX exporter exists; closest local formatting analog is `LapDashState.kt`.

**Shared formatter pattern** (`LapDashState.kt` lines 93-107):
```kotlin
fun Long?.formatLapTime(): String {
    if (this == null) return "--:--.---"
    val totalMillis = if (this < 0) 0 else this
    val minutes = totalMillis / 60_000
    val seconds = (totalMillis % 60_000) / 1_000
    val millis = totalMillis % 1_000
    val mm = minutes.toString().padStart(2, '0')
    val ss = seconds.toString().padStart(2, '0')
    val mmm = millis.toString().padStart(3, '0')
    return "$mm:$ss.$mmm"
}
```

**Sample metadata to preserve** (`GpsProbeModels.kt` lines 23-32):
```kotlin
data class LocationSample(
    val elapsedMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val horizontalAccuracyMeters: Double?,
    val speedMetersPerSecond: Double?,
    val headingDegrees: Double?,
    val altitudeMeters: Double?,
    val source: LocationSource,
)
```

**Apply to:** JSON export should reuse canonical DTOs and include schemaVersion, IDs, source/app/build metadata, raw samples, lines, laps, sectors, and quality summary. GPX export has no local analog; implement a small tested writer with XML escaping and explicit sample-count assertions.

---

### Review and Trace Projection: `ReviewModels.kt`, `TraceProjection.kt`, `TraceView.kt`

**Analogs:** `LapDashState.kt`, `LocalProjection.kt`, `GeometryTest.kt`

**Presentation-ready state pattern** (`LapDashState.kt` lines 14-39, 47-79):
```kotlin
data class LapDashState(
    val isRunning: Boolean = false,
    val fixStatus: GpsFixStatus = GpsFixStatus.Idle,
    val courseName: String = "",
    val lapCount: Int = 0,
    val currentLapMillis: Long? = null,
    val lastLapMillis: Long? = null,
    val bestLapMillis: Long? = null,
    val latestSectorLabel: String? = null,
    val latestSectorSplitMillis: Long? = null,
    val sectorSummaries: List<SectorSummary> = emptyList(),
    val speedMetersPerSecond: Double? = null,
    val accuracyMeters: Double? = null,
) {
    val currentLapLabel: String get() = currentLapMillis.formatLapTime()
    val speedKmhLabel: String
        get() = speedMetersPerSecond?.let { (it * 3.6).roundToInt().toString() } ?: "--"

    companion object {
        fun from(
            isRunning: Boolean,
            fixStatus: GpsFixStatus,
            courseName: String,
            timing: LapTimingState,
            latestSample: LocationSample?,
        ): LapDashState {
            return LapDashState(
                isRunning = isRunning,
                fixStatus = fixStatus,
                courseName = courseName,
                lapCount = timing.lapCount,
                currentLapMillis = timing.currentLapElapsedMillis,
                lastLapMillis = timing.lastLapMillis,
                bestLapMillis = timing.bestLapMillis,
                speedMetersPerSecond = latestSample?.speedMetersPerSecond,
                accuracyMeters = latestSample?.horizontalAccuracyMeters,
            )
        }
    }
}
```

**Projection test pattern** (`GeometryTest.kt` lines 14-27, 30-35):
```kotlin
@Test
fun projectionMapsOriginToZero() {
    val local = projection.toLocal(origin)
    assertTrue(abs(local.x) < 1e-6)
    assertTrue(abs(local.y) < 1e-6)
}

@Test
fun projectionIsReversibleWithinTolerance() {
    val point = GeoPoint(origin.latitude + 0.0009, origin.longitude - 0.0007)
    val roundTrip = projection.toGeo(projection.toLocal(point))
    assertTrue(abs(roundTrip.latitude - point.latitude) < 1e-9)
    assertTrue(abs(roundTrip.longitude - point.longitude) < 1e-9)
}

@Test
fun northwardDegreeMapsToExpectedMeters() {
    val north = projection.toLocal(GeoPoint(origin.latitude + 0.001, origin.longitude))
    assertTrue(abs(north.x) < 1e-6)
    assertTrue(abs(north.y - 111.32) < 0.01)
}
```

**Apply to:** Review list/detail state should be pre-derived for UI rendering. `TraceProjection` should transform canonical lat/lon into screen/local points without mutating saved geographic data.

---

### Compose UI: `AppShell.kt`, `DriveScreen.kt`, `ReviewScreen.kt`, `TraceView.kt`, `App.kt`

**Analog:** `App.kt`

**Theme pattern** (`App.kt` lines 37-50):
```kotlin
@Composable
@Preview
fun App(orientationController: OrientationController = NoOpOrientationController) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF05070A),
            surface = Color(0xFF101722),
            primary = Color(0xFF62E3FF),
            secondary = Color(0xFFFFD166),
        )
    ) {
        LapSightApp(orientationController)
    }
}
```

**State and timer pattern** (`App.kt` lines 52-99):
```kotlin
@Composable
fun LapSightApp(orientationController: OrientationController = NoOpOrientationController) {
    val session = remember { DemoLapSession() }
    var dash by remember { mutableStateOf(session.dashState) }

    var orientation by remember { mutableStateOf(DashOrientation.Portrait) }
    LaunchedEffect(orientation) {
        orientationController.apply(orientation)
    }

    LaunchedEffect(dash.isRunning) {
        while (dash.isRunning && !session.isFinished) {
            delay(700)
            dash = session.tick()
        }
    }

    LapDashboard(
        dash = dash,
        orientation = orientation,
        onToggleOrientation = { /* update state */ },
        onStart = {
            session.start()
            dash = session.dashState
        },
        onStop = {
            session.stop()
            dash = session.dashState
        },
        onReset = {
            session.reset()
            dash = session.dashState
        },
    )
}
```

**Responsive dash layout pattern** (`App.kt` lines 110-151):
```kotlin
BoxWithConstraints(
    modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .safeContentPadding()
) {
    val isLandscape = maxWidth > maxHeight
    val isCompactLandscape = isLandscape && maxHeight < 520.dp
    val dashboardPadding = if (isCompactLandscape) 12.dp else 20.dp
    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(dashboardPadding),
            horizontalArrangement = Arrangement.spacedBy(if (isCompactLandscape) 12.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderPanel(dash, Modifier.weight(0.9f), compact = isCompactLandscape)
            LapMetricsPanel(dash, Modifier.weight(1.3f), compact = isCompactLandscape)
            ControlPanel(dash, orientation, onToggleOrientation, onStart, onStop, onReset, Modifier.weight(0.9f), compact = isCompactLandscape)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dashboardPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeaderPanel(dash, Modifier.fillMaxWidth())
            LapMetricsPanel(dash, Modifier.fillMaxWidth())
            ControlPanel(dash, orientation, onToggleOrientation, onStart, onStop, onReset, Modifier.fillMaxWidth())
        }
    }
}
```

**Safety/source labeling pattern** (`App.kt` lines 174-192, 394-412):
```kotlin
Text(
    text = "Closed-course timing aid. Phone GPS accuracy varies; this is not pro-grade timing. Verify before trusting lap data.",
    color = Color(0xFFCED7E2),
    fontSize = if (compact) 11.sp else 13.sp,
    lineHeight = if (compact) 15.sp else 17.sp,
)

Text(
    text = dash.fixStatus.label,
    color = dash.fixStatus.color,
    fontSize = if (compact) 15.sp else 17.sp,
    fontWeight = FontWeight.Bold,
)

private val GpsFixStatus.label: String
    get() = when (this) {
        GpsFixStatus.Idle -> "IDLE"
        GpsFixStatus.Acquiring -> "ACQUIRING"
        GpsFixStatus.Simulated -> "SIMULATED REPLAY"
        GpsFixStatus.Live -> "LIVE GPS"
        GpsFixStatus.Degraded -> "DEGRADED FIX"
        GpsFixStatus.Unavailable -> "UNAVAILABLE"
    }
```

**Metric card pattern** (`App.kt` lines 285-340):
```kotlin
@Composable
private fun MetricCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    compact: Boolean = false,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(if (compact) 12.dp else 16.dp)) {
            Text(
                text = label.uppercase(),
                color = Color(0xFF7E8DA0),
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = when {
                        emphasized && compact -> 40.sp
                        emphasized -> 52.sp
                        compact -> 22.sp
                        else -> 28.sp
                    },
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}
```

**Apply to:** refactor `App.kt` into a shell plus screen components without introducing a navigation package unless planning chooses otherwise. Use Material3 `NavigationBar`/`NavigationBarItem`, keep the existing dark palette, preserve closed-course safety copy, and hide bottom navigation in fullscreen Drive mode.

---

### Platform Boundaries: Android/iOS storage roots and export handoff

**Analogs:** `OrientationController.kt`, `MainActivity.kt`, `MainViewController.kt`, `ContentView.swift`

**Android platform injection pattern** (`MainActivity.kt` lines 14-33):
```kotlin
class MainActivity : ComponentActivity() {
    private val orientationController = object : OrientationController {
        override fun apply(orientation: DashOrientation) {
            requestedOrientation = when (orientation) {
                DashOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                DashOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(orientationController)
        }
    }
}
```

**iOS shared Compose entry pattern** (`MainViewController.kt` lines 1-5; `ContentView.swift` lines 5-16):
```kotlin
package com.huanfuli.lapsight.shared

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController { App() }
```

```swift
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Self.Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
```

**Apply to:** keep platform-specific storage/share behavior behind injected or expect/actual boundaries. Do not move storage, export schema, or timing logic into Android/iOS entrypoints.

---

### Tests: Wave 0 files

**Analogs:** `LapEngineTest.kt`, `ReplayTest.kt`, `LapTestSupport.kt`, `GeometryTest.kt`, `LapDashStateTest.kt`, `LapModelsTest.kt`

**Test imports/style pattern** (`LapEngineTest.kt` lines 1-14):
```kotlin
package com.huanfuli.lapsight.shared.lap

import com.huanfuli.lapsight.shared.lap.LapTestSupport.sample
import com.huanfuli.lapsight.shared.lap.LapTestSupport.verticalSector
import com.huanfuli.lapsight.shared.lap.LapTestSupport.verticalStartFinish
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LapEngineTest {
    private val course = CourseDefinition(startFinish = verticalStartFinish())
    private val lenient = LapEngineConfig.lenientForTests()
}
```

**Shared fixture helper pattern** (`LapTestSupport.kt` lines 13-44, 62-68):
```kotlin
object LapTestSupport {

    val ORIGIN = GeoPoint(latitude = 0.0, longitude = 0.0)

    fun lonForMetersEast(meters: Double): Double =
        meters / LocalProjection.METERS_PER_DEGREE

    fun latForMetersNorth(meters: Double): Double =
        meters / LocalProjection.METERS_PER_DEGREE

    fun sample(
        elapsedMillis: Long,
        eastMeters: Double,
        northMeters: Double = 0.0,
        accuracy: Double? = 5.0,
        speed: Double? = 12.0,
        heading: Double? = 90.0,
    ): LocationSample = LocationSample(
        elapsedMillis = elapsedMillis,
        latitude = latForMetersNorth(northMeters),
        longitude = lonForMetersEast(eastMeters),
        horizontalAccuracyMeters = accuracy,
        speedMetersPerSecond = speed,
        headingDegrees = heading,
        altitudeMeters = 200.0,
        source = LocationSource.Simulated,
    )

    fun run(course: CourseDefinition, config: LapEngineConfig, samples: List<LocationSample>): LapTimingState {
        val engine = LapEngine(course, config)
        var state = engine.state
        for (s in samples) state = engine.onSample(s)
        return state
    }
}
```

**Determinism/replay assertion pattern** (`ReplayTest.kt` lines 11-21, 38-50):
```kotlin
class ReplayTest {

    private fun runner(config: LapEngineConfig = ReplayFixtures.DEMO_CONFIG) =
        ReplayRunner(ReplayFixtures.DEMO_COURSE, config)

    @Test
    fun replayIsDeterministic() {
        val a = runner().run(ReplayFixtures.multiLapLoop())
        val b = runner().run(ReplayFixtures.multiLapLoop())
        assertEquals(a.finalState, b.finalState)
    }

    @Test
    fun multiLapProducesExpectedCounters() {
        val durations = listOf(40_000L, 32_000L, 36_000L)
        val result = runner().run(ReplayFixtures.multiLapLoop(durations))

        assertEquals(3, result.finalState.lapCount)
        val best = result.finalState.bestLapMillis!!
        val completed = result.finalState.completedLaps.map { it.durationMillis }
        assertEquals(completed.min(), best)
        assertEquals(completed.last(), result.finalState.lastLapMillis)
    }
}
```

**State-machine assertion pattern** (`LapDashStateTest.kt` lines 17-31, 43-61):
```kotlin
@Test
fun demoSessionAdvancesAndCompletesLaps() {
    val session = DemoLapSession()
    session.start()
    while (!session.isFinished) {
        session.tick()
    }
    val dash = session.dashState

    assertEquals(3, dash.lapCount)
    assertTrue(dash.bestLapMillis != null)
    assertTrue(dash.lastLapMillis != null)
    assertEquals("Demo Course", dash.courseName)
    assertTrue(!dash.isRunning)
}

@Test
fun tickIsNoOpWhenStopped() {
    val session = DemoLapSession()
    val before = session.dashState
    val after = session.tick()
    assertEquals(before, after)
}
```

**Validation/failure assertion pattern** (`LapModelsTest.kt` lines 33-47):
```kotlin
@Test
fun configRejectsInvalidValues() {
    assertFailsWith<IllegalArgumentException> {
        LapEngineConfig(minLapDurationMillis = -1)
    }
    assertFailsWith<IllegalArgumentException> {
        LapEngineConfig(maxHorizontalAccuracyMeters = 0.0)
    }
}

@Test
fun lapEventDurationIsEndMinusStart() {
    val lap = LapEvent(lapNumber = 1, startMillis = 1_000, endMillis = 91_000)
    assertEquals(90_000, lap.durationMillis)
}
```

**Apply to:** create Wave 0 tests before broad implementation. Use `kotlin.test`, deterministic synthetic samples, no UI/platform services, and direct assertions on state and payload contents.

## Shared Patterns

### Auth/Guard

**Source:** planning constraints and current codebase
**Apply to:** all Phase 3 files

No authentication pattern exists or should be introduced. Phase 3 is local-only. Product "sessions" are timing data, not auth sessions.

### Clean Shared Domain

**Source:** `LapEngine.kt` lines 6-11 and `ReplayRunner.kt` lines 42-60
**Apply to:** providers, track, session, storage DTOs, exporters, review models, tests

```kotlin
class ReplayRunner(
    private val course: CourseDefinition,
    private val config: LapEngineConfig = LapEngineConfig(),
) {
    fun run(samples: List<LocationSample>): ReplayResult {
        val engine = LapEngine(course, config)
        val steps = samples.map { sample ->
            ReplayStep(sample = sample, state = engine.onSample(sample))
        }
        return ReplayResult(steps)
    }
}
```

Keep algorithms and state machines independent of Compose, Android, iOS, and file-system side effects where possible.

### Source Metadata and Demo Labeling

**Source:** `LocationSource` in `GpsProbeModels.kt` lines 8-12; UI labels/colors in `App.kt` lines 394-412
**Apply to:** simulated provider, saved tracks/sessions, review rows, exports, ghost-candidate filtering

```kotlin
enum class LocationSource {
    Simulated,
    PhoneGps,
    ExternalGnss,
}
```

Every simulated sample and saved/exported entity derived from simulated samples must remain visibly demo/simulated.

### Error Handling and Validation

**Source:** `LapEngineConfig.kt` lines 31-38; `SegmentGeometry.kt` null-return boundaries lines 73-87 and 105-108
**Apply to:** schema versions, storage decode/import validation, GPX escaping, reference extractor config

Use `require` for invalid constructor/config input. For expected geometry/parse misses, return `null` or typed result state instead of throwing.

### Response/State Formatting

**Source:** `LapDashState.kt` lines 14-39 and 93-107
**Apply to:** Review rows, Track Review state, Timing Session Review state, export filenames

Derive display labels in shared presentation models so Android and iOS render identical strings.

### UI Safety Language

**Source:** `App.kt` lines 174-179
**Apply to:** Drive, fullscreen Drive, Settings/help text, review/export surfaces where accuracy could be misread

```kotlin
Text(
    text = "Closed-course timing aid. Phone GPS accuracy varies; this is not pro-grade timing. Verify before trusting lap data.",
    color = Color(0xFFCED7E2),
    fontSize = if (compact) 11.sp else 13.sp,
    lineHeight = if (compact) 15.sp else 17.sp,
)
```

### Platform Boundary

**Source:** `OrientationController.kt` lines 14-24; `MainActivity.kt` lines 27-33; `MainViewController.kt` lines 1-5
**Apply to:** storage roots, export/share handoff, future GPS providers

Platform code should provide capabilities; shared code should own product state and business logic.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `shared/src/commonMain/composeResources/files/fixtures/*.json` | fixture | file-I/O | No committed JSON resources exist yet. Use RESEARCH.md fixture schema recommendations. |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/FileSessionStore.kt` | service | file-I/O | No local persistence implementation exists. Use RESEARCH.md Okio/FileSystem and atomic write guidance. |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/export/JsonExportService.kt` | service | transform/file-I/O | No serialization/export service exists yet. Use canonical DTO and `kotlinx.serialization-json`. |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/export/GpxExportService.kt` | service | transform/file-I/O | No XML/GPX writer exists. Implement narrowly with XML escaping and golden tests. |

## Metadata

**Analog search scope:** `shared/src/commonMain`, `shared/src/commonTest`, `shared/src/androidHostTest`, `androidApp/src/main`, `shared/src/iosMain`, `iosApp/iosApp`, `gradle`
**Files scanned:** 68 repository files from `rg --files`
**Strong analogs read:** `GpsProbeModels.kt`, `TimingLines.kt`, `LapModels.kt`, `LapEngine.kt`, `LapEngineConfig.kt`, `ReplayRunner.kt`, `ReplayFixtures.kt`, `DemoLapSession.kt`, `LapDashState.kt`, `LocalProjection.kt`, `SegmentGeometry.kt`, `CrossingDetector.kt`, `App.kt`, `LapEngineTest.kt`, `ReplayTest.kt`, `LapTestSupport.kt`, `GeometryTest.kt`, `CrossingDetectorTest.kt`, `LapDashStateTest.kt`, `LapModelsTest.kt`, `shared/build.gradle.kts`, `gradle/libs.versions.toml`, Android/iOS entrypoints
**Pattern extraction date:** 2026-06-25
