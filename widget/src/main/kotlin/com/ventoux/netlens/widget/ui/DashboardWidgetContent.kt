package com.ventoux.netlens.widget.ui

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
import com.ventoux.netlens.widget.util.Deeplink
import com.ventoux.netlens.widget.util.formatLinkSpeed

@Composable
fun DashboardWidgetContent(
    state: WidgetState,
    showHeader: Boolean = true,
    modifier: GlanceModifier = GlanceModifier
        .fillMaxSize()
        .cornerRadius(WidgetTheme.CORNER_RADIUS)
        .background(ColorProvider(WidgetTheme.BACKGROUND_NAVY))
        .padding(horizontal = 10.dp, vertical = 6.dp),
) {
    val bottomLabel = when {
        state.ssid != null -> "SSID: ${state.ssid}"
        state.cellGeneration.isNotEmpty() -> state.cellGeneration
        !state.isConnected -> "Offline"
        else -> "Connected"
    }
    val rssiColor = when {
        state.rssi >= -60 -> WidgetTheme.SCORE_GREEN
        state.rssi >= -70 -> WidgetTheme.SCORE_AMBER
        else -> WidgetTheme.SCORE_RED
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
            // Section 1: Country flag + VPN lock
            Column(
                modifier = GlanceModifier
                    .width(64.dp)
                    .fillMaxHeight()
                    .clickable(
                        actionRunCallback<OpenDeeplinkAction>(
                            actionParametersOf(DeeplinkUriKey to Deeplink.POSTURE),
                        ),
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.countryFlag.ifEmpty { "🌐" },
                    style = TextStyle(fontSize = 40.sp),
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                val (backdropColor, lockDrawable, vpnLabel) = when (state.vpnState) {
                    VpnState.FullTunnel -> Triple(WidgetTheme.VPN_FULL_GREEN, R.drawable.ic_lock_closed_white, "Protected")
                    VpnState.SplitTunnel -> Triple(WidgetTheme.VPN_SPLIT_AMBER, R.drawable.ic_lock_closed_white, "Split Tunnel")
                    VpnState.None -> Triple(WidgetTheme.VPN_NONE_RED, R.drawable.ic_lock_open_white, "No VPN")
                }
                Box(
                    modifier = GlanceModifier
                        .size(28.dp)
                        .cornerRadius(6.dp)
                        .background(ColorProvider(backdropColor)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(lockDrawable),
                        contentDescription = vpnLabel,
                        modifier = GlanceModifier.size(18.dp),
                    )
                    if (state.vpnState is VpnState.SplitTunnel) {
                        Text(
                            text = "⚠",
                            style = TextStyle(
                                color = ColorProvider(androidx.compose.ui.graphics.Color.White),
                                fontSize = 10.sp,
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
                        color = ColorProvider(backdropColor),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                if (state.hasPrivateDns) {
                    Text(
                        text = "●",
                        style = TextStyle(
                            color = ColorProvider(WidgetTheme.PRIVATE_DNS_BLUE),
                            fontSize = 10.sp,
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
                        color = ColorProvider(WidgetTheme.TEXT_MUTED),
                        fontSize = 13.sp,
                    ),
                )
                Text(
                    text = state.publicIp.ifEmpty { "—.—.—.—" },
                    style = TextStyle(
                        color = ColorProvider(WidgetTheme.TEXT_PRIMARY),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
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
                        color = ColorProvider(WidgetTheme.TEXT_MUTED),
                        fontSize = 13.sp,
                    ),
                )
                Text(
                    text = state.localIp.ifEmpty { "—" },
                    style = TextStyle(
                        color = ColorProvider(WidgetTheme.TEXT_PRIMARY),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
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
                        color = ColorProvider(WidgetTheme.TEXT_SECONDARY),
                        fontSize = 14.sp,
                    ),
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
            }
            Text(
                text = bottomLabel,
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.TEXT_SECONDARY),
                    fontSize = 14.sp,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            if (state.hasRssi) {
                Text(
                    text = "${state.rssi}",
                    style = TextStyle(
                        color = ColorProvider(rssiColor),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            if (state.isCaptivePortal) {
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = "⚠",
                    style = TextStyle(
                        color = ColorProvider(WidgetTheme.CAPTIVE_ORANGE),
                        fontSize = 14.sp,
                    ),
                )
            }
        }
    }
}
