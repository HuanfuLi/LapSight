package com.huanfuli.lapsight

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.LocationSampleProvider
import com.huanfuli.lapsight.shared.LocationSource

class AndroidFusedLocationSampleProvider(
    context: Context,
    private val hasFineLocationPermission: () -> Boolean,
) : LocationSampleProvider {
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context.applicationContext)
    private val queue = ArrayDeque<LocationSample>()
    private var running = false
    private var feedStartElapsedRealtimeMillis = 0L
    private var lastSampleElapsedMillis: Long? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::enqueue)
        }
    }

    override val isRunning: Boolean
        get() = running

    override fun start() {
        if (running) return
        if (!hasFineLocationPermission()) {
            running = false
            return
        }
        running = true
        feedStartElapsedRealtimeMillis = SystemClock.elapsedRealtime()
        lastSampleElapsedMillis = null
        synchronized(queue) {
            queue.clear()
        }
        requestUpdates()
    }

    override fun stop() {
        if (!running) return
        running = false
        client.removeLocationUpdates(callback)
    }

    override fun reset() {
        stop()
        feedStartElapsedRealtimeMillis = SystemClock.elapsedRealtime()
        lastSampleElapsedMillis = null
        synchronized(queue) {
            queue.clear()
        }
    }

    override fun nextSample(): LocationSample? =
        synchronized(queue) {
            queue.removeFirstOrNull()
        }

    @SuppressLint("MissingPermission")
    private fun requestUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MILLIS)
            .setMinUpdateIntervalMillis(FASTEST_UPDATE_INTERVAL_MILLIS)
            .setMaxUpdateDelayMillis(0L)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener {
                    running = false
                }
        } catch (_: SecurityException) {
            running = false
        }
    }

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
        )

        synchronized(queue) {
            while (queue.size >= MAX_QUEUE_SIZE) {
                queue.removeFirst()
            }
            queue.addLast(sample)
        }
    }

    private companion object {
        const val UPDATE_INTERVAL_MILLIS = 100L
        const val FASTEST_UPDATE_INTERVAL_MILLIS = 100L
        const val MAX_QUEUE_SIZE = 1_000
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
