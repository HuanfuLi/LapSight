package com.huanfuli.lapsight.shared.glasses

import com.huanfuli.lapsight.shared.GpsFixStatus
import com.huanfuli.lapsight.shared.ghost.DeltaDisplayState
import com.huanfuli.lapsight.shared.session.TimingRunSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HudModelTest {

    private val readyGps = GlassesGpsState.from(
        fixStatus = GpsFixStatus.Live,
        accuracyMeters = 5.0,
        sampleRateHz = 2.0,
    )

    private val notReadyGps = GlassesGpsState.idle()

    private fun activeRun(
        currentLapMillis: Long? = 83_400L,
        lastLapMillis: Long? = 90_100L,
        bestLapMillis: Long? = 88_250L,
        speedMetersPerSecond: Double? = 20.0,
        lapCount: Int = 2,
        referenceLapMillis: Long? = 88_250L,
        deltaDisplay: DeltaDisplayState = DeltaDisplayState.fromDeltaMillis(-218L),
        latestSectorName: String? = "S2",
        latestSectorSplitMillis: Long? = 12_300L,
    ): TimingRunSnapshot = TimingRunSnapshot.inactive().copy(
        isActive = true,
        currentLapMillis = currentLapMillis,
        lastLapMillis = lastLapMillis,
        bestLapMillis = bestLapMillis,
        speedMetersPerSecond = speedMetersPerSecond,
        lapCount = lapCount,
        referenceLapMillis = referenceLapMillis,
        deltaDisplay = deltaDisplay,
        latestSectorName = latestSectorName,
        latestSectorSplitMillis = latestSectorSplitMillis,
    )

    // --- MR-02: all five readouts present per page ---------------------------

    @Test
    fun deltaOnlyPageCarriesDeltaAndClock() {
        val model = HudModel.from(activeRun(), readyGps, HudPage.DELTA_ONLY, nowEpochMs = 0L, flashUntilEpochMs = null)

        assertEquals(HudPage.DELTA_ONLY, model.page)
        assertEquals("-0.218s", model.deltaText)
        assertEquals(DeltaCaret.Down, model.deltaCaret)
        assertEquals("1:23.4", model.clockText)
    }

    @Test
    fun focusedPageAddsLastBestSpeedFooter() {
        val model = HudModel.from(activeRun(), readyGps, HudPage.FOCUSED, nowEpochMs = 0L, flashUntilEpochMs = null)

        assertEquals(HudPage.FOCUSED, model.page)
        assertEquals("01:30.100", model.lastLapLabel)
        assertEquals("01:28.250", model.bestLapLabel)
        assertEquals("72", model.speedLabel) // 20 m/s * 3.6 = 72 km/h
    }

    @Test
    fun telemetryPageAddsLapCount() {
        val model = HudModel.from(activeRun(lapCount = 5), readyGps, HudPage.TELEMETRY, nowEpochMs = 0L, flashUntilEpochMs = null)

        assertEquals(HudPage.TELEMETRY, model.page)
        assertEquals(5, model.lapCount)
    }

    // --- Delta tone/sign -> caret + passthrough text --------------------------

    @Test
    fun fasterDeltaMapsToCaretDownWithPassthroughText() {
        val run = activeRun(deltaDisplay = DeltaDisplayState.fromDeltaMillis(-421L))
        val model = HudModel.from(run, readyGps, HudPage.DELTA_ONLY, nowEpochMs = 0L, flashUntilEpochMs = null)

        assertEquals(DeltaCaret.Down, model.deltaCaret)
        assertEquals("-0.421s", model.deltaText)
    }

    @Test
    fun slowerDeltaMapsToCaretUpWithPassthroughText() {
        val run = activeRun(deltaDisplay = DeltaDisplayState.fromDeltaMillis(421L))
        val model = HudModel.from(run, readyGps, HudPage.DELTA_ONLY, nowEpochMs = 0L, flashUntilEpochMs = null)

        assertEquals(DeltaCaret.Up, model.deltaCaret)
        assertEquals("+0.421s", model.deltaText)
    }

    // --- D-14: neutral -- pill, identical geometry ----------------------------

    @Test
    fun noReferenceLapYieldsNeutralPillWithNoCaret() {
        val run = activeRun(referenceLapMillis = null, deltaDisplay = DeltaDisplayState.UNAVAILABLE)
        val model = HudModel.from(run, readyGps, HudPage.FOCUSED, nowEpochMs = 0L, flashUntilEpochMs = null)

        assertTrue(model.isNeutralDelta)
        assertEquals(DeltaCaret.None, model.deltaCaret)
        assertEquals("--", model.deltaText)
        // Geometry unaffected: the clock/footer fields are still populated normally.
        assertEquals("1:23.4", model.clockText)
    }

    @Test
    fun neutralToneAloneAlsoMarksNeutralDelta() {
        // referenceLapMillis present but the tone itself reads Neutral (D-14 "or DeltaTone.Neutral").
        val run = activeRun(referenceLapMillis = 88_250L, deltaDisplay = DeltaDisplayState.UNAVAILABLE)
        val model = HudModel.from(run, readyGps, HudPage.FOCUSED, nowEpochMs = 0L, flashUntilEpochMs = null)

        assertTrue(model.isNeutralDelta)
    }

    // --- D-13: idle content when no active run --------------------------------

    @Test
    fun inactiveRunIsIdleRegardlessOfGpsState() {
        val model = HudModel.from(TimingRunSnapshot.inactive(), readyGps, HudPage.FOCUSED, nowEpochMs = 0L, flashUntilEpochMs = null)

        assertTrue(model.isIdle)
        assertFalse(model.isStaleFix)
        assertTrue(model.gpsReady)
    }

    @Test
    fun idleScreenSurfacesGpsNotReadyForWaitingForGpsCopy() {
        val model = HudModel.from(TimingRunSnapshot.inactive(), notReadyGps, HudPage.FOCUSED, nowEpochMs = 0L, flashUntilEpochMs = null)

        assertTrue(model.isIdle)
        assertFalse(model.gpsReady)
    }

    // --- D-15: stale fix keeps the clock advancing, speed/delta collapse -----

    @Test
    fun staleFixCollapsesSpeedAndDeltaButKeepsTheClock() {
        val run = activeRun(currentLapMillis = 45_200L, speedMetersPerSecond = 15.0)
        val model = HudModel.from(run, notReadyGps, HudPage.FOCUSED, nowEpochMs = 0L, flashUntilEpochMs = null)

        assertTrue(model.isStaleFix)
        assertEquals("--", model.speedLabel)
        assertEquals("--", model.deltaText)
        assertEquals(DeltaCaret.None, model.deltaCaret)
        // The clock is untouched by staleness — it reads whatever currentLapMillis carries.
        assertEquals("0:45.2", model.clockText)
    }

    @Test
    fun clockKeepsAdvancingAcrossBeatsWhileStaleAsCurrentLapMillisAdvances() {
        val firstBeat = activeRun(currentLapMillis = 45_200L)
        val secondBeat = activeRun(currentLapMillis = 45_700L)

        val first = HudModel.from(firstBeat, notReadyGps, HudPage.FOCUSED, nowEpochMs = 0L, flashUntilEpochMs = null)
        val second = HudModel.from(secondBeat, notReadyGps, HudPage.FOCUSED, nowEpochMs = 500L, flashUntilEpochMs = null)

        assertTrue(first.isStaleFix && second.isStaleFix)
        assertEquals("0:45.2", first.clockText)
        assertEquals("0:45.7", second.clockText)
    }

    // --- Tenths clock formatting -----------------------------------------------

    @Test
    fun clockFormatsAtTenthsPrecision() {
        assertEquals(
            "1:23.4",
            HudModel.from(activeRun(currentLapMillis = 83_400L), readyGps, HudPage.DELTA_ONLY, 0L, null).clockText,
        )
        assertEquals(
            "0:00.0",
            HudModel.from(activeRun(currentLapMillis = 0L), readyGps, HudPage.DELTA_ONLY, 0L, null).clockText,
        )
        assertEquals(
            "-:--.-",
            HudModel.from(activeRun(currentLapMillis = null), readyGps, HudPage.DELTA_ONLY, 0L, null).clockText,
        )
    }

    // --- D-04: sector-flash window swaps the clock slot, then reverts --------

    @Test
    fun sectorFlashWindowShowsSplitLabelInClockSlot() {
        val run = activeRun(latestSectorName = "S2", latestSectorSplitMillis = 12_300L)
        val model = HudModel.from(run, readyGps, HudPage.FOCUSED, nowEpochMs = 1_000L, flashUntilEpochMs = 2_500L)

        assertTrue(model.isSectorFlash)
        assertEquals("S2 +12.3s", model.clockText)
    }

    @Test
    fun sectorFlashWindowRevertsToRunningClockAfterItEnds() {
        val run = activeRun(currentLapMillis = 83_400L, latestSectorName = "S2", latestSectorSplitMillis = 12_300L)
        val model = HudModel.from(run, readyGps, HudPage.FOCUSED, nowEpochMs = 3_000L, flashUntilEpochMs = 2_500L)

        assertFalse(model.isSectorFlash)
        assertEquals("1:23.4", model.clockText)
    }

    @Test
    fun sectorFlashWindowIgnoredWithoutASplit() {
        val run = activeRun(currentLapMillis = 83_400L, latestSectorName = null, latestSectorSplitMillis = null)
        val model = HudModel.from(run, readyGps, HudPage.FOCUSED, nowEpochMs = 1_000L, flashUntilEpochMs = 2_500L)

        assertFalse(model.isSectorFlash)
        assertEquals("1:23.4", model.clockText)
    }
}
