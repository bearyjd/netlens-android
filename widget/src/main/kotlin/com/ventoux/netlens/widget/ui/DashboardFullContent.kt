package com.ventoux.netlens.widget.ui

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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
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
import com.ventoux.netlens.widget.util.formatLinkSpeed

/**
 * 4x1 widget content with feature parity to the 4x2:
 *   row 1: score · flag · VPN lock · WAN IP · LAN IP · refresh
 *   row 2: signal · speed · DNS status · scanned-ago · 4 action chips
 */
@Composable
fun DashboardFullContent(state: WidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(WidgetTheme.CORNER_RADIUS)
            .background(ColorProvider(WidgetTheme.BACKGROUND_NAVY))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        TopRow(state)
        Spacer(modifier = GlanceModifier.height(2.dp))
        BottomRow(state)
    }
}

@Composable
private fun TopRow(state: WidgetState) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val gradeText = if (state.hasScore) state.scoreGrade else "?"
        val gradeColor = if (state.hasScore) {
            WidgetTheme.scoreColor(state.scoreGrade)
        } else {
            WidgetTheme.SCORE_GRAY
        }
        Text(
            text = gradeText,
            style = TextStyle(
                color = ColorProvider(gradeColor),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            ),
            modifier = GlanceModifier.clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.POSTURE),
                ),
            ),
        )

        Spacer(modifier = GlanceModifier.width(6.dp))

        VpnFlagBlock(state)

        Spacer(modifier = GlanceModifier.width(6.dp))

        IpColumn(
            label = "WAN",
            value = state.publicIp.ifEmpty { "—.—.—.—" },
            deeplink = Deeplink.IPINFO,
            modifier = GlanceModifier.defaultWeight(),
            alignEnd = false,
        )

        Spacer(modifier = GlanceModifier.width(4.dp))

        IpColumn(
            label = "LAN",
            value = state.localIp.ifEmpty { "—" },
            deeplink = Deeplink.DEVICES,
            modifier = GlanceModifier.defaultWeight(),
            alignEnd = true,
        )

        Spacer(modifier = GlanceModifier.width(4.dp))

        Image(
            provider = ImageProvider(R.drawable.ic_refresh),
            contentDescription = "Refresh",
            colorFilter = ColorFilter.tint(ColorProvider(WidgetTheme.TEXT_SECONDARY)),
            modifier = GlanceModifier
                .size(16.dp)
                .clickable(actionRunCallback<TriggerScanAction>()),
        )
    }
}

@Composable
private fun BottomRow(state: WidgetState) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.hasRssi) {
            SignalBarsIcon(level = state.rssiLevel)
            Spacer(modifier = GlanceModifier.width(3.dp))
            Text(
                text = "${state.rssi}",
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.rssiColor(state.rssiLevel)),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
        }

        val speedLabel = when {
            state.hasLinkSpeed -> formatLinkSpeed(state.linkSpeedMbps)
            state.cellGeneration.isNotEmpty() -> state.cellGeneration
            else -> ""
        }
        if (speedLabel.isNotEmpty()) {
            Text(
                text = speedLabel,
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.TEXT_SECONDARY),
                    fontSize = 10.sp,
                ),
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
        }

        DnsStatusText(state = state, modifier = GlanceModifier.defaultWeight())

        Spacer(modifier = GlanceModifier.width(4.dp))

        MiniChips(state)
    }
}

@Composable
private fun VpnFlagBlock(state: WidgetState) {
    val (backdrop, lockDrawable, vpnLabel) = when (state.vpnState) {
        VpnState.FullTunnel ->
            Triple(WidgetTheme.VPN_FULL_GREEN, R.drawable.ic_lock_closed_white, "Protected")
        VpnState.SplitTunnel ->
            Triple(WidgetTheme.VPN_SPLIT_AMBER, R.drawable.ic_lock_closed_white, "Split Tunnel")
        VpnState.None ->
            Triple(WidgetTheme.VPN_NONE_RED, R.drawable.ic_lock_open_white, "No VPN")
    }
    Row(
        modifier = GlanceModifier
            .fillMaxHeight()
            .clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.VPNSTATUS),
                ),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.countryFlag.ifEmpty { "🌐" },
            style = TextStyle(fontSize = 14.sp),
        )
        Spacer(modifier = GlanceModifier.width(3.dp))
        Box(
            modifier = GlanceModifier
                .size(14.dp)
                .cornerRadius(3.dp)
                .background(ColorProvider(backdrop)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(lockDrawable),
                contentDescription = vpnLabel,
                modifier = GlanceModifier.size(10.dp),
            )
        }
        if (state.hasPrivateDns) {
            Spacer(modifier = GlanceModifier.width(2.dp))
            Text(
                text = "●",
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.PRIVATE_DNS_BLUE),
                    fontSize = 9.sp,
                ),
            )
        }
    }
}

@Composable
private fun IpColumn(
    label: String,
    value: String,
    deeplink: String,
    modifier: GlanceModifier,
    alignEnd: Boolean,
) {
    Column(
        modifier = modifier.clickable(
            actionRunCallback<OpenDeeplinkAction>(
                actionParametersOf(DeeplinkUriKey to deeplink),
            ),
        ),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(WidgetTheme.TEXT_MUTED),
                fontSize = 8.sp,
            ),
            maxLines = 1,
        )
        Text(
            text = value,
            style = TextStyle(
                color = ColorProvider(WidgetTheme.TEXT_PRIMARY),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun DnsStatusText(state: WidgetState, modifier: GlanceModifier) {
    val (statusText, statusColor) = when {
        state.isCaptivePortal -> "Captive portal" to WidgetTheme.CAPTIVE_ORANGE
        state.isDnsLeaking -> "DNS leak!" to WidgetTheme.SCORE_AMBER
        state.vpnState is VpnState.FullTunnel ->
            "DNS→${state.primaryDns.ifEmpty { "?" }} · VPN" to WidgetTheme.VPN_FULL_GREEN
        state.vpnState is VpnState.SplitTunnel ->
            "DNS→${state.primaryDns.ifEmpty { "?" }} · Split" to WidgetTheme.VPN_SPLIT_AMBER
        state.isConnected ->
            "DNS→${state.primaryDns.ifEmpty { "?" }}" to WidgetTheme.TEXT_SECONDARY
        else -> "Offline" to WidgetTheme.SCORE_RED
    }
    val elapsed = WidgetTheme.relativeTime(state.lastRefreshMs)
    val suffix = if (elapsed.isNotEmpty()) " · $elapsed" else ""
    Text(
        text = "$statusText$suffix",
        style = TextStyle(
            color = ColorProvider(statusColor),
            fontSize = 10.sp,
        ),
        maxLines = 1,
        modifier = modifier.clickable(
            actionRunCallback<OpenDeeplinkAction>(
                actionParametersOf(DeeplinkUriKey to Deeplink.DNS),
            ),
        ),
    )
}

@Composable
private fun MiniChips(state: WidgetState) {
    val pingBg = when (state.chipPingResult) {
        "fail" -> WidgetTheme.CHIP_BAD
        "", "running" -> WidgetTheme.CHIP_DEFAULT
        else -> WidgetTheme.CHIP_GOOD
    }
    val dnsBg = when (state.chipDnsResult) {
        "leak" -> WidgetTheme.CHIP_BAD
        "clean" -> WidgetTheme.CHIP_GOOD
        else -> WidgetTheme.CHIP_DEFAULT
    }
    val portalBg = if (state.isCaptivePortal) {
        WidgetTheme.CHIP_PORTAL_ACTIVE
    } else {
        WidgetTheme.CHIP_DEFAULT
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        MiniChip(
            icon = "🔍",
            action = actionRunCallback<OpenDeeplinkAction>(
                actionParametersOf(DeeplinkUriKey to Deeplink.DEVICES),
            ),
            background = WidgetTheme.CHIP_DEFAULT,
        )
        Spacer(modifier = GlanceModifier.width(2.dp))
        MiniChip(
            icon = "📡",
            action = actionRunCallback<RunPingAction>(),
            background = pingBg,
        )
        Spacer(modifier = GlanceModifier.width(2.dp))
        MiniChip(
            icon = "🌐",
            action = actionRunCallback<RunDnsCheckAction>(),
            background = dnsBg,
        )
        Spacer(modifier = GlanceModifier.width(2.dp))
        MiniChip(
            icon = "🚪",
            action = actionRunCallback<OpenPortalAction>(),
            background = portalBg,
        )
    }
}

@Composable
private fun MiniChip(
    icon: String,
    action: Action,
    background: androidx.compose.ui.graphics.Color,
) {
    Text(
        text = icon,
        modifier = GlanceModifier
            .cornerRadius(3.dp)
            .background(ColorProvider(background))
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .clickable(action),
        style = TextStyle(
            fontSize = 11.sp,
            color = ColorProvider(WidgetTheme.TEXT_PRIMARY),
        ),
        maxLines = 1,
    )
}
