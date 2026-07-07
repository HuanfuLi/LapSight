package com.huanfuli.lapsight.glasses

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.huanfuli.lapsight.shared.glasses.HudPage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 07-06 fallback coverage. DAT 0.8 exposes MockCaptouchKit simulation, but no
 * public real-device raw captouch receive Flow. We therefore route only the
 * documented Display clickable callback here; raw tap-and-hold stays ignored
 * until a real API exists.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class CaptouchInputTest {

    @Test
    fun displayClickCyclesHudPagesWithWraparound() {
        var page = HudPage.DELTA_ONLY
        val input = GlassesInput(
            currentPage = { page },
            selectPage = { page = it },
        )

        input.handle(GlassesInputAction.DisplayClick)
        assertEquals(HudPage.FOCUSED, page)

        input.handle(GlassesInputAction.DisplayClick)
        assertEquals(HudPage.TELEMETRY, page)

        input.handle(GlassesInputAction.DisplayClick)
        assertEquals(HudPage.DELTA_ONLY, page)
    }

    @Test
    fun unsupportedAndUnknownEventsAreIgnored() {
        var page = HudPage.FOCUSED
        val input = GlassesInput(
            currentPage = { page },
            selectPage = { page = it },
        )

        input.handle(GlassesInputAction.UnsupportedTapAndHold)
        input.handle(GlassesInputAction.Unknown)

        assertEquals(HudPage.FOCUSED, page)
    }
}
