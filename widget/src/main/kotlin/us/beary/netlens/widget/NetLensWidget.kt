package us.beary.netlens.widget

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

private val VpnOnColor = androidx.compose.ui.graphics.Color(0xFFFFC107)
private val VpnOffColor = androidx.compose.ui.graphics.Color(0xFF4CAF50)

private val CompactSize = DpSize(250.dp, 40.dp)
private val FullSize = DpSize(180.dp, 110.dp)

class NetLensWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<Preferences> = IpWidgetStateDefinition

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(CompactSize, FullSize))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val prefs = currentState<Preferences>()
                val state = prefs.toIpWidgetState()
                val size = LocalSize.current
                if (size.height < FullSize.height) {
                    CompactContent(state = state)
                } else {
                    WidgetContent(state = state)
                }
            }
        }
    }
}

@Composable
private fun CompactContent(state: IpWidgetState) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .background(GlanceTheme.colors.widgetBackground),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            CompactIpRow(label = "WAN", value = state.ip.ifEmpty { "—" })
            Spacer(modifier = GlanceModifier.size(2.dp))
            CompactIpRow(label = "LAN", value = state.lanIp.ifEmpty { "—" })
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column(
            horizontalAlignment = Alignment.End,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(
                        if (state.isVpn) R.drawable.ic_vpn_on else R.drawable.ic_vpn_off,
                    ),
                    contentDescription = if (state.isVpn) "VPN detected" else "No VPN",
                    modifier = GlanceModifier.size(10.dp),
                    colorFilter = ColorFilter.tint(
                        ColorProvider(if (state.isVpn) VpnOnColor else VpnOffColor),
                    ),
                )
                Spacer(modifier = GlanceModifier.width(3.dp))
                Text(
                    text = if (state.isVpn) "VPN" else "Direct",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 10.sp,
                    ),
                    maxLines = 1,
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Image(
                    provider = ImageProvider(R.drawable.ic_refresh),
                    contentDescription = "Refresh",
                    modifier = GlanceModifier
                        .size(16.dp)
                        .clickable(actionRunCallback<IpWidgetRefreshAction>()),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
                )
            }
            val hasSignal = state.signalDbm != 0 || state.linkSpeedMbps > 0
            val transportLabel = transportLabel(state.transport)
            if (hasSignal || transportLabel != null) {
                Spacer(modifier = GlanceModifier.size(2.dp))
                if (hasSignal) {
                    SignalRow(rssi = state.signalDbm, linkSpeedMbps = state.linkSpeedMbps)
                } else {
                    Text(
                        text = transportLabel.orEmpty(),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                    )
                }
            }
            Spacer(modifier = GlanceModifier.size(2.dp))
            Text(
                text = formatScannedAt(state.lastUpdatedEpochMs),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 9.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SignalRow(rssi: Int, linkSpeedMbps: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (rssi != 0) {
            val bars = signalBars(rssi)
            Text(
                text = "▮".repeat(bars) + "▯".repeat(4 - bars),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 9.sp,
                ),
                maxLines = 1,
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
        }
        if (linkSpeedMbps > 0) {
            Text(
                text = "${linkSpeedMbps}M",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
        }
        if (rssi != 0) {
            Text(
                text = "$rssi",
                style = TextStyle(
                    color = ColorProvider(signalColor(rssi)),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
    }
}

private fun signalBars(rssi: Int): Int = when {
    rssi == 0 -> 0
    rssi >= -55 -> 4
    rssi >= -65 -> 3
    rssi >= -75 -> 2
    rssi >= -85 -> 1
    else -> 0
}

private fun signalColor(rssi: Int): androidx.compose.ui.graphics.Color = when {
    rssi >= -65 -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
    rssi >= -80 -> androidx.compose.ui.graphics.Color(0xFFFFC107)
    else -> androidx.compose.ui.graphics.Color(0xFFE53935)
}

private fun transportLabel(transport: Transport): String? = when (transport) {
    Transport.WIFI -> "Wi-Fi"
    Transport.CELLULAR -> "Cellular"
    Transport.ETHERNET -> "Ethernet"
    Transport.VPN -> null
    Transport.UNKNOWN -> null
}

@Composable
private fun CompactIpRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = GlanceModifier.width(28.dp),
            maxLines = 1,
        )
        Text(
            text = value,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
            modifier = GlanceModifier.clickable(
                actionRunCallback<CopyIpAction>(actionParametersOf()),
            ),
        )
    }
}

private fun formatScannedAt(epochMs: Long): String {
    if (epochMs <= 0L) return "Not scanned"
    val now = System.currentTimeMillis()
    val delta = now - epochMs
    if (delta < DateUtils.MINUTE_IN_MILLIS) return "Scanned just now"
    val relative = DateUtils.getRelativeTimeSpanString(
        epochMs,
        now,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    )
    return "Scanned $relative"
}

@Composable
private fun WidgetContent(state: IpWidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(12.dp)
            .background(GlanceTheme.colors.widgetBackground),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "NetLens",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                ),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Refresh",
                modifier = GlanceModifier
                    .size(24.dp)
                    .clickable(actionRunCallback<IpWidgetRefreshAction>()),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
            )
        }

        if (state.ip.isEmpty()) {
            Text(
                text = "Loading...",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 18.sp,
                ),
            )
        } else {
            Text(
                text = state.ip,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                ),
                modifier = GlanceModifier.clickable(
                    actionRunCallback<CopyIpAction>(actionParametersOf()),
                ),
            )

            if (state.isp.isNotEmpty()) {
                Text(
                    text = state.isp,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 12.sp,
                    ),
                    maxLines = 1,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(
                        if (state.isVpn) R.drawable.ic_vpn_on else R.drawable.ic_vpn_off,
                    ),
                    contentDescription = if (state.isVpn) "VPN detected" else "No VPN",
                    modifier = GlanceModifier.size(12.dp),
                    colorFilter = ColorFilter.tint(
                        ColorProvider(if (state.isVpn) VpnOnColor else VpnOffColor),
                    ),
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = if (state.isVpn) "VPN" else "Direct",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 11.sp,
                    ),
                )
            }
        }
    }
}
