package com.ventoux.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.unit.ColorProvider
import com.ventoux.netlens.widget.WidgetState
import com.ventoux.netlens.widget.action.DeeplinkUriKey
import com.ventoux.netlens.widget.action.OpenDeeplinkAction
import com.ventoux.netlens.widget.action.TriggerScanAction
import com.ventoux.netlens.widget.util.Deeplink

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
            state.isCaptivePortal -> "Captive portal" to WidgetTheme.CAPTIVE_ORANGE
            state.isDnsLeaking -> "DNS → ${state.primaryDns} · Leak!" to WidgetTheme.SCORE_AMBER
            state.vpnActive && state.routingMode == "VPN_FULL" ->
                "DNS → ${state.primaryDns} · VPN routed" to WidgetTheme.VPN_GREEN

            state.vpnActive -> "DNS → ${state.primaryDns} · Split" to WidgetTheme.TEXT_SECONDARY
            state.isConnected -> "DNS → ${state.primaryDns} · Direct" to WidgetTheme.TEXT_SECONDARY
            else -> "No network" to WidgetTheme.SCORE_RED
        }
        Text(
            text = statusText,
            style = TextStyle(
                color = ColorProvider(statusColor),
                fontSize = 11.sp,
            ),
            maxLines = 1,
        )

        val elapsed = WidgetTheme.relativeTime(state.lastRefreshMs)
        if (elapsed.isNotEmpty()) {
            val stale = state.lastRefreshMs > 0L &&
                System.currentTimeMillis() - state.lastRefreshMs > 10 * 60 * 1000L
            Text(
                text = "Scanned $elapsed",
                modifier = GlanceModifier.clickable(actionRunCallback<TriggerScanAction>()),
                style = TextStyle(
                    color = ColorProvider(
                        if (stale) WidgetTheme.CAPTIVE_ORANGE else WidgetTheme.TEXT_MUTED,
                    ),
                    fontSize = 9.sp,
                ),
            )
        }
    }
}
