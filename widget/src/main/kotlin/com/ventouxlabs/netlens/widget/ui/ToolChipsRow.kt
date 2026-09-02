package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ventouxlabs.netlens.widget.WidgetState
import com.ventouxlabs.netlens.widget.action.DeeplinkUriKey
import com.ventouxlabs.netlens.widget.action.OpenDeeplinkAction
import com.ventouxlabs.netlens.widget.action.OpenPortalAction
import com.ventouxlabs.netlens.widget.util.ChipCatalog
import com.ventouxlabs.netlens.widget.util.ChipDefinition
import com.ventouxlabs.netlens.widget.util.Deeplink

/**
 * Resolves [chipRoutes] against [ChipCatalog.ELIGIBLE], always in the catalog's declaration
 * order — never the input list's order — so the chip row stays visually stable across periodic
 * widget refreshes regardless of the `Set<String>` iteration order the routes were collected
 * from. Capped at [ChipCatalog.MAX_WIDGET_CHIPS].
 */
internal fun resolveToolChips(chipRoutes: List<String>): List<ChipDefinition> {
    val selected = chipRoutes.toSet()
    return ChipCatalog.ELIGIBLE
        .filter { it.route in selected }
        .take(ChipCatalog.MAX_WIDGET_CHIPS)
}

@Composable
fun ToolChipsRow(state: WidgetState, modifier: GlanceModifier = GlanceModifier) {
    val portalBackground = if (state.isCaptivePortal) {
        NetLensWidgetColors.warnSoft
    } else {
        NetLensWidgetColors.accentSoft
    }
    val portalOnBackground = if (state.isCaptivePortal) {
        NetLensWidgetColors.onWarnSoft
    } else {
        NetLensWidgetColors.onAccentSoft
    }

    val chipRows = resolveToolChips(state.chipRoutes).chunked(2)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(start = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chipRows.forEachIndexed { rowIndex, rowChips ->
            if (rowIndex > 0) Spacer(GlanceModifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                rowChips.forEachIndexed { index, chip ->
                    if (index > 0) Spacer(GlanceModifier.width(4.dp))
                    ToolChip(
                        label = chip.shortLabel,
                        action = actionRunCallback<OpenDeeplinkAction>(
                            actionParametersOf(DeeplinkUriKey to Deeplink.forRoute(chip.route)),
                        ),
                        background = NetLensWidgetColors.accentSoft,
                        onBackground = NetLensWidgetColors.onAccentSoft,
                    )
                }
            }
        }
        if (chipRows.isNotEmpty()) Spacer(GlanceModifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolChip(
                label = "Portal",
                action = actionRunCallback<OpenPortalAction>(),
                background = portalBackground,
                onBackground = portalOnBackground,
            )
        }
    }
}

@Composable
private fun ToolChip(
    label: String,
    action: Action,
    background: ColorProvider,
    onBackground: ColorProvider,
) {
    Text(
        text = label,
        modifier = GlanceModifier
            .cornerRadius(4.dp)
            .background(background)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clickable(action),
        style = TextStyle(
            fontSize = widgetSp(14f),
            color = onBackground,
        ),
        maxLines = 1,
    )
}
