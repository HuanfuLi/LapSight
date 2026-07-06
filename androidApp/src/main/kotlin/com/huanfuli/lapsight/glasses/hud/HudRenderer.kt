package com.huanfuli.lapsight.glasses.hud

import com.huanfuli.lapsight.shared.glasses.HudModel
import com.huanfuli.lapsight.shared.glasses.HudPage
import com.meta.wearable.dat.display.views.Alignment
import com.meta.wearable.dat.display.views.ContentScope
import com.meta.wearable.dat.display.views.Direction
import com.meta.wearable.dat.display.views.DisplayComponent
import com.meta.wearable.dat.display.views.FlexBoxBackground
import com.meta.wearable.dat.display.views.FlexBoxScope
import com.meta.wearable.dat.display.views.IconName
import com.meta.wearable.dat.display.views.IconStyle
import com.meta.wearable.dat.display.views.TextColor
import com.meta.wearable.dat.display.views.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Maps the pure shared [HudModel] to one complete Meta DAT display tree. */
object HudRenderer {
    fun render(scope: ContentScope, model: HudModel): DisplayComponent = with(scope) {
        flexBox(
            direction = Direction.COLUMN,
            gap = 12,
            padding = 24,
            alignment = Alignment.CENTER,
            crossAlignment = Alignment.STRETCH,
        ) {
            if (model.isIdle) {
                idleScreen(model)
            } else {
                timingScreen(model)
            }
        }
    }

    private fun FlexBoxScope.idleScreen(model: HudModel) {
        flexBox(
            direction = Direction.COLUMN,
            gap = 10,
            padding = 24,
            alignment = Alignment.CENTER,
            crossAlignment = Alignment.CENTER,
            background = FlexBoxBackground.CARD,
        ) {
            icon(
                name = if (model.gpsReady) IconName.CHECKMARK_CIRCLE else IconName.EXCLAMATION_TRIANGLE,
                style = IconStyle.FILLED,
            )
            text(
                if (model.gpsReady) "Ready — start timing" else "Waiting for GPS",
                style = TextStyle.HEADING,
            )
            text(
                model.gpsQualityLabel(),
                style = TextStyle.BODY,
                color = TextColor.SECONDARY,
            )
        }
    }

    private fun FlexBoxScope.timingScreen(model: HudModel) {
        if (model.isStaleFix) {
            gpsWarning()
        }

        when (model.page) {
            HudPage.DELTA_ONLY -> deltaOnly(model)
            HudPage.FOCUSED -> focused(model, includeLapCount = false)
            HudPage.TELEMETRY -> focused(model, includeLapCount = true)
        }
    }

    private fun FlexBoxScope.deltaOnly(model: HudModel) {
        DeltaPill.render(this, model)
        clockCard(model)
    }

    private fun FlexBoxScope.focused(
        model: HudModel,
        includeLapCount: Boolean,
    ) {
        flexBox(
            direction = Direction.ROW,
            gap = 10,
            alignment = Alignment.CENTER,
            crossAlignment = Alignment.STRETCH,
        ) {
            flexBox(
                direction = Direction.COLUMN,
                flexGrow = 1f,
                alignment = Alignment.CENTER,
                crossAlignment = Alignment.CENTER,
            ) {
                DeltaPill.render(this, model)
            }
            flexBox(
                direction = Direction.COLUMN,
                flexGrow = 1f,
                alignment = Alignment.CENTER,
                crossAlignment = Alignment.CENTER,
            ) {
                clockCard(model)
            }
        }
        footer(model, includeLapCount)
    }

    private fun FlexBoxScope.clockCard(model: HudModel) {
        flexBox(
            direction = Direction.COLUMN,
            gap = 4,
            padding = 12,
            alignment = Alignment.CENTER,
            crossAlignment = Alignment.CENTER,
            background = FlexBoxBackground.CARD,
        ) {
            text(
                if (model.isSectorFlash) "SPLIT" else "LAP",
                style = TextStyle.META,
                color = TextColor.SECONDARY,
            )
            text(model.clockText, style = TextStyle.HEADING)
        }
    }

    private fun FlexBoxScope.footer(
        model: HudModel,
        includeLapCount: Boolean,
    ) {
        flexBox(
            direction = Direction.ROW,
            gap = 8,
            alignment = Alignment.CENTER,
            crossAlignment = Alignment.CENTER,
            wrap = true,
        ) {
            metric("LAST", model.lastLapLabel)
            metric("BEST", model.bestLapLabel)
            metric("SPD", model.speedLabel)
            if (includeLapCount) {
                metric("LAPS", model.lapCount.toString())
            }
        }
    }

    private fun FlexBoxScope.metric(label: String, value: String) {
        flexBox(
            direction = Direction.COLUMN,
            gap = 2,
            padding = 8,
            flexGrow = 1f,
            alignment = Alignment.CENTER,
            crossAlignment = Alignment.CENTER,
        ) {
            text(label, style = TextStyle.META, color = TextColor.SECONDARY)
            text(value, style = TextStyle.BODY)
        }
    }

    private fun FlexBoxScope.gpsWarning() {
        flexBox(
            direction = Direction.ROW,
            gap = 6,
            alignment = Alignment.CENTER,
            crossAlignment = Alignment.CENTER,
        ) {
            icon(name = IconName.EXCLAMATION_TRIANGLE, style = IconStyle.FILLED)
            text("GPS", style = TextStyle.META, color = TextColor.SECONDARY)
        }
    }

    private fun HudModel.gpsQualityLabel(): String {
        val accuracy = gpsAccuracyMeters?.takeIf { it.isFinite() && it >= 0.0 }
            ?.roundToInt()
            ?.let { "${it}m" }
            ?: "--m"
        val rate = gpsSampleRateHz?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let { String.format(Locale.US, "%.1fHz", it) }
            ?: "--Hz"
        return "$accuracy / $rate"
    }
}
