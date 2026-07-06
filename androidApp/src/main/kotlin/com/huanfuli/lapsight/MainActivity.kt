package com.huanfuli.lapsight

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.tooling.preview.Preview
import com.huanfuli.lapsight.shared.App
import com.huanfuli.lapsight.shared.DashOrientation
import com.huanfuli.lapsight.shared.DisplaySettingsStore
import com.huanfuli.lapsight.shared.DriveDisplayController
import com.huanfuli.lapsight.shared.DriveDisplaySettings
import com.huanfuli.lapsight.shared.LanguageMode
import com.huanfuli.lapsight.shared.LocationFeedMode
import com.huanfuli.lapsight.shared.OrientationController
import com.huanfuli.lapsight.shared.PhoneGpsPermissionState
import com.huanfuli.lapsight.shared.SpeedUnit
import com.huanfuli.lapsight.shared.ThemeMode
import com.huanfuli.lapsight.shared.export.AndroidExportShareTarget
import com.huanfuli.lapsight.shared.storage.StoragePaths

class MainActivity : ComponentActivity() {
    private val fineLocationPermissionGranted = mutableStateOf(false)
    private var phoneGpsProvider: AndroidPhoneLocationProvider? = null

    private val requestLocationPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            fineLocationPermissionGranted.value = hasFineLocationPermission()
        }

    // Locks the window to the user's chosen orientation using fixed
    // (sensor-independent) values. Never SENSOR_*/USER_* — a mounted phone must
    // not rotate from accelerometer input under racing G-forces.
    private val orientationController = object : OrientationController {
        override fun apply(orientation: DashOrientation) {
            requestedOrientation = when (orientation) {
                DashOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                DashOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
    }

    private val driveDisplayController = object : DriveDisplayController {
        override fun apply(fullscreen: Boolean, keepScreenAwake: Boolean) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { insets ->
                    if (fullscreen) {
                        insets.systemBarsBehavior =
                            android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        insets.hide(WindowInsets.Type.systemBars())
                    } else {
                        insets.show(WindowInsets.Type.systemBars())
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (fullscreen) {
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                } else {
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                }
            }
            if (keepScreenAwake) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        fineLocationPermissionGranted.value = hasFineLocationPermission()

        // Wire the app-private storage root before any save/load access (D-21).
        StoragePaths.initialize(this)

        val shareTarget = AndroidExportShareTarget(this)
        val displaySettingsStore = AndroidDisplaySettingsStore(this)
        phoneGpsProvider = AndroidPhoneLocationProvider(
            context = this,
            hasFineLocationPermission = { hasFineLocationPermission() },
            // Read fresh each feed start so a settings toggle applies on next start.
            useDirectGnss = { displaySettingsStore.load().useDirectGnss },
        )

        setContent {
            App(
                orientationController = orientationController,
                driveDisplayController = driveDisplayController,
                displaySettingsStore = displaySettingsStore,
                phoneGpsProvider = phoneGpsProvider,
                phoneGpsPermission = PhoneGpsPermissionState(
                    isSupported = true,
                    isGranted = fineLocationPermissionGranted.value,
                    requestPermission = {
                        requestLocationPermissions.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                ),
                sessionStore = StoragePaths.fileSessionStore(),
                exportShareTarget = shareTarget,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        fineLocationPermissionGranted.value = hasFineLocationPermission()
    }

    override fun onDestroy() {
        phoneGpsProvider?.stop()
        super.onDestroy()
    }

    private fun hasFineLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}

private class AndroidDisplaySettingsStore(
    activity: ComponentActivity,
) : DisplaySettingsStore {
    private val preferences = activity.getSharedPreferences("display_settings", Context.MODE_PRIVATE)

    override fun load(): DriveDisplaySettings = DriveDisplaySettings(
        speedUnit = runCatching {
            SpeedUnit.valueOf(
                preferences.getString("speed_unit", SpeedUnit.KilometersPerHour.name)
                    ?: SpeedUnit.KilometersPerHour.name,
            )
        }.getOrDefault(SpeedUnit.KilometersPerHour),
        fullscreenWhileTiming = preferences.getBoolean("fullscreen_while_timing", true),
        keepScreenAwakeWhileTiming = preferences.getBoolean("keep_screen_awake", true),
        showSpeedTrace = preferences.getBoolean("show_speed_trace", true),
        showGpsDiagnostics = preferences.getBoolean("show_gps_diagnostics", true),
        themeMode = runCatching {
            ThemeMode.valueOf(
                preferences.getString("theme_mode", ThemeMode.System.name)
                    ?: ThemeMode.System.name,
            )
        }.getOrDefault(ThemeMode.System),
        languageMode = runCatching {
            LanguageMode.valueOf(
                preferences.getString("language_mode", LanguageMode.System.name)
                    ?: LanguageMode.System.name,
            )
        }.getOrDefault(LanguageMode.System),
        locationFeedMode = runCatching {
            LocationFeedMode.valueOf(
                preferences.getString("location_feed_mode", LocationFeedMode.PhoneGps.name)
                    ?: LocationFeedMode.PhoneGps.name,
            )
        }.getOrDefault(LocationFeedMode.PhoneGps),
        useDirectGnss = preferences.getBoolean("use_direct_gnss", false),
    )

    override fun save(settings: DriveDisplaySettings) {
        preferences.edit()
            .putString("speed_unit", settings.speedUnit.name)
            .putBoolean("fullscreen_while_timing", settings.fullscreenWhileTiming)
            .putBoolean("keep_screen_awake", settings.keepScreenAwakeWhileTiming)
            .putBoolean("show_speed_trace", settings.showSpeedTrace)
            .putBoolean("show_gps_diagnostics", settings.showGpsDiagnostics)
            .putString("theme_mode", settings.themeMode.name)
            .putString("language_mode", settings.languageMode.name)
            .putString("location_feed_mode", settings.locationFeedMode.name)
            .putBoolean("use_direct_gnss", settings.useDirectGnss)
            .apply()
    }
}
