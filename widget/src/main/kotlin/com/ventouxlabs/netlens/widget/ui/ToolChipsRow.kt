package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.ventouxlabs.netlens.widget.util.Deeplink

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

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(start = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolChip(
                label = "LAN",
                action = actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.DEVICES),
                ),
                background = NetLensWidgetColors.accentSoft,
                onBackground = NetLensWidgetColors.onAccentSoft,
            )
            Spacer(GlanceModifier.width(4.dp))
            ToolChip(
                label = "Ping",
                action = actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.pingHost("8.8.8.8")),
                ),
                background = NetLensWidgetColors.accentSoft,
                onBackground = NetLensWidgetColors.onAccentSoft,
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolChip(
                label = "DNS",
                action = actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.DNS_LEAK),
                ),
                background = NetLensWidgetColors.accentSoft,
                onBackground = NetLensWidgetColors.onAccentSoft,
            )
            Spacer(GlanceModifier.width(4.dp))
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
            fontSize = 14.sp,
            color = onBackground,
        ),
        maxLines = 1,
    )
}
