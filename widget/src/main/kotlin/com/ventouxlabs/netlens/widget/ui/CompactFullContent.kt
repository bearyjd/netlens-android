package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import com.ventouxlabs.netlens.core.network.VpnState
import com.ventouxlabs.netlens.widget.R
import com.ventouxlabs.netlens.widget.WidgetState
import com.ventouxlabs.netlens.widget.action.DeeplinkUriKey
import com.ventouxlabs.netlens.widget.action.OpenDeeplinkAction
import com.ventouxlabs.netlens.widget.util.Deeplink

/**
 * 2x1 widget content — flag · VPN lock · WAN IP / signal · LAN IP, filling the cell.
 */
@Composable
fun CompactFullContent(state: WidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(NetLensWidgetColors.background)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        val rowModifier = GlanceModifier.fillMaxWidth().defaultWeight()
        TopRow(state, rowModifier)
        BottomRow(state, rowModifier)
    }
}

@Composable
private fun TopRow(state: WidgetState, modifier: GlanceModifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlagAndLock(state)
        Spacer(modifier = GlanceModifier.width(5.dp))
        IpText(
            label = "WAN",
            value = state.publicIp.ifEmpty { "—.—.—.—" },
            deeplink = Deeplink.IPINFO,
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

@Composable
private fun BottomRow(state: WidgetState, modifier: GlanceModifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SignalBlock(state)
        Spacer(modifier = GlanceModifier.width(5.dp))
        IpText(
            label = "LAN",
            value = state.localIp.ifEmpty { "—" },
            deeplink = Deeplink.DEVICES,
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

@Composable
private fun FlagAndLock(state: WidgetState) {
    val (backdrop, lockDrawable, vpnLabel) = when (state.vpnState) {
        VpnState.FullTunnel ->
            Triple(NetLensWidgetColors.accent, R.drawable.ic_lock_closed_white, "Protected")
        VpnState.SplitTunnel ->
            Triple(NetLensWidgetColors.warn, R.drawable.ic_lock_closed_white, "Split Tunnel")
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
            style = TextStyle(fontSize = 13.sp),
        )
        Spacer(modifier = GlanceModifier.width(2.dp))
        Box(
            modifier = GlanceModifier
                .size(13.dp)
                .cornerRadius(3.dp)
                .background(backdrop),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(lockDrawable),
                contentDescription = vpnLabel,
                modifier = GlanceModifier.size(9.dp),
            )
        }
    }
}

@Composable
private fun SignalBlock(state: WidgetState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (state.hasRssi) {
            SignalBarsIcon(level = state.rssiLevel)
        } else if (state.cellGeneration.isNotEmpty()) {
            Text(
                text = state.cellGeneration,
                style = TextStyle(
                    color = NetLensWidgetColors.inkSoft,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        } else {
            Text(
                text = "▯▯▯▯",
                style = TextStyle(
                    color = NetLensWidgetColors.inkSoft,
                    fontSize = 10.sp,
                ),
            )
        }
    }
}

@Composable
private fun IpText(
    label: String,
    value: String,
    deeplink: String,
    modifier: GlanceModifier,
) {
    Row(
        modifier = modifier.clickable(
            actionRunCallback<OpenDeeplinkAction>(
                actionParametersOf(DeeplinkUriKey to deeplink),
            ),
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = NetLensWidgetColors.inkSoft,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(modifier = GlanceModifier.width(3.dp))
        Text(
            text = value,
            style = TextStyle(
                color = NetLensWidgetColors.ink,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            ),
            maxLines = 1,
        )
    }
}
