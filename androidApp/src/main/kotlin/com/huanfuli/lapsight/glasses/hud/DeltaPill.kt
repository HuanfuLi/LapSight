package com.huanfuli.lapsight.glasses.hud

import com.huanfuli.lapsight.shared.glasses.DeltaCaret
import com.huanfuli.lapsight.shared.glasses.HudModel
import com.meta.wearable.dat.display.views.Alignment
import com.meta.wearable.dat.display.views.Direction
import com.meta.wearable.dat.display.views.FlexBoxBackground
import com.meta.wearable.dat.display.views.FlexBoxScope
import com.meta.wearable.dat.display.views.IconName
import com.meta.wearable.dat.display.views.IconStyle
import com.meta.wearable.dat.display.views.TextColor
import com.meta.wearable.dat.display.views.TextStyle

/**
 * Delta card for the DAT display HUD.
 *
 * The SDK exposes no arbitrary hue palette, so the ahead/behind signal is:
 * CARD shape + caret direction + already-signed delta text. Neutral and stale
 * states keep the same CARD container and render `--` with no icon.
 */
object DeltaPill {
    fun render(
        scope: FlexBoxScope,
        model: HudModel,
        textStyle: TextStyle = TextStyle.HEADING,
    ) = with(scope) {
        flexBox(
            direction = Direction.ROW,
            gap = 6,
            padding = 12,
            alignment = Alignment.CENTER,
            crossAlignment = Alignment.CENTER,
            background = FlexBoxBackground.CARD,
        ) {
            when (model.deltaCaret) {
                DeltaCaret.Down -> icon(
                    name = IconName.CARET_DOWN,
                    style = IconStyle.FILLED,
                )
                DeltaCaret.Up -> icon(
                    name = IconName.CARET_UP,
                    style = IconStyle.FILLED,
                )
                DeltaCaret.None -> Unit
            }
            text(
                model.deltaText,
                style = textStyle,
                color = model.deltaTextColor(),
            )
        }
    }

    private fun HudModel.deltaTextColor(): TextColor = when (deltaCaret) {
        DeltaCaret.Down -> TextColor.PRIMARY
        DeltaCaret.Up -> TextColor.SECONDARY
        DeltaCaret.None -> TextColor.SECONDARY
    }
}
