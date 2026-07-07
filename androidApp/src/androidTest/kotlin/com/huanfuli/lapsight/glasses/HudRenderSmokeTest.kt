package com.huanfuli.lapsight.glasses

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.huanfuli.lapsight.glasses.hud.HudRenderer
import com.huanfuli.lapsight.shared.GpsFixStatus
import com.huanfuli.lapsight.shared.glasses.DeltaCaret
import com.huanfuli.lapsight.shared.glasses.HudModel
import com.huanfuli.lapsight.shared.glasses.HudPage
import com.meta.wearable.dat.display.views.ContentScope
import com.meta.wearable.dat.display.views.FlexBoxBackground
import com.meta.wearable.dat.display.views.IconName
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class HudRenderSmokeTest {

    @Test
    fun deltaOnlyRendersDeltaPillAndClock() {
        val tree = renderTree(model(page = HudPage.DELTA_ONLY))

        tree.assertText("-0.218s")
        tree.assertText("1:23.4")
        tree.assertIcon(IconName.CARET_DOWN)
        assertTrue("Delta pill should use CARD background", FlexBoxBackground.CARD.name in tree.backgrounds)
    }

    @Test
    fun focusedRendersHeroPlusLastBestSpeedFooter() {
        val tree = renderTree(model(page = HudPage.FOCUSED))

        tree.assertText("-0.218s")
        tree.assertText("1:23.4")
        tree.assertText("LAST")
        tree.assertText("BEST")
        tree.assertText("SPD")
        tree.assertText("01:30.100")
        tree.assertText("01:28.250")
        tree.assertText("72")
        assertFalse("Focused page should not show lap count", "LAPS" in tree.texts)
    }

    @Test
    fun telemetryAddsLapCount() {
        val tree = renderTree(model(page = HudPage.TELEMETRY, lapCount = 5))

        tree.assertText("LAPS")
        tree.assertText("5")
    }

    @Test
    fun idleScreensRenderWaitingAndReadyStatesDistinctly() {
        val waiting = renderTree(
            model(
                isIdle = true,
                gpsReady = false,
                gpsFixStatus = GpsFixStatus.Idle,
                gpsAccuracyMeters = null,
                gpsSampleRateHz = null,
            ),
        )
        waiting.assertText("WAITING GPS")
        assertFalse("Idle waiting state should not render the warning glyph", IconName.EXCLAMATION_TRIANGLE.value in waiting.icons)
        assertFalse("Idle screen should avoid CARD background on glasses", FlexBoxBackground.CARD.name in waiting.backgrounds)

        val weak = renderTree(
            model(
                isIdle = true,
                gpsReady = false,
                gpsFixStatus = GpsFixStatus.Live,
                gpsAccuracyMeters = 31.0,
                gpsSampleRateHz = 0.7,
            ),
        )
        weak.assertText("GPS WEAK")

        val ready = renderTree(model(isIdle = true, gpsReady = true))
        ready.assertText("READY")
        assertFalse("Idle ready state should not render a top glyph", IconName.CHECKMARK_CIRCLE.value in ready.icons)
    }

    @Test
    fun staleFixShowsGpsGlyphAndDimmedUnavailableReadouts() {
        val tree = renderTree(
            model(
                isStaleFix = true,
                deltaText = "--",
                deltaCaret = DeltaCaret.None,
                speedLabel = "--",
            ),
        )

        tree.assertText("GPS")
        tree.assertIcon(IconName.EXCLAMATION_TRIANGLE)
        tree.assertText("--")
        tree.assertText("1:23.4")
        assertFalse("Stale/neutral delta should not render a caret", tree.icons.contains(IconName.CARET_DOWN.value))
        assertFalse("Stale/neutral delta should not render a caret", tree.icons.contains(IconName.CARET_UP.value))
    }

    @Test
    fun sectorFlashUsesClockSlot() {
        val tree = renderTree(
            model(
                clockText = "S2 +12.3s",
                isSectorFlash = true,
            ),
        )

        tree.assertText("SPLIT")
        tree.assertText("S2 +12.3s")
        assertFalse("Sector flash should not create an always-on sector row", "SECTOR" in tree.texts)
    }

    @Test
    fun slowerDeltaUsesCaretUpAndNeutralDeltaHasNoCaret() {
        val slower = renderTree(
            model(
                deltaText = "+0.421s",
                deltaCaret = DeltaCaret.Up,
            ),
        )
        slower.assertIcon(IconName.CARET_UP)

        val neutral = renderTree(
            model(
                deltaText = "--",
                deltaCaret = DeltaCaret.None,
                isNeutralDelta = true,
            ),
        )
        neutral.assertText("--")
        assertFalse("Neutral delta should not render CARET_DOWN", neutral.icons.contains(IconName.CARET_DOWN.value))
        assertFalse("Neutral delta should not render CARET_UP", neutral.icons.contains(IconName.CARET_UP.value))
    }

    @Test
    fun rootClickHandlerIsAttachedWhenProvided() {
        var clicked = false
        val view = HudRenderer.render(
            scope = ContentScope(),
            model = model(),
            onClick = { clicked = true },
        )
        val root = view.callGetter("root")
        val onClick = root?.callGetter("onClick") as? Function0<*>

        assertNotNull("Root flexBox should carry the clickable fallback handler", onClick)
        onClick?.invoke()
        assertTrue("Root click should invoke the supplied callback", clicked)
    }

    private fun renderTree(model: HudModel): RenderTree {
        val view = HudRenderer.render(ContentScope(), model)
        return collect(view)
    }

    private fun collect(node: Any?): RenderTree {
        if (node == null) return RenderTree()
        return when (node.javaClass.simpleName) {
            "DView" -> collect(node.callGetter("root"))
            "DFlexBox" -> {
                val background = (node.callGetter("background") as? FlexBoxBackground)?.name
                val children = node.callGetter("children") as? List<*> ?: emptyList<Any>()
                children.fold(RenderTree(backgrounds = listOfNotNull(background))) { acc, child ->
                    acc + collect(child)
                }
            }
            "DText" -> RenderTree(texts = listOfNotNull(node.callGetter("content") as? String))
            "DIcon" -> RenderTree(icons = listOfNotNull(node.callGetter("name") as? String))
            "FlexChildWrapper" -> collect(node.callGetter("inner"))
            else -> RenderTree()
        }
    }

    private fun Any.callGetter(property: String): Any? {
        val getter = "get" + property.replaceFirstChar { it.uppercaseChar() }
        return javaClass.methods.firstOrNull { it.name == getter && it.parameterCount == 0 }?.invoke(this)
    }

    private fun model(
        page: HudPage = HudPage.FOCUSED,
        deltaText: String = "-0.218s",
        deltaCaret: DeltaCaret = DeltaCaret.Down,
        clockText: String = "1:23.4",
        isSectorFlash: Boolean = false,
        lastLapLabel: String = "01:30.100",
        bestLapLabel: String = "01:28.250",
        speedLabel: String = "72",
        lapCount: Int = 2,
        isIdle: Boolean = false,
        isStaleFix: Boolean = false,
        isNeutralDelta: Boolean = false,
        gpsReady: Boolean = true,
        gpsFixStatus: GpsFixStatus = GpsFixStatus.Live,
        gpsAccuracyMeters: Double? = 5.0,
        gpsSampleRateHz: Double? = 2.0,
    ) = HudModel(
        page = page,
        deltaText = deltaText,
        deltaCaret = deltaCaret,
        clockText = clockText,
        isSectorFlash = isSectorFlash,
        lastLapLabel = lastLapLabel,
        bestLapLabel = bestLapLabel,
        speedLabel = speedLabel,
        lapCount = lapCount,
        isIdle = isIdle,
        isStaleFix = isStaleFix,
        isNeutralDelta = isNeutralDelta,
        gpsReady = gpsReady,
        gpsFixStatus = gpsFixStatus,
        gpsAccuracyMeters = gpsAccuracyMeters,
        gpsSampleRateHz = gpsSampleRateHz,
    )

    private data class RenderTree(
        val texts: List<String> = emptyList(),
        val icons: List<String> = emptyList(),
        val backgrounds: List<String> = emptyList(),
    ) {
        operator fun plus(other: RenderTree): RenderTree = RenderTree(
            texts = texts + other.texts,
            icons = icons + other.icons,
            backgrounds = backgrounds + other.backgrounds,
        )

        fun assertText(value: String) {
            assertTrue("Expected text '$value' in $texts", value in texts)
        }

        fun assertIcon(name: IconName) {
            assertTrue("Expected icon '${name.value}' in $icons", name.value in icons)
        }
    }
}
