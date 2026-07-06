package com.huanfuli.lapsight.shared.glasses

/**
 * Platform-free glasses connection state (Phase 7 MR-01), crossing the KMP seam
 * from the Android-only `GlassesBridge` (androidApp) into shared UI (07-05's
 * Settings/Drive screens). Imports zero `com.meta.wearable.*`/Compose types —
 * mirrors the repo's `ReadyState`/`StartTimingResult` sealed-state idiom: never
 * a bare Boolean "connected" flag.
 *
 * `androidApp`'s `GlassesBridge` owns the mapping from DAT `DeviceSessionState`/
 * `DisplayState` into this sealed interface; `shared` never sees a DAT type.
 */
sealed interface GlassesConnectionState {
    /** No session has been requested yet. */
    data object Idle : GlassesConnectionState

    /** Session/display creation is in flight (createSession -> STARTED -> addDisplay -> STARTED). */
    data object Connecting : GlassesConnectionState

    /** Display is attached and `DisplayState.STARTED`; the render loop may push content. */
    data object Connected : GlassesConnectionState

    /**
     * A mid-session disconnect is being silently recovered (D-11): phone timing
     * (`SessionController`) is unaffected while the bridge recreates the session
     * in the background. The UI (07-05) surfaces this ONLY as a non-blocking
     * status chip — never a mid-drive alert.
     */
    data class Reconnecting(val reason: String? = null) : GlassesConnectionState

    /** A failure that needs user action (e.g. no eligible device, create-session failure). */
    data class Error(val message: String) : GlassesConnectionState
}
