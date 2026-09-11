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
import androidx.glance.layout.Row
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ventouxlabs.netlens.core.network.VpnState
import com.ventouxlabs.netlens.widget.WidgetState
import com.ventouxlabs.netlens.widget.action.DeeplinkUriKey
import com.ventouxlabs.netlens.widget.action.OpenDeeplinkAction
import com.ventouxlabs.netlens.widget.util.Deeplink

/**
 * DNS/routing status, with a device count and encryption type beneath it.
 *
 * [compact] keeps only the status line. The FULL path's `fillMaxHeight` is a Row-child
 * modifier and safe — see the invariant in [FourByTwoVariant].
 */
@Composable
fun StatusLineContent(
    state: WidgetState,
    compact: Boolean = false,
    modifier: GlanceModifier = GlanceModifier,
) {
    val (statusText, statusColor) = dnsStatusLine(state)
    Column(
        modifier = modifier
            .then(if (compact) GlanceModifier else GlanceModifier.fillMaxHeight())
            .padding(end = 6.dp)
            .clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.DNS),
                ),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = statusText,
            style = TextStyle(
                color = statusColor,
                fontSize = widgetSp(14f),
            ),
            maxLines = 1,
        )

        if (!compact) {
            DeviceEncryptionRow(state = state)
        }
    }
}

/** Which DNS server is in use and how traffic reaches it, with its severity color. */
private fun dnsStatusLine(state: WidgetState): Pair<String, ColorProvider> = when {
    state.isCaptivePortal -> "Captive portal" to NetLensWidgetColors.warn
    state.isDnsLeaking -> "DNS → ${state.primaryDns} · Leak!" to NetLensWidgetColors.warn
    state.vpnState is VpnState.FullTunnel ->
        "DNS → ${state.primaryDns} · VPN routed" to NetLensWidgetColors.accent
    state.vpnState is VpnState.SplitTunnel ->
        "DNS → ${state.primaryDns} · Split" to NetLensWidgetColors.warn
    state.isConnected -> "DNS → ${state.primaryDns} · Direct" to NetLensWidgetColors.inkSoft
    else -> "No network" to NetLensWidgetColors.stamp
}

@Composable
private fun DeviceEncryptionRow(state: WidgetState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val deviceText = "${state.deviceCount} device${if (state.deviceCount != 1) "s" else ""}"
        Text(
            text = deviceText,
            style = TextStyle(
                color = NetLensWidgetColors.inkSoft,
                fontSize = widgetSp(12f),
            ),
            maxLines = 1,
        )
        if (state.encryptionType.isNotEmpty()) {
            val secure = state.isEncryptionSecure
            Text(
                text = " · ${state.encryptionType}",
                style = TextStyle(
                    color = if (secure) NetLensWidgetColors.accent else NetLensWidgetColors.stamp,
                    fontSize = widgetSp(12f),
                ),
                maxLines = 1,
            )
        }
    }
}
