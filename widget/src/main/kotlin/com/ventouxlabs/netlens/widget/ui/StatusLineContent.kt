package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ventouxlabs.netlens.core.network.VpnState
import com.ventouxlabs.netlens.widget.WidgetState
import com.ventouxlabs.netlens.widget.action.DeeplinkUriKey
import com.ventouxlabs.netlens.widget.action.OpenDeeplinkAction
import com.ventouxlabs.netlens.widget.util.Deeplink
import com.ventouxlabs.netlens.widget.util.relativeTimeLabel

@Composable
fun StatusLineContent(state: WidgetState, modifier: GlanceModifier = GlanceModifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(end = 6.dp)
            .clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.DNS),
                ),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (statusText, statusColor) = when {
            state.isCaptivePortal -> "Captive portal" to NetLensWidgetColors.warn
            state.isDnsLeaking -> "DNS → ${state.primaryDns} · Leak!" to NetLensWidgetColors.warn
            state.vpnState is VpnState.FullTunnel ->
                "DNS → ${state.primaryDns} · VPN routed" to NetLensWidgetColors.accent
            state.vpnState is VpnState.SplitTunnel ->
                "DNS → ${state.primaryDns} · Split" to NetLensWidgetColors.warn
            state.isConnected -> "DNS → ${state.primaryDns} · Direct" to NetLensWidgetColors.inkSoft
            else -> "No network" to NetLensWidgetColors.stamp
        }
        Text(
            text = statusText,
            style = TextStyle(
                color = statusColor,
                fontSize = widgetSp(14f),
            ),
            maxLines = 1,
        )

        val elapsed = relativeTimeLabel(state.lastRefreshMs)
        if (elapsed.isNotEmpty()) {
            val stale = state.lastRefreshMs > 0L &&
                System.currentTimeMillis() - state.lastRefreshMs > WidgetState.STALE_ALERT_THRESHOLD_MS
            Text(
                text = "Scanned $elapsed",
                style = TextStyle(
                    color = if (stale) NetLensWidgetColors.warn else NetLensWidgetColors.inkSoft,
                    fontSize = widgetSp(11f),
                ),
            )
        }
    }
}
