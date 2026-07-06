package com.huanfuli.lapsight.shared.session

import com.huanfuli.lapsight.shared.GpsQualitySummary
import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.track.CurrentProfileResolution

/**
 * One conservative reason a session is NOT Ready to begin formal timing (D-14).
 *
 * Each value maps one-to-one to a single Ready input so the dash and tests can
 * surface exactly which condition failed. Motion, speed, and heading are NOT
 * inputs (D-15): the user need not already be moving for Ready.
 */
enum class ReadyBlocker {
    /** No GPS fix has been delivered yet. */
    MissingFix,

    /** The fix carries non-finite / out-of-range coordinates. */
    NonFiniteFix,

    /** Horizontal accuracy is missing or worse than the conservative threshold. */
    PoorAccuracy,

    /** The fix is older than the freshness threshold (stale). */
    StaleFix,

    /** Recent sample rate / update cadence is below the conservative floor. */
    LowSampleRate,

    /** No current course/profile is selected. */
    NoCourseSelected,

    /** The selected revision has no confirmed start/finish line. */
    StartFinishUnconfirmed,

    /** The selected course direction is incompatible with the chosen reference. */
    DirectionIncompatible,

    /** Wrong-course preflight blocked Ready (clearly far from the course, D-18). */
    WrongCourseBlocked,

    /** Wrong-course preflight could not make a trustworthy decision. */
    PreflightUnavailable,
}

/**
 * Conservative, injectable Ready thresholds (D-14, the agent's-discretion clause).
 *
 * Defaults are derived from the existing engine/preflight constants so Ready is
 * strict but still reachable on real phone GPS:
 *  - [maxHorizontalAccuracyMeters] = the engine's degraded-accuracy line
 *    ([GpsQualitySummary.DEFAULT_DEGRADED_ACCURACY_METERS] = 25.0 m), stricter
 *    than the preflight's 100 m course-distance accuracy gate.
 *  - [maxFixAgeMillis] = 15 s, the wrong-course preflight freshness window.
 *  - [minSampleRateHz] = 0.9 Hz, the observed real Fused-Location delivery floor
 *    (RESEARCH "Sampling-rate (literal Nyquist) note": requested ~10 Hz, real
 *    delivery often hovers around 1 Hz). Below this a start/finish crossing cannot be
 *    trustworthily reconstructed.
 *
 * Constructing thresholds with non-finite or non-positive values throws via
 * [init] require so a misconfigured gate fails loudly at construction, never at
 * decision time.
 */
data class ReadyThresholds(
    val maxHorizontalAccuracyMeters: Double = GpsQualitySummary.DEFAULT_DEGRADED_ACCURACY_METERS,
    val maxFixAgeMillis: Long = 15_000L,
    val minSampleRateHz: Double = 0.9,
) {
    init {
        require(maxHorizontalAccuracyMeters > 0.0 && maxHorizontalAccuracyMeters.isFinite()) {
            "maxHorizontalAccuracyMeters must be finite and > 0"
        }
        require(maxFixAgeMillis >= 0L) { "maxFixAgeMillis must be >= 0" }
        require(minSampleRateHz > 0.0 && minSampleRateHz.isFinite()) {
            "minSampleRateHz must be finite and > 0"
        }
    }

    companion object {
        val Default = ReadyThresholds()
    }
}

/**
 * Result of the aggregate Ready decision (D-13, D-14).
 *
 * Ready is a typed result, never a bare Boolean, so each blocker is independently
 * testable and the dash can show a primary not-Ready reason.
 */
sealed interface ReadyState {
    /** Every Ready input is satisfied; formal timing may begin (D-13). */
    data object Ready : ReadyState

    /** One or more inputs failed; [reasons] lists every applicable blocker. */
    data class NotReady(val reasons: List<ReadyBlocker>) : ReadyState
}

/**
 * The single conservative Ready gate over all seven D-14 inputs plus wrong-course
 * preflight (D-13, D-14, D-15).
 *
 * Pure decision function: it owns no UI, storage, platform, matcher, or
 * lap-engine state, and never throws on malformed GPS — bad input is data, so a
 * non-finite / out-of-range fix returns [ReadyState.NotReady] with
 * [ReadyBlocker.NonFiniteFix] rather than an exception. Motion / speed / heading
 * are deliberately NOT inputs (D-15).
 *
 * The result accumulates EVERY applicable [ReadyBlocker], so a dash can show a
 * primary reason while replay/tests can assert the full set. Returns
 * [ReadyState.Ready] only when all inputs are satisfied.
 *
 * @param latest the most recent GPS fix, or null when none has arrived.
 * @param nowElapsedMillis the elapsed-clock reference used for freshness.
 * @param recentRateHz recent sample rate / update cadence, or null when unknown.
 * @param selection the resolved current course/profile selection.
 * @param startFinishConfirmed whether the selected revision has a confirmed
 *   start/finish line.
 * @param directionCompatible whether the selected course direction is compatible.
 * @param preflight the wrong-course preflight outcome (D-18).
 * @param thresholds conservative, injectable Ready thresholds.
 */
fun aggregateReady(
    latest: LocationSample?,
    nowElapsedMillis: Long,
    recentRateHz: Double?,
    selection: CurrentProfileResolution,
    startFinishConfirmed: Boolean,
    directionCompatible: Boolean,
    preflight: CoursePreflightResult,
    thresholds: ReadyThresholds = ReadyThresholds.Default,
): ReadyState {
    val reasons = mutableListOf<ReadyBlocker>()

    if (latest == null) {
        reasons += ReadyBlocker.MissingFix
    } else if (!latest.latitude.isFinite() ||
        !latest.longitude.isFinite() ||
        latest.latitude !in -90.0..90.0 ||
        latest.longitude !in -180.0..180.0
    ) {
        reasons += ReadyBlocker.NonFiniteFix
    } else {
        val accuracy = latest.horizontalAccuracyMeters
        if (accuracy == null ||
            !accuracy.isFinite() ||
            accuracy < 0.0 ||
            accuracy > thresholds.maxHorizontalAccuracyMeters
        ) {
            reasons += ReadyBlocker.PoorAccuracy
        }
        val ageMillis = nowElapsedMillis - latest.elapsedMillis
        if (latest.elapsedMillis < 0L || ageMillis < 0L || ageMillis > thresholds.maxFixAgeMillis) {
            reasons += ReadyBlocker.StaleFix
        }
    }

    if (recentRateHz == null || !recentRateHz.isFinite() || recentRateHz < thresholds.minSampleRateHz) {
        reasons += ReadyBlocker.LowSampleRate
    }

    if (selection !is CurrentProfileResolution.Selected) {
        reasons += ReadyBlocker.NoCourseSelected
    }

    if (!startFinishConfirmed) {
        reasons += ReadyBlocker.StartFinishUnconfirmed
    }

    if (!directionCompatible) {
        reasons += ReadyBlocker.DirectionIncompatible
    }

    when (preflight) {
        is CoursePreflightResult.Blocked -> reasons += ReadyBlocker.WrongCourseBlocked
        is CoursePreflightResult.Unavailable -> reasons += ReadyBlocker.PreflightUnavailable
        is CoursePreflightResult.Ready -> Unit
    }

    return if (reasons.isEmpty()) ReadyState.Ready else ReadyState.NotReady(reasons)
}

/**
 * Production Start button gate. This is intentionally less strict than
 * [aggregateReady]: a low update rate, poor horizontal accuracy, stale-ish fix,
 * or unavailable course-distance preflight are quality warnings, not reasons to
 * trap the user before timing can start.
 *
 * The hard blockers are limited to conditions that make starting impossible or
 * clearly unsafe as data: no usable fix, no selected/confirmed course, direction
 * incompatibility, or a trustworthy wrong-course block.
 */
fun aggregateStartReady(
    latest: LocationSample?,
    selection: CurrentProfileResolution,
    startFinishConfirmed: Boolean,
    directionCompatible: Boolean,
    preflight: CoursePreflightResult,
): ReadyState {
    val reasons = mutableListOf<ReadyBlocker>()

    if (latest == null) {
        reasons += ReadyBlocker.MissingFix
    } else if (!latest.latitude.isFinite() ||
        !latest.longitude.isFinite() ||
        latest.latitude !in -90.0..90.0 ||
        latest.longitude !in -180.0..180.0
    ) {
        reasons += ReadyBlocker.NonFiniteFix
    }

    if (selection !is CurrentProfileResolution.Selected) {
        reasons += ReadyBlocker.NoCourseSelected
    }

    if (!startFinishConfirmed) {
        reasons += ReadyBlocker.StartFinishUnconfirmed
    }

    if (!directionCompatible) {
        reasons += ReadyBlocker.DirectionIncompatible
    }

    if (preflight is CoursePreflightResult.Blocked) {
        reasons += ReadyBlocker.WrongCourseBlocked
    }

    return if (reasons.isEmpty()) ReadyState.Ready else ReadyState.NotReady(reasons)
}
