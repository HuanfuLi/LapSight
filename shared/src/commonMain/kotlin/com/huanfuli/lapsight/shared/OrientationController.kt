package com.huanfuli.lapsight.shared

/** The orientation the mounted dash is locked to. */
enum class DashOrientation { Portrait, Landscape }

/**
 * Platform hook to LOCK the window to an explicitly chosen [DashOrientation].
 *
 * This must NEVER use the device accelerometer/gravity sensor to decide
 * orientation. On a mounted phone, real racing G-forces and vibration swing the
 * device tilt unpredictably, so sensor-driven rotation would flip the dash
 * mid-corner. Orientation is changed only when the user taps the in-app toggle.
 */
interface OrientationController {
    fun apply(orientation: DashOrientation)
}

/**
 * Default no-op controller for platforms/contexts without a window-lock binding
 * (iOS, Compose previews, tests). Orientation stays as the host provides it.
 */
object NoOpOrientationController : OrientationController {
    override fun apply(orientation: DashOrientation) {}
}
