package com.huanfuli.lapsight

import android.content.Context
import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.LocationSampleProvider

/**
 * The single phone-GPS [LocationSampleProvider] handed to the shared app, which
 * picks its engine per feed run:
 *
 * - **Fused** (default): Google Play Services fused location — robust, sensor-aided.
 * - **Direct GNSS** (opt-in via [useDirectGnss]): raw `GPS_PROVIDER` for the
 *   highest rate the chipset offers plus satellite/L5 quality signals.
 *
 * The engine is chosen at [start] from the current [useDirectGnss] preference, so
 * toggling the setting takes effect on the next feed start (source change / timing
 * start), never mid-run. If Direct GNSS is requested but the device has no raw GPS
 * provider, it transparently falls back to Fused so the dash still gets a feed.
 */
class AndroidPhoneLocationProvider(
    context: Context,
    hasFineLocationPermission: () -> Boolean,
    private val useDirectGnss: () -> Boolean,
) : LocationSampleProvider {

    private val fused = AndroidFusedLocationSampleProvider(context, hasFineLocationPermission)
    private val gnss = AndroidGnssLocationProvider(context, hasFineLocationPermission)
    private var active: LocationSampleProvider = fused

    override val isRunning: Boolean
        get() = active.isRunning

    override fun start() {
        if (active.isRunning) return
        val next: LocationSampleProvider = if (useDirectGnss()) gnss else fused
        active = next
        next.start()
        // Direct GNSS declines to run when the device exposes no raw GPS provider;
        // fall back to Fused so the feed is never silently dead.
        if (next === gnss && !gnss.isRunning) {
            active = fused
            fused.start()
        }
    }

    override fun stop() {
        active.stop()
    }

    override fun reset() {
        // Clear both engines so a stale backlog can never leak across a swap.
        fused.reset()
        gnss.reset()
        active = fused
    }

    override fun nextSample(): LocationSample? = active.nextSample()

    override fun drainPending(): List<LocationSample> = active.drainPending()
}
