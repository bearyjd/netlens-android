package com.ventoux.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import com.ventoux.netlens.core.network.VpnState
import com.ventoux.netlens.widget.R
import com.ventoux.netlens.widget.WidgetState
import com.ventoux.netlens.widget.action.DeeplinkUriKey
import com.ventoux.netlens.widget.action.OpenDeeplinkAction
import com.ventoux.netlens.widget.action.OpenPortalAction
import com.ventoux.netlens.widget.action.RunDnsCheckAction
import com.ventoux.netlens.widget.action.RunPingAction
import com.ventoux.netlens.widget.action.TriggerScanAction
import com.ventoux.netlens.widget.util.Deeplink

/**
 * 4x1 widget content — three rows: WAN/LAN big IPs, flag/lock/DNS, action chips.
 */
@Composable
fun DashboardFullContent(state: WidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(WidgetTheme.CORNER_RADIUS)
            .background(ColorProvider(WidgetTheme.BACKGROUND_NAVY))
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
                        color = ColorProvider(WidgetTheme.TEXT_MUTED),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = state.publicIp.ifEmpty { "—.—.—.—" },
                    style = TextStyle(
                        color = ColorProvider(WidgetTheme.TEXT_PRIMARY),
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
                        color = ColorProvider(WidgetTheme.TEXT_MUTED),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = state.localIp.ifEmpty { "—" },
                    style = TextStyle(
                        color = ColorProvider(WidgetTheme.TEXT_PRIMARY),
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
                colorFilter = ColorFilter.tint(ColorProvider(WidgetTheme.TEXT_SECONDARY)),
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
                    Triple(WidgetTheme.VPN_FULL_GREEN, R.drawable.ic_lock_closed_white, "Protected")
                VpnState.SplitTunnel ->
                    Triple(WidgetTheme.VPN_SPLIT_AMBER, R.drawable.ic_lock_closed_white, "Split")
                VpnState.None ->
                    Triple(WidgetTheme.VPN_NONE_RED, R.drawable.ic_lock_open_white, "No VPN")
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
                    text = state.countryFlag.ifEmpty { "🌐" },
                    style = TextStyle(fontSize = 16.sp),
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Box(
                    modifier = GlanceModifier
                        .size(16.dp)
                        .cornerRadius(4.dp)
                        .background(ColorProvider(backdrop)),
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
                    color = ColorProvider(statusColor),
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
                        color = ColorProvider(WidgetTheme.rssiColor(state.rssiLevel)),
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
                        color = ColorProvider(WidgetTheme.TEXT_SECONDARY),
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
                icon = "🔍",
                label = "LAN",
                background = WidgetTheme.CHIP_DEFAULT,
                action = actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.DEVICES),
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            BigChip(
                icon = "📡",
                label = pingLabel(state),
                background = pingBg(state),
                action = actionRunCallback<RunPingAction>(),
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            BigChip(
                icon = "🌐",
                label = dnsChipLabel(state),
                background = dnsChipBg(state),
                action = actionRunCallback<RunDnsCheckAction>(),
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            BigChip(
                icon = "🚪",
                label = "Portal",
                background = if (state.isCaptivePortal) {
                    WidgetTheme.CHIP_PORTAL_ACTIVE
                } else {
                    WidgetTheme.CHIP_DEFAULT
                },
                action = actionRunCallback<OpenPortalAction>(),
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

@Composable
private fun BigChip(
    icon: String,
    label: String,
    background: Color,
    action: Action,
    modifier: GlanceModifier,
) {
    Row(
        modifier = modifier
            .cornerRadius(6.dp)
            .background(ColorProvider(background))
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .clickable(action),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = icon,
            style = TextStyle(fontSize = 14.sp),
        )
        Spacer(modifier = GlanceModifier.width(3.dp))
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(WidgetTheme.TEXT_PRIMARY),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

private fun dnsStatus(state: WidgetState): Pair<String, Color> = when {
    state.isCaptivePortal -> "Captive portal" to WidgetTheme.CAPTIVE_ORANGE
    state.isDnsLeaking ->
        "DNS → ${state.primaryDns.ifEmpty { "?" }} · Leak!" to WidgetTheme.SCORE_AMBER
    state.vpnState is VpnState.FullTunnel ->
        "DNS → ${state.primaryDns.ifEmpty { "?" }}" to WidgetTheme.VPN_FULL_GREEN
    state.vpnState is VpnState.SplitTunnel ->
        "DNS → ${state.primaryDns.ifEmpty { "?" }}" to WidgetTheme.VPN_SPLIT_AMBER
    state.isConnected ->
        "DNS → ${state.primaryDns.ifEmpty { "?" }}" to WidgetTheme.TEXT_SECONDARY
    else -> "Offline" to WidgetTheme.SCORE_RED
}

private fun pingLabel(state: WidgetState): String = when (state.chipPingResult) {
    "" -> "Ping"
    "running" -> "..."
    "fail" -> "✗"
    else -> "${state.chipPingResult}ms"
}

private fun pingBg(state: WidgetState): Color = when (state.chipPingResult) {
    "fail" -> WidgetTheme.CHIP_BAD
    "", "running" -> WidgetTheme.CHIP_DEFAULT
    else -> WidgetTheme.CHIP_GOOD
}

private fun dnsChipLabel(state: WidgetState): String = when (state.chipDnsResult) {
    "running" -> "..."
    "clean" -> "Clean"
    "leak" -> "Leak!"
    else -> "DNS"
}

private fun dnsChipBg(state: WidgetState): Color = when (state.chipDnsResult) {
    "leak" -> WidgetTheme.CHIP_BAD
    "clean" -> WidgetTheme.CHIP_GOOD
    else -> WidgetTheme.CHIP_DEFAULT
}
