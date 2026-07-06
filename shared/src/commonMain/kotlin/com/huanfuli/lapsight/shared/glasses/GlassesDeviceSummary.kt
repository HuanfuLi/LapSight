package com.huanfuli.lapsight.shared.glasses

/**
 * Platform-free device-summary DTO (Phase 7 MR-01) for the shared-UI device
 * picker (07-05). Imports zero `com.meta.wearable.*` types.
 *
 * @property id an OPAQUE device-identifier string minted by the Android
 *   `GlassesBridge` from the DAT `DeviceIdentifier` (its `identifier` string —
 *   round-trippable via `DeviceIdentifier(id)`). Shared code never constructs
 *   or interprets this value; it only passes it back to
 *   `GlassesActions.pickDevice(id)` (07-05), which the bridge maps to a
 *   `SpecificDeviceSelector` for `Wearables.createSession(...)`.
 * @property name the device's display name (`Device.name`).
 * @property type a human-readable device type (`Device.deviceType.description`).
 * @property isDisplayCapable whether the device supports the Display capability
 *   (`Device.isDisplayCapable()`) — the shared-UI picker uses this to gate
 *   selection.
 * @property requiresFirmwareUpdate true when the SDK reports the device itself
 *   must be updated before a display session can be used.
 */
data class GlassesDeviceSummary(
    val id: String,
    val name: String,
    val type: String,
    val isDisplayCapable: Boolean,
    val requiresFirmwareUpdate: Boolean = false,
)
