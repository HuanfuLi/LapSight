package com.huanfuli.lapsight.shared

enum class SpeedUnit {
    KilometersPerHour,
    MilesPerHour,
}

enum class ThemeMode {
    System,
    Dark,
    Light,
}

enum class LocationFeedMode {
    PhoneGps,
    Simulated,
}

data class PhoneGpsPermissionState(
    val isSupported: Boolean = false,
    val isGranted: Boolean = false,
    val requestPermission: () -> Unit = {},
)

data class DriveDisplaySettings(
    val speedUnit: SpeedUnit = SpeedUnit.KilometersPerHour,
    val fullscreenWhileTiming: Boolean = true,
    val keepScreenAwakeWhileTiming: Boolean = true,
    val showSpeedTrace: Boolean = true,
    val showGpsDiagnostics: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.System,
    val locationFeedMode: LocationFeedMode = LocationFeedMode.Simulated,
)

interface DisplaySettingsStore {
    fun load(): DriveDisplaySettings
    fun save(settings: DriveDisplaySettings)
}

class InMemoryDisplaySettingsStore(
    initial: DriveDisplaySettings = DriveDisplaySettings(),
) : DisplaySettingsStore {
    private var settings = initial

    override fun load(): DriveDisplaySettings = settings

    override fun save(settings: DriveDisplaySettings) {
        this.settings = settings
    }
}

interface DriveDisplayController {
    fun apply(fullscreen: Boolean, keepScreenAwake: Boolean)
}

object NoOpDriveDisplayController : DriveDisplayController {
    override fun apply(fullscreen: Boolean, keepScreenAwake: Boolean) = Unit
}
