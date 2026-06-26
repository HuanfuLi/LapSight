package com.huanfuli.lapsight.shared.ghost

import kotlin.math.abs

/**
 * Semantic tone of a live-delta readout (D-15). Platform-free so the same
 * mapping drives Android/iOS; Compose color values are applied at the UI edge.
 *
 * - [Faster]: current lap is ahead of the reference (negative delta) → green.
 * - [Slower]: current lap is behind the reference (positive delta) → orange/red.
 * - [Neutral]: delta is unavailable (`--`) → muted neutral.
 */
enum class DeltaTone { Faster, Slower, Neutral }

/**
 * Display-independent, value-only live-delta readout (D-10, D-14, D-15, D-17,
 * D-18).
 *
 * Pure presentation derived from the domain [LiveDeltaSnapshot]: a sign-prefixed
 * seconds string plus a semantic [tone], with NO directional words ("ahead",
 * "behind", "faster", "slower", "ghost") so the moving dashboard stays glanceable
 * and safe. Lives in the ghost package next to [LiveDeltaSnapshot] so both the
 * session snapshot and the Compose UI can depend on it without a UI→session or
 * session→UI inversion.
 *
 * Mapping is stateless: an [LiveDeltaSnapshot.Unavailable] always yields `--`
 * regardless of any previously available value, so the UI can never render a
 * stale delta (D-18).
 *
 * @property text exactly what the dashboard shows, e.g. `-0.218s`, `+0.421s`, `--`.
 * @property tone semantic color bucket.
 * @property isAvailable true only when a concrete delta value is shown.
 */
data class DeltaDisplayState(
    val text: String,
    val tone: DeltaTone,
    val isAvailable: Boolean,
) {
    companion object {
        /** Copy shown when no trustworthy delta exists (D-17). */
        const val UNAVAILABLE_TEXT: String = "--"

        /** Canonical unavailable readout (`--`, neutral, not available). */
        val UNAVAILABLE: DeltaDisplayState =
            DeltaDisplayState(UNAVAILABLE_TEXT, DeltaTone.Neutral, isAvailable = false)

        /** Map a domain delta snapshot to its value-only display state. */
        fun from(snapshot: LiveDeltaSnapshot): DeltaDisplayState = when (snapshot) {
            is LiveDeltaSnapshot.Available -> fromDeltaMillis(snapshot.deltaMillis)
            is LiveDeltaSnapshot.Unavailable -> UNAVAILABLE
        }

        /**
         * Format a signed delta in milliseconds as value-only seconds copy.
         *
         * Negative is faster (`-`), positive is slower (`+`); the explicit sign is
         * always preserved (D-10). Millis are zero-padded to three digits so the
         * readout stays width-stable as the delta ticks.
         */
        fun fromDeltaMillis(deltaMillis: Long): DeltaDisplayState {
            val tone = when {
                deltaMillis < 0L -> DeltaTone.Faster
                deltaMillis > 0L -> DeltaTone.Slower
                else -> DeltaTone.Neutral
            }
            val sign = if (deltaMillis < 0L) "-" else "+"
            val absMillis = abs(deltaMillis)
            val seconds = absMillis / 1_000L
            val millis = absMillis % 1_000L
            val mmm = millis.toString().padStart(3, '0')
            return DeltaDisplayState(
                text = "$sign$seconds.${mmm}s",
                tone = tone,
                isAvailable = true,
            )
        }
    }
}
