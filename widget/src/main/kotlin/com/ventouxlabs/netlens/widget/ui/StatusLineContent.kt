package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
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
 * Which DNS resolver is in use and how traffic reaches it — one line, opening DNS.
 *
 * It was a Column holding this line with the device count and encryption beneath it, and
 * a `compact` flag that dropped the second half. Both 4x2 variants now place the two
 * separately — FULL gives the detail its own full-width row ([FourByTwoDetailRow]),
 * COMPACT drops it — so the wrapper and the flag are gone and this is the line itself.
 *
 * Callers give it a width: it is a weighted child in both of its rows, and the one thing
 * on those rows that may ellipsize. It asks for no height. See the invariant in
 * [FourByTwoVariant].
 */
@Composable
fun StatusLineContent(state: WidgetState, modifier: GlanceModifier = GlanceModifier) {
    val (statusText, statusColor) = dnsStatusLine(state)
    Text(
        text = statusText,
        style = TextStyle(
            color = statusColor,
            fontSize = widgetSp(WidgetType.BODY),
        ),
        maxLines = 1,
        modifier = modifier
            .padding(end = WidgetSpace.TIGHT)
            .clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.DNS),
                ),
            ),
    )
}

/**
 * Which DNS server is in use and how traffic reaches it, with its severity color.
 *
 * Deliberately not shared with [DashboardFullContent]'s `dnsStatus`, which the 4x1 uses:
 * the two produce different strings (`"Offline"` against `"No network"`, a bare
 * `"DNS → x"` against `"DNS → x · VPN routed"`, and an `ifEmpty { "?" }` guard the 4x1
 * has and this does not). Unifying them would silently change one widget's wording.
 */
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
