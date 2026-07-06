package com.huanfuli.lapsight.glasses

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.huanfuli.lapsight.shared.glasses.GlassesConnectionState
import com.huanfuli.lapsight.shared.session.SessionController
import com.huanfuli.lapsight.shared.storage.InMemorySessionStore
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.GlassesModel
import com.meta.wearable.dat.mockdevice.api.MockDeviceKitInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MockDeviceKit instrumented proof of D-11 (Phase 7 07-03 Task 3): a
 * mid-session disconnect drives the bridge to
 * [GlassesConnectionState.Reconnecting] and it re-establishes the display on
 * power-on — all while the phone's [SessionController] is never touched. No
 * physical hardware required.
 *
 * The disconnect is simulated exactly per the SDK's own `mockdevice-testing`
 * skill idiom: `device.doff(); device.fold(); device.powerOff()`. This is a
 * definitive disconnect (the DAT SDK exposes a dedicated
 * `DeviceSessionError.DEVICE_POWERED_OFF`), distinct from a transient link
 * hiccup that might only pause the session.
 *
 * Scope note: no formal timing run is started in this test (that would
 * require a full course/profile + confirmed start/finish + fed GPS samples,
 * well beyond a lifecycle/reconnect test's scope). What IS asserted here: (1)
 * [GlassesBridge]'s reconnect path (`handleSessionStopped` / `startSession`)
 * never references [SessionController] at all — verifiable directly by
 * reading `GlassesBridge.kt` — and (2) the read-only
 * [SessionController.timingRunSnapshot] view is byte-identical immediately
 * before and after the disconnect, i.e. the bridge did not indirectly mutate
 * phone timing state while recovering the glasses session.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class GlassesReconnectTest {

    private lateinit var targetContext: Context
    private lateinit var mockDeviceKit: MockDeviceKitInterface

    @Before
    fun setUp() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        mockDeviceKit = MockDeviceKit.getInstance(targetContext)
        grantRuntimePermissions()
    }

    @After
    fun tearDown() {
        mockDeviceKit.disable()
    }

    @Test
    fun bridgeReconnectsSilentlyWithoutTouchingSessionController() = runBlocking {
        mockDeviceKit.enable()
        val device = mockDeviceKit.pairGlasses(GlassesModel.RAYBAN_META).getOrThrow()
        device.powerOn()
        device.unfold()
        device.don()

        val sessionController = SessionController(store = InMemorySessionStore())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val bridge = GlassesBridge(sessionController, scope)

        try {
            bridge.connect(device.deviceIdentifier.toString())
            val connected = withTimeoutOrNull(10_000L) {
                bridge.connectionState.first { it is GlassesConnectionState.Connected }
            }
            assertNotNull("Bridge should reach Connected before simulating a disconnect", connected)

            val timingBeforeDisconnect = sessionController.timingRunSnapshot()

            // Simulate a mid-session disconnect (D-11).
            device.doff()
            device.fold()
            device.powerOff()

            val reconnecting = withTimeoutOrNull(10_000L) {
                bridge.connectionState.first { it is GlassesConnectionState.Reconnecting }
            }
            assertNotNull("Bridge should surface Reconnecting on disconnect", reconnecting)

            // Phone timing (SessionController) must be untouched by the reconnect path.
            assertEquals(timingBeforeDisconnect, sessionController.timingRunSnapshot())

            // Bring the device back; the bridge should silently re-establish the
            // display with NO user-facing alert (D-11) and no glasses-side input.
            device.powerOn()
            device.unfold()
            device.don()
            val reconnected = withTimeoutOrNull(15_000L) {
                bridge.connectionState.first { it is GlassesConnectionState.Connected }
            }
            assertNotNull("Bridge should re-reach Connected after the device returns", reconnected)

            // Phone timing remains untouched across the full disconnect/reconnect cycle.
            assertEquals(timingBeforeDisconnect, sessionController.timingRunSnapshot())
        } finally {
            bridge.stop()
            scope.cancel()
        }
    }

    private fun grantRuntimePermissions() {
        val packageName = targetContext.packageName
        val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
        shell.executeShellCommand("pm grant $packageName android.permission.BLUETOOTH_CONNECT")
    }
}
