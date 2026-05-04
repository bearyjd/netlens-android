package com.ventoux.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
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
                .width(48.dp)
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
                style = TextStyle(fontSize = 20.sp),
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (state.vpnActive) "🔒" else "🔓",
                    style = TextStyle(
                        color = ColorProvider(
                            if (state.vpnActive) WidgetTheme.VPN_GREEN else WidgetTheme.VPN_GRAY,
                        ),
                        fontSize = 11.sp,
                    ),
                )
                if (state.hasPrivateDns) {
                    Text(
                        text = " ●",
                        style = TextStyle(
                            color = ColorProvider(WidgetTheme.PRIVATE_DNS_BLUE),
                            fontSize = 8.sp,
                        ),
                    )
                }
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
                    fontSize = 9.sp,
                ),
            )
            Text(
                text = state.publicIp.ifEmpty { "—.—.—.—" },
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.TEXT_PRIMARY),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                ),
                maxLines = 1,
            )
            if (state.vpnActive && state.vpnInterfaceName.isNotEmpty()) {
                Text(
                    text = state.vpnInterfaceName,
                    style = TextStyle(
                        color = ColorProvider(WidgetTheme.VPN_GREEN),
                        fontSize = 9.sp,
                        fontStyle = FontStyle.Italic,
                    ),
                    maxLines = 1,
                )
            } else if (state.hasPing) {
                Text(
                    text = "${state.pingMs}ms",
                    style = TextStyle(
                        color = ColorProvider(WidgetTheme.pingColor(state.pingMs)),
                        fontSize = 10.sp,
                    ),
                )
            }
        }

        Spacer(modifier = GlanceModifier.width(6.dp))

        // Section 3: LAN
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
        ) {
            Text(
                text = "LAN",
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.TEXT_MUTED),
                    fontSize = 9.sp,
                ),
            )
            Text(
                text = state.localIp.ifEmpty { "—" },
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.TEXT_PRIMARY),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                ),
                maxLines = 1,
            )
            Row {
                if (state.hasIpv6) {
                    Text(
                        text = "v6",
                        style = TextStyle(
                            color = ColorProvider(WidgetTheme.IPV6_TEAL),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                }
                if (state.isMetered) {
                    Text(
                        text = "M",
                        style = TextStyle(
                            color = ColorProvider(WidgetTheme.METERED_GRAY),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }

        Spacer(modifier = GlanceModifier.width(6.dp))

        // Section 4: Signal
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .clickable(
                    actionRunCallback<OpenDeeplinkAction>(
                        actionParametersOf(DeeplinkUriKey to Deeplink.HOME),
                    ),
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.End,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                            fontSize = 10.sp,
                        ),
                    )
                }
            }
            val networkLabel = when {
                state.ssid != null -> state.ssid
                state.cellGeneration.isNotEmpty() -> state.cellGeneration
                !state.isConnected -> "Offline"
                else -> "Connected"
            }
            Text(
                text = networkLabel,
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.TEXT_SECONDARY),
                    fontSize = 11.sp,
                ),
                maxLines = 1,
            )
            if (state.isCaptivePortal) {
                Text(
                    text = "⚠",
                    style = TextStyle(
                        color = ColorProvider(WidgetTheme.CAPTIVE_ORANGE),
                        fontSize = 11.sp,
                    ),
                )
            }
        }
    }
    }
}
