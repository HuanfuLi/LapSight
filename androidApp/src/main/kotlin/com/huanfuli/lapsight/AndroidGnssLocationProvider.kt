package com.huanfuli.lapsight

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.LocationSampleProvider
import com.huanfuli.lapsight.shared.LocationSource

/**
 * Direct-from-chip phone GNSS feed via [LocationManager] `GPS_PROVIDER`.
 *
 * The Fused Location provider deliberately throttles and duty-cycles the GNSS
 * radio for battery life, which for most phones pins it near 1 Hz. This provider
 * asks the raw GPS provider for updates as fast as the chipset will emit them
 * (`minTime = 0`), and — where supported — keeps the receiver in continuous
 * (non-duty-cycled) tracking so a mounted racing dash gets the steadiest,
 * highest-rate fixes the hardware can produce.
 *
 * It also observes [GnssStatus] to report satellites-in-use and dual-frequency
 * (L1 + L5/L2) capability on each fix — the signal that actually correlates with
 * better positional accuracy. Those land on [LocationSample] for the dash and the
 * saved GPS-quality rollup.
 *
 * Poll semantics mirror [AndroidFusedLocationSampleProvider]: fixes are enqueued
 * as they arrive and [drainPending] hands the whole backlog to the consumer each
 * tick so nothing is dropped at the poll rate.
 */
class AndroidGnssLocationProvider(
    context: Context,
    private val hasFineLocationPermission: () -> Boolean,
) : LocationSampleProvider {

    private val locationManager: LocationManager? =
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val queue = ArrayDeque<LocationSample>()
    private var running = false
    private var feedStartElapsedRealtimeMillis = 0L
    private var lastSampleElapsedMillis: Long? = null

    // Latest GNSS-status-derived quality, applied to subsequent fixes.
    @Volatile private var satellitesInUse: Int? = null
    @Volatile private var usesDualFrequency: Boolean? = null

    private val locationListener = LocationListener { location -> enqueue(location) }

    private val statusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            var hasL1 = false
            var hasSecondary = false
            for (i in 0 until status.satelliteCount) {
                if (!status.usedInFix(i)) continue
                used++
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && status.hasCarrierFrequencyHz(i)) {
                    when (bandOf(status.getCarrierFrequencyHz(i))) {
                        Band.L1 -> hasL1 = true
                        Band.SECONDARY -> hasSecondary = true
                        Band.OTHER -> Unit
                    }
                }
            }
            satellitesInUse = used
            // Dual-frequency only counts when a lower band accompanies L1 in the fix.
            usesDualFrequency = if (hasL1 && hasSecondary) true else if (used > 0) false else null
        }
    }

    override val isRunning: Boolean
        get() = running

    @SuppressLint("MissingPermission")
    override fun start() {
        if (running) return
        val manager = locationManager ?: return
        if (!hasFineLocationPermission()) {
            running = false
            return
        }
        if (!manager.allProviders.contains(LocationManager.GPS_PROVIDER)) {
            // No raw GPS provider on this device; caller can fall back to Fused.
            running = false
            return
        }
        running = true
        feedStartElapsedRealtimeMillis = SystemClock.elapsedRealtime()
        lastSampleElapsedMillis = null
        satellitesInUse = null
        usesDualFrequency = null
        synchronized(queue) { queue.clear() }
        try {
            manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0L,
                0f,
                locationListener,
                Looper.getMainLooper(),
            )
            manager.registerGnssStatusCallback(statusCallback, mainHandler)
            enableFullTracking(manager)
        } catch (_: SecurityException) {
            running = false
        } catch (_: IllegalArgumentException) {
            running = false
        }
    }

    override fun stop() {
        if (!running) return
        running = false
        locationManager?.let { manager ->
            manager.removeUpdates(locationListener)
            manager.unregisterGnssStatusCallback(statusCallback)
        }
    }

    override fun reset() {
        stop()
        feedStartElapsedRealtimeMillis = SystemClock.elapsedRealtime()
        lastSampleElapsedMillis = null
        satellitesInUse = null
        usesDualFrequency = null
        synchronized(queue) { queue.clear() }
    }

    override fun nextSample(): LocationSample? =
        synchronized(queue) { queue.removeFirstOrNull() }

    override fun drainPending(): List<LocationSample> =
        synchronized(queue) {
            if (queue.isEmpty()) {
                emptyList()
            } else {
                val drained = queue.toList()
                queue.clear()
                drained
            }
        }

    /**
     * Register a full-tracking raw-measurement request purely to hold the GNSS
     * engine out of duty-cycling (steadier, higher-rate fixes). We do not consume
     * the raw measurements; best-effort and API 31+ only.
     */
    @SuppressLint("MissingPermission")
    private fun enableFullTracking(manager: LocationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            val request = android.location.GnssMeasurementRequest.Builder()
                .setFullTracking(true)
                .build()
            manager.registerGnssMeasurementsCallback(
                request,
                java.util.concurrent.Executors.newSingleThreadExecutor(),
                noOpMeasurementsCallback,
            )
        }
    }

    private val noOpMeasurementsCallback =
        object : android.location.GnssMeasurementsEvent.Callback() {}

    private fun enqueue(location: Location) {
        if (!running) return
        if (!hasFineLocationPermission()) {
            stop()
            return
        }

        val rawElapsedMillis =
            location.elapsedRealtimeNanos / NANOS_PER_MILLI - feedStartElapsedRealtimeMillis
        val elapsedMillis = lastSampleElapsedMillis
            ?.let { rawElapsedMillis.coerceAtLeast(it + 1L) }
            ?: rawElapsedMillis.coerceAtLeast(0L)
        lastSampleElapsedMillis = elapsedMillis

        val sample = LocationSample(
            elapsedMillis = elapsedMillis,
            latitude = location.latitude,
            longitude = location.longitude,
            horizontalAccuracyMeters =
                if (location.hasAccuracy()) location.accuracy.toDouble() else null,
            speedMetersPerSecond =
                if (location.hasSpeed()) location.speed.toDouble() else null,
            headingDegrees =
                if (location.hasBearing()) location.bearing.toDouble() else null,
            altitudeMeters =
                if (location.hasAltitude()) location.altitude else null,
            source = LocationSource.PhoneGps,
            speedAccuracyMetersPerSecond =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasSpeedAccuracy()) {
                    location.speedAccuracyMetersPerSecond.toDouble()
                } else {
                    null
                },
            verticalAccuracyMeters =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy()) {
                    location.verticalAccuracyMeters.toDouble()
                } else {
                    null
                },
            satellitesInUse = satellitesInUse,
            usesDualFrequency = usesDualFrequency,
        )

        synchronized(queue) {
            while (queue.size >= MAX_QUEUE_SIZE) {
                queue.removeFirst()
            }
            queue.addLast(sample)
        }
    }

    private enum class Band { L1, SECONDARY, OTHER }

    private companion object {
        const val MAX_QUEUE_SIZE = 1_000
        const val NANOS_PER_MILLI = 1_000_000L

        // GNSS band centers in Hz. L1/E1/B1 sit near 1.575 GHz; the accuracy-helping
        // second civil band (L5/E5a ~1.176 GHz, L2 ~1.227 GHz) sits well below it.
        const val L1_BAND_LOW_HZ = 1.55e9f
        const val L1_BAND_HIGH_HZ = 1.61e9f
        const val SECONDARY_BAND_LOW_HZ = 1.15e9f
        const val SECONDARY_BAND_HIGH_HZ = 1.30e9f

        fun bandOf(carrierFrequencyHz: Float): Band = when (carrierFrequencyHz) {
            in L1_BAND_LOW_HZ..L1_BAND_HIGH_HZ -> Band.L1
            in SECONDARY_BAND_LOW_HZ..SECONDARY_BAND_HIGH_HZ -> Band.SECONDARY
            else -> Band.OTHER
        }
    }
}
