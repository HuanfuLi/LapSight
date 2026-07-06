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
import kotlinx.coroutines.delay
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
 * MockDeviceKit instrumented proof of MR-01/MR-03 (Phase 7 07-03 Task 3): the
 * bridge reaches `DisplayState.STARTED` and drives the HUD passively — with
 * ZERO input events ever delivered to the mock device. No physical hardware
 * required.
 *
 * Exercises a standalone [GlassesBridge] built directly by the test (not
 * `MainActivity`'s own instance), so this test never contends with a second
 * live DAT session against the same `Wearables` singleton.
 *
 * Caveat (recorded per plan instruction — not fabricated): `mwdat-mockdevice`
 * 0.8.0 exposes no display-content inspection API (`MockGlassesServices` only
 * surfaces `camera`/`captouch`), so "sends content" is verified indirectly —
 * the bridge must reach [GlassesConnectionState.Connected] (only reachable
 * after `DisplayState.STARTED`, the gate [GlassesBridge] uses before calling
 * `sendContent`) and remain there across at least one full ~2 Hz poll beat
 * with no error transitioning it away.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class GlassesLifecycleTest {

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
    fun bridgeReachesDisplayStartedAndDrivesHudWithNoInputEvents() = runBlocking {
        mockDeviceKit.enable()
        val device = mockDeviceKit.pairGlasses(GlassesModel.RAYBAN_META).getOrThrow()
        device.powerOn()
        device.unfold()
        device.don()

        val sessionController = SessionController(store = InMemorySessionStore())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val bridge = GlassesBridge(sessionController, scope)

        try {
            // MR-03: connect, then only the passive poll loop runs. NO button,
            // click, or captouch event is ever sent to `device` in this test.
            bridge.connect(device.deviceIdentifier.toString())

            val connected = withTimeoutOrNull(10_000L) {
                bridge.connectionState.first { it is GlassesConnectionState.Connected }
            }
            assertNotNull("Bridge should reach Connected (DisplayState.STARTED)", connected)

            // Let at least one ~2 Hz render beat elapse purely from the passive loop.
            delay(1_200L)
            assertEquals(GlassesConnectionState.Connected, bridge.connectionState.value)
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
