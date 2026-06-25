package com.huanfuli.lapsight

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.huanfuli.lapsight.shared.App
import com.huanfuli.lapsight.shared.DashOrientation
import com.huanfuli.lapsight.shared.OrientationController

class MainActivity : ComponentActivity() {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(orientationController)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
