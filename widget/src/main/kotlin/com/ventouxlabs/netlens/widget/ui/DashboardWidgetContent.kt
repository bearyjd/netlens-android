package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
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
import com.ventouxlabs.netlens.core.network.VpnState
import com.ventouxlabs.netlens.widget.R
import com.ventouxlabs.netlens.widget.WidgetState
import com.ventouxlabs.netlens.widget.action.DeeplinkUriKey
import com.ventouxlabs.netlens.widget.action.OpenDeeplinkAction
import com.ventouxlabs.netlens.widget.util.Deeplink
import com.ventouxlabs.netlens.widget.util.formatLinkSpeed

@Composable
fun DashboardWidgetContent(
    state: WidgetState,
    showHeader: Boolean = true,
    modifier: GlanceModifier = GlanceModifier
        .fillMaxSize()
        .cornerRadius(16.dp)
        .background(NetLensWidgetColors.background)
        .padding(horizontal = 10.dp, vertical = 6.dp),
) {
    val bottomLabel = when {
        state.ssid != null -> "SSID: ${state.ssid}"
        state.cellGeneration.isNotEmpty() -> state.cellGeneration
        !state.isConnected -> "Offline"
        else -> "Connected"
    }
    val rssiColor = when {
        state.rssi >= -60 -> NetLensWidgetColors.accent
        state.rssi >= -70 -> NetLensWidgetColors.warn
        else -> NetLensWidgetColors.stamp
    }

    Column(modifier = modifier) {
        if (showHeader) {
            WidgetHeaderRow(state = state)
            Spacer(modifier = GlanceModifier.height(2.dp))
        }
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Section 1: Country flag + VPN lock — taps open the VPN Status screen
            Column(
                modifier = GlanceModifier
                    .width(64.dp)
                    .fillMaxHeight()
                    .clickable(
                        actionRunCallback<OpenDeeplinkAction>(
                            actionParametersOf(DeeplinkUriKey to Deeplink.VPNSTATUS),
                        ),
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.countryFlag.ifEmpty { "—" },
                    style = TextStyle(fontSize = widgetSp(40f)),
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                val (backdropColor, lockDrawable, vpnLabel) = when (state.vpnState) {
                    VpnState.FullTunnel -> Triple(NetLensWidgetColors.accent, R.drawable.ic_lock_closed_white, "Protected")
                    VpnState.SplitTunnel -> Triple(NetLensWidgetColors.warn, R.drawable.ic_lock_closed_white, "Split Tunnel")
                    VpnState.None -> Triple(NetLensWidgetColors.stamp, R.drawable.ic_lock_open_white, "No VPN")
                }
                Box(
                    modifier = GlanceModifier
                        .size(28.dp)
                        .cornerRadius(6.dp)
                        .background(backdropColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(lockDrawable),
                        contentDescription = vpnLabel,
                        modifier = GlanceModifier.size(18.dp),
                    )
                    if (state.vpnState is VpnState.SplitTunnel) {
                        Text(
                            text = "!",
                            style = TextStyle(
                                color = NetLensWidgetColors.onAccentFill,
                                fontSize = widgetSp(10f),
                                fontWeight = FontWeight.Bold,
                            ),
                            modifier = GlanceModifier.padding(start = 14.dp, bottom = 14.dp),
                        )
                    }
                }
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = vpnLabel,
                    style = TextStyle(
                        color = backdropColor,
                        fontSize = widgetSp(10f),
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                if (state.hasPrivateDns) {
                    Text(
                        text = "●",
                        style = TextStyle(
                            color = NetLensWidgetColors.accent,
                            fontSize = widgetSp(10f),
                        ),
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(6.dp))

            // Section 2: WAN
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .clickable(
                        actionRunCallback<OpenDeeplinkAction>(
                            actionParametersOf(DeeplinkUriKey to Deeplink.IPINFO),
                        ),
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "WAN",
                    style = TextStyle(
                        color = NetLensWidgetColors.inkSoft,
                        fontSize = widgetSp(13f),
                    ),
                )
                Text(
                    text = state.publicIp.ifEmpty { "—.—.—.—" },
                    style = TextStyle(
                        color = NetLensWidgetColors.ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = widgetSp(18f),
                    ),
                )
            }

            Spacer(modifier = GlanceModifier.width(6.dp))

            // Section 3: LAN (right-aligned)
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .clickable(
                        actionRunCallback<OpenDeeplinkAction>(
                            actionParametersOf(DeeplinkUriKey to Deeplink.DEVICES),
                        ),
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = "LAN",
                    style = TextStyle(
                        color = NetLensWidgetColors.inkSoft,
                        fontSize = widgetSp(13f),
                    ),
                )
                Text(
                    text = state.localIp.ifEmpty { "—" },
                    style = TextStyle(
                        color = NetLensWidgetColors.ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = widgetSp(18f),
                    ),
                )
            }
        }

        // Bottom row: signal strength + speed | SSID/cell/status | RSSI dBm | captive
        Spacer(modifier = GlanceModifier.height(2.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.hasRssi) {
                SignalBarsIcon(level = state.rssiLevel)
                Spacer(modifier = GlanceModifier.width(3.dp))
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
                        color = NetLensWidgetColors.inkSoft,
                        fontSize = widgetSp(14f),
                    ),
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
            }
            Text(
                text = bottomLabel,
                style = TextStyle(
                    color = NetLensWidgetColors.inkSoft,
                    fontSize = widgetSp(14f),
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            if (state.hasRssi) {
                Text(
                    text = "${state.rssi}",
                    style = TextStyle(
                        color = rssiColor,
                        fontSize = widgetSp(14f),
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            if (state.isCaptivePortal) {
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = "Portal",
                    style = TextStyle(
                        color = NetLensWidgetColors.warn,
                        fontSize = widgetSp(14f),
                    ),
                )
            }
        }
    }
}
