package com.huanfuli.lapsight.shared.ui

import com.huanfuli.lapsight.shared.ghost.DeltaDisplayState
import com.huanfuli.lapsight.shared.ghost.DeltaTone
import com.huanfuli.lapsight.shared.ghost.DeltaUnavailableReason
import com.huanfuli.lapsight.shared.ghost.LiveDeltaSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Wave 0 (RED) coverage for the value-only live-delta display contract
 * (Plan 04-03 Task 1; D-10, D-14, D-15, D-17, D-18).
 *
 * These tests are pure/common and encode the exact UI-SPEC copy and color
 * semantics WITHOUT depending on Compose rendering: the production type maps a
 * domain [LiveDeltaSnapshot] to a display-independent text + tone so Android and
 * iOS render identical strings and the verifier can assert behavior without a
 * screenshot harness.
 */
class DeltaDisplayStateTest {

    private fun available(deltaMillis: Long) = LiveDeltaSnapshot.Available(
        deltaMillis = deltaMillis,
        currentElapsedMillis = 0L,
        referenceElapsedMillis = 0L,
        progressMeters = 0.0,
        normalizedProgress = 0.0,
    )

    @Test
    fun fasterDeltaFormatsWithMinusSignAndFasterTone() {
        // D-10/D-14/D-15: faster is value-only `-0.218s` and green/faster.
        val state = DeltaDisplayState.from(available(-218L))
        assertEquals("-0.218s", state.text)
        assertEquals(DeltaTone.Faster, state.tone)
        assertTrue(state.isAvailable)
    }

    @Test
    fun slowerDeltaFormatsWithPlusSignAndSlowerTone() {
        // D-10/D-14/D-15: slower keeps an explicit `+` and is orange/red/slower.
        val state = DeltaDisplayState.from(available(421L))
        assertEquals("+0.421s", state.text)
        assertEquals(DeltaTone.Slower, state.tone)
        assertTrue(state.isAvailable)
    }

    @Test
    fun unavailableFormatsAsDoubleDashAndNeutral() {
        // D-17: no-reference / unavailable renders `--` neutral.
        val state = DeltaDisplayState.from(
            LiveDeltaSnapshot.Unavailable(DeltaUnavailableReason.NoReference),
        )
        assertEquals("--", state.text)
        assertEquals(DeltaTone.Neutral, state.tone)
        assertFalse(state.isAvailable)
    }

    @Test
    fun unavailableAfterAvailableClearsPreviousValue() {
        // D-18: the UI must never keep a stale delta when the engine reports
        // unavailable; the pure mapping returns `--` regardless of prior value.
        val first = DeltaDisplayState.from(available(-218L))
        assertEquals("-0.218s", first.text)

        val cleared = DeltaDisplayState.from(
            LiveDeltaSnapshot.Unavailable(DeltaUnavailableReason.PoorGpsQuality),
        )
        assertEquals("--", cleared.text)
        assertEquals(DeltaTone.Neutral, cleared.tone)
        assertFalse(cleared.isAvailable)
    }

    @Test
    fun displayTextContainsNoDirectionalWords() {
        // D-14: live moving copy is sign + seconds only — no words.
        val forbidden = listOf("ahead", "behind", "faster", "slower", "ghost")
        val texts = listOf(
            DeltaDisplayState.from(available(-218L)).text,
            DeltaDisplayState.from(available(421L)).text,
            DeltaDisplayState.from(
                LiveDeltaSnapshot.Unavailable(DeltaUnavailableReason.NoCurrentLap),
            ).text,
        )
        texts.forEach { text ->
            forbidden.forEach { word ->
                assertFalse(
                    text.lowercase().contains(word),
                    "delta text '$text' must not contain the word '$word'",
                )
            }
        }
    }

    @Test
    fun multiSecondDeltaKeepsSecondsAndMillisComponents() {
        // D-14: seconds component is preserved beyond sub-second deltas.
        assertEquals("+1.500s", DeltaDisplayState.from(available(1_500L)).text)
        assertEquals("-2.045s", DeltaDisplayState.from(available(-2_045L)).text)
    }
}
