package com.huanfuli.lapsight.glasses

import com.huanfuli.lapsight.shared.glasses.HudPage

/**
 * Routes glasses-originated input that is confirmed in the public DAT 0.8 API.
 *
 * Raw temple captouch receive events are not exposed publicly in DAT 0.8. The
 * supported fallback is Display clickable content (`flexBox`/`button` onClick),
 * which is enough for page cycling but not enough to safely implement
 * tap-and-hold start/stop.
 */
class GlassesInput(
    private val currentPage: () -> HudPage,
    private val selectPage: (HudPage) -> Unit,
) {
    fun handle(action: GlassesInputAction) {
        when (action) {
            GlassesInputAction.DisplayClick -> selectPage(currentPage().next())
            GlassesInputAction.UnsupportedTapAndHold,
            GlassesInputAction.Unknown,
            -> Unit
        }
    }

    private fun HudPage.next(): HudPage {
        val pages = HudPage.entries
        return pages[(ordinal + 1) % pages.size]
    }
}

enum class GlassesInputAction {
    /** Documented DAT Display clickable fallback. */
    DisplayClick,

    /** Reserved until a real raw captouch receive API is confirmed. */
    UnsupportedTapAndHold,

    /** Defensive bucket for future/unknown events. */
    Unknown,
}
