package com.huanfuli.lapsight.shared

import platform.Foundation.NSUserDefaults

class IosDisplaySettingsStore : DisplaySettingsStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun load(): DriveDisplaySettings = DriveDisplaySettings(
        speedUnit = defaults.stringForKey(SPEED_UNIT)
            ?.let { runCatching { SpeedUnit.valueOf(it) }.getOrNull() }
            ?: SpeedUnit.KilometersPerHour,
        fullscreenWhileTiming = boolean(FULLSCREEN_WHILE_TIMING, default = true),
        keepScreenAwakeWhileTiming = boolean(KEEP_SCREEN_AWAKE, default = true),
        showSpeedTrace = boolean(SHOW_SPEED_TRACE, default = true),
        showGpsDiagnostics = boolean(SHOW_GPS_DIAGNOSTICS, default = true),
        themeMode = defaults.stringForKey(THEME_MODE)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.System,
    )

    override fun save(settings: DriveDisplaySettings) {
        defaults.setObject(settings.speedUnit.name, forKey = SPEED_UNIT)
        defaults.setBool(settings.fullscreenWhileTiming, forKey = FULLSCREEN_WHILE_TIMING)
        defaults.setBool(settings.keepScreenAwakeWhileTiming, forKey = KEEP_SCREEN_AWAKE)
        defaults.setBool(settings.showSpeedTrace, forKey = SHOW_SPEED_TRACE)
        defaults.setBool(settings.showGpsDiagnostics, forKey = SHOW_GPS_DIAGNOSTICS)
        defaults.setObject(settings.themeMode.name, forKey = THEME_MODE)
    }

    private fun boolean(key: String, default: Boolean): Boolean =
        if (defaults.objectForKey(key) == null) default else defaults.boolForKey(key)

    private companion object {
        const val SPEED_UNIT = "display.speedUnit"
        const val FULLSCREEN_WHILE_TIMING = "display.fullscreenWhileTiming"
        const val KEEP_SCREEN_AWAKE = "display.keepScreenAwake"
        const val SHOW_SPEED_TRACE = "display.showSpeedTrace"
        const val SHOW_GPS_DIAGNOSTICS = "display.showGpsDiagnostics"
        const val THEME_MODE = "display.themeMode"
    }
}
