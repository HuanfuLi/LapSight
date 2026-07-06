package com.huanfuli.lapsight.shared.glasses

/**
 * The three selectable HUD pages on the Meta glasses display (D-01, Phase 7 MR-02).
 *
 * Cycle order is the enum ordinal: a phone-side page selector or (hardware-gated,
 * D-07) captouch tap advances `DELTA_ONLY -> FOCUSED -> TELEMETRY -> DELTA_ONLY`.
 */
enum class HudPage {
    /** Delta pill + current-lap clock only — the hot-lap glance (D-01). */
    DELTA_ONLY,

    /**
     * Driving default (D-01/D-02): delta pill + current-lap hero, co-equal, with
     * a small footer of last / best / speed.
     */
    FOCUSED,

    /** [FOCUSED] plus a lap-count readout (D-01). */
    TELEMETRY,
}
