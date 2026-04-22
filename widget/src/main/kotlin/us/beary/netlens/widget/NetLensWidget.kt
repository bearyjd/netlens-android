package us.beary.netlens.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
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

class NetLensWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<Preferences> = IpWidgetStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val prefs = currentState<Preferences>()
                val state = prefs.toIpWidgetState()
                WidgetContent(state = state)
            }
        }
    }
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
