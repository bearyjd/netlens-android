package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ventouxlabs.netlens.core.network.VpnState
import com.ventouxlabs.netlens.widget.R
import com.ventouxlabs.netlens.widget.WidgetState
import com.ventouxlabs.netlens.widget.action.DeeplinkUriKey
import com.ventouxlabs.netlens.widget.action.OpenDeeplinkAction
import com.ventouxlabs.netlens.widget.action.OpenPortalAction
import com.ventouxlabs.netlens.widget.action.TriggerScanAction
import com.ventouxlabs.netlens.widget.util.Deeplink

/**
 * 4x1 widget content — three rows: WAN/LAN big IPs, flag/lock/DNS, action chips.
 *
 * Row content is intentionally inlined into the outer Column rather than split into
 * private composables. Extracting Rows that carry `defaultWeight()` modifiers caused
 * the widget to render blank on device — Glance's weight attribute does not survive
 * being constructed in one composable's scope and consumed in another.
 */
@Composable
fun DashboardFullContent(state: WidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .widgetBackground()
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        // Row 1: WAN + LAN (big)
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = GlanceModifier.defaultWeight().clickable(
                    actionRunCallback<OpenDeeplinkAction>(
                        actionParametersOf(DeeplinkUriKey to Deeplink.IPINFO),
                    ),
                ),
            ) {
                Text(
                    text = "WAN",
                    style = TextStyle(
                        color = NetLensWidgetColors.inkSoft,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = state.publicIp.ifEmpty { "—.—.—.—" },
                    style = TextStyle(
                        color = NetLensWidgetColors.ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    ),
                    maxLines = 1,
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            Column(
                modifier = GlanceModifier.defaultWeight().clickable(
                    actionRunCallback<OpenDeeplinkAction>(
                        actionParametersOf(DeeplinkUriKey to Deeplink.DEVICES),
                    ),
                ),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = "LAN",
                    style = TextStyle(
                        color = NetLensWidgetColors.inkSoft,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = state.localIp.ifEmpty { "—" },
                    style = TextStyle(
                        color = NetLensWidgetColors.ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    ),
                    maxLines = 1,
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Refresh",
                colorFilter = ColorFilter.tint(NetLensWidgetColors.inkSoft),
                modifier = GlanceModifier
                    .size(20.dp)
                    .clickable(actionRunCallback<TriggerScanAction>()),
            )
        }

        // Row 2: flag + VPN lock + DNS status
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val (backdrop, lockDrawable, vpnLabel) = when (state.vpnState) {
                VpnState.FullTunnel ->
                    Triple(NetLensWidgetColors.accent, R.drawable.ic_lock_closed_white, "Protected")
                VpnState.SplitTunnel ->
                    Triple(NetLensWidgetColors.warn, R.drawable.ic_lock_closed_white, "Split")
                VpnState.None ->
                    Triple(NetLensWidgetColors.stamp, R.drawable.ic_lock_open_white, "No VPN")
            }
            Row(
                modifier = GlanceModifier.clickable(
                    actionRunCallback<OpenDeeplinkAction>(
                        actionParametersOf(DeeplinkUriKey to Deeplink.VPNSTATUS),
                    ),
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.countryFlag.ifEmpty { "—" },
                    style = TextStyle(fontSize = 16.sp),
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Box(
                    modifier = GlanceModifier
                        .size(16.dp)
                        .cornerRadius(4.dp)
                        .background(backdrop),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(lockDrawable),
                        contentDescription = vpnLabel,
                        modifier = GlanceModifier.size(11.dp),
                    )
                }
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            val (statusText, statusColor) = dnsStatus(state)
            Text(
                text = statusText,
                style = TextStyle(
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight().clickable(
                    actionRunCallback<OpenDeeplinkAction>(
                        actionParametersOf(DeeplinkUriKey to Deeplink.DNS),
                    ),
                ),
            )
            if (state.hasRssi) {
                Spacer(modifier = GlanceModifier.width(6.dp))
                SignalBarsIcon(level = state.rssiLevel)
                Spacer(modifier = GlanceModifier.width(3.dp))
                Text(
                    text = "${state.rssi}",
                    style = TextStyle(
                        color = NetLensWidgetColors.rssiColor(state.rssiLevel),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            } else if (state.cellGeneration.isNotEmpty()) {
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = state.cellGeneration,
                    style = TextStyle(
                        color = NetLensWidgetColors.inkSoft,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
            }
        }

        // Row 3: action chips
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BigChip(
                label = "LAN",
                background = NetLensWidgetColors.accentSoft,
                onBackground = NetLensWidgetColors.onAccentSoft,
                action = actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.DEVICES),
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            BigChip(
                label = "Ping",
                background = NetLensWidgetColors.accentSoft,
                onBackground = NetLensWidgetColors.onAccentSoft,
                action = actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.pingHost("8.8.8.8")),
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            BigChip(
                label = "DNS",
                background = NetLensWidgetColors.accentSoft,
                onBackground = NetLensWidgetColors.onAccentSoft,
                action = actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.DNS_LEAK),
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            BigChip(
                label = "Portal",
                background = if (state.isCaptivePortal) {
                    NetLensWidgetColors.warnSoft
                } else {
                    NetLensWidgetColors.accentSoft
                },
                onBackground = if (state.isCaptivePortal) {
                    NetLensWidgetColors.onWarnSoft
                } else {
                    NetLensWidgetColors.onAccentSoft
                },
                action = actionRunCallback<OpenPortalAction>(),
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

@Composable
private fun BigChip(
    label: String,
    background: ColorProvider,
    onBackground: ColorProvider,
    action: Action,
    modifier: GlanceModifier,
) {
    Row(
        modifier = modifier
            .cornerRadius(6.dp)
            .background(background)
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .clickable(action),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = onBackground,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

private fun dnsStatus(state: WidgetState): Pair<String, ColorProvider> = when {
    state.isCaptivePortal -> "Captive portal" to NetLensWidgetColors.warn
    state.isDnsLeaking ->
        "DNS → ${state.primaryDns.ifEmpty { "?" }} · Leak!" to NetLensWidgetColors.warn
    state.vpnState is VpnState.FullTunnel ->
        "DNS → ${state.primaryDns.ifEmpty { "?" }}" to NetLensWidgetColors.accent
    state.vpnState is VpnState.SplitTunnel ->
        "DNS → ${state.primaryDns.ifEmpty { "?" }}" to NetLensWidgetColors.warn
    state.isConnected ->
        "DNS → ${state.primaryDns.ifEmpty { "?" }}" to NetLensWidgetColors.inkSoft
    else -> "Offline" to NetLensWidgetColors.stamp
}
