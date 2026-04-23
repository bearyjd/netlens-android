package us.beary.netlens.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.flow.first
import us.beary.netlens.widget.action.CarouselNextAction
import us.beary.netlens.widget.action.CarouselPrevAction
import us.beary.netlens.widget.action.CopyGatewayAction
import us.beary.netlens.widget.action.CopyPublicIpAction
import us.beary.netlens.widget.action.DeeplinkUriKey
import us.beary.netlens.widget.action.OpenAppAction
import us.beary.netlens.widget.action.OpenDeeplinkAction
import us.beary.netlens.widget.data.WidgetPreferencesRepository
import us.beary.netlens.widget.model.WidgetPage
import us.beary.netlens.widget.model.WidgetPreferences
import us.beary.netlens.widget.model.WidgetSize
import us.beary.netlens.widget.util.Deeplink
import us.beary.netlens.widget.util.toFlagEmoji

class NetLensWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<Preferences> = IpWidgetStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetPrefs = WidgetPreferencesRepository.observe(context).first()

        provideContent {
            val prefs = currentState<Preferences>()
            val state = prefs.toIpWidgetState()
            val pageIndex = prefs[IpWidgetStateDefinition.CAROUSEL_PAGE_KEY] ?: 0
            WidgetRoot(state = state, widgetPrefs = widgetPrefs, pageIndex = pageIndex)
        }
    }
}

@Composable
private fun WidgetRoot(
    state: IpWidgetState,
    widgetPrefs: WidgetPreferences,
    pageIndex: Int,
) {
    val bgColor = Color(widgetPrefs.backgroundColor.argb).copy(alpha = widgetPrefs.backgroundAlpha)
    val accentColor = Color(widgetPrefs.accentColor.argb)
    val textColor = if (widgetPrefs.backgroundColor.argb == 0xFFFFFFFF.toLong()) Color.Black else Color.White
    val textSizeSp = widgetPrefs.textSize.sp.sp
    val pages = widgetPrefs.pages
    val safeIndex = if (pages.isEmpty()) 0 else pageIndex.coerceIn(0, pages.lastIndex)
    val currentPage = pages.getOrNull(safeIndex) ?: WidgetPage.CONNECTION
    val isSmall = widgetPrefs.widgetSize == WidgetSize.SMALL

    val rootModifier = if (isSmall) {
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(widgetPrefs.cornerRadius.dp)
            .background(ColorProvider(bgColor))
            .clickable(actionRunCallback<CarouselNextAction>())
    } else {
        GlanceModifier
            .fillMaxSize()
            .cornerRadius(widgetPrefs.cornerRadius.dp)
            .background(ColorProvider(bgColor))
            .clickable(actionRunCallback<OpenAppAction>())
    }

    Column(
        modifier = rootModifier.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (currentPage) {
            WidgetPage.CONNECTION -> ConnectionPage(state, textColor, textSizeSp, accentColor, isSmall)
            WidgetPage.NETWORK -> NetworkPage(state, textColor, textSizeSp, accentColor, isSmall)
        }

        if (pages.size > 1) {
            Spacer(modifier = GlanceModifier.height(4.dp))
            PageIndicator(
                pageCount = pages.size,
                currentIndex = safeIndex,
                accentColor = accentColor,
                dimColor = textColor.copy(alpha = 0.3f),
            )
        }
    }
}

@Composable
private fun ConnectionPage(
    state: IpWidgetState,
    textColor: Color,
    textSizeSp: androidx.compose.ui.unit.TextUnit,
    accentColor: Color,
    isSmall: Boolean,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.countryCode.isNotEmpty()) {
            Text(
                text = state.countryCode.toFlagEmoji(),
                style = TextStyle(fontSize = (textSizeSp.value + 4).sp),
                modifier = GlanceModifier.clickable(
                    actionRunCallback<OpenDeeplinkAction>(
                        actionParametersOf(DeeplinkUriKey to Deeplink.IPINFO),
                    ),
                ),
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
        }

        val vpnDot = if (state.isVpn) "●" else "○"
        Text(
            text = "${vpnDot}vpn",
            style = TextStyle(
                color = ColorProvider(if (state.isVpn) accentColor else textColor.copy(alpha = 0.5f)),
                fontSize = (textSizeSp.value - 2).sp,
            ),
        )

        Spacer(modifier = GlanceModifier.width(8.dp))

        Text(
            text = state.publicIp.ifEmpty { "—" },
            style = TextStyle(
                color = ColorProvider(textColor),
                fontWeight = FontWeight.Bold,
                fontSize = textSizeSp,
            ),
            modifier = GlanceModifier.defaultWeight().clickable(
                actionRunCallback<CopyPublicIpAction>(),
            ),
            maxLines = 1,
        )

        if (!isSmall) {
            Spacer(modifier = GlanceModifier.width(4.dp))
            NavArrow(text = ">", textColor = textColor)
        }
    }

    Spacer(modifier = GlanceModifier.height(2.dp))

    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ssidText = state.ssid ?: "Not connected"
        val localIpText = state.localIp ?: "—"
        Text(
            text = "$ssidText · $localIpText",
            style = TextStyle(
                color = ColorProvider(textColor.copy(alpha = 0.7f)),
                fontSize = (textSizeSp.value - 2).sp,
            ),
            modifier = GlanceModifier.clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(
                        DeeplinkUriKey to (state.localIp?.let { Deeplink.lanScanForDevice(it) } ?: Deeplink.LAN_SCAN),
                    ),
                ),
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun NetworkPage(
    state: IpWidgetState,
    textColor: Color,
    textSizeSp: androidx.compose.ui.unit.TextUnit,
    accentColor: Color,
    isSmall: Boolean,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isSmall) {
            NavArrow(text = "<", textColor = textColor)
            Spacer(modifier = GlanceModifier.width(4.dp))
        }

        Text(
            text = "Gateway",
            style = TextStyle(
                color = ColorProvider(textColor.copy(alpha = 0.6f)),
                fontSize = (textSizeSp.value - 2).sp,
            ),
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = state.gateway ?: "—",
            style = TextStyle(
                color = ColorProvider(textColor),
                fontWeight = FontWeight.Bold,
                fontSize = textSizeSp,
            ),
            modifier = GlanceModifier.clickable(actionRunCallback<CopyGatewayAction>()),
            maxLines = 1,
        )
    }

    Spacer(modifier = GlanceModifier.height(4.dp))

    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "DNS",
            style = TextStyle(
                color = ColorProvider(textColor.copy(alpha = 0.6f)),
                fontSize = (textSizeSp.value - 2).sp,
            ),
        )
        Spacer(modifier = GlanceModifier.width(8.dp))

        val dnsText = state.dnsServers.joinToString(", ").ifEmpty { "—" }
        val firstDns = state.dnsServers.firstOrNull()
        val dnsModifier = if (firstDns != null) {
            GlanceModifier.clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.dnsWithServer(firstDns)),
                ),
            )
        } else {
            GlanceModifier
        }

        Text(
            text = dnsText,
            style = TextStyle(
                color = ColorProvider(textColor),
                fontSize = (textSizeSp.value - 1).sp,
            ),
            modifier = dnsModifier,
            maxLines = 1,
        )
    }
}

@Composable
private fun NavArrow(text: String, textColor: Color) {
    val action = if (text == ">") {
        actionRunCallback<CarouselNextAction>()
    } else {
        actionRunCallback<CarouselPrevAction>()
    }
    Text(
        text = text,
        style = TextStyle(
            color = ColorProvider(textColor.copy(alpha = 0.5f)),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        ),
        modifier = GlanceModifier.clickable(action),
    )
}

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentIndex: Int,
    accentColor: Color,
    dimColor: Color,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until pageCount) {
            val dotColor = if (i == currentIndex) accentColor else dimColor
            Text(
                text = if (i == currentIndex) "●" else "○",
                style = TextStyle(
                    color = ColorProvider(dotColor),
                    fontSize = 8.sp,
                ),
            )
            if (i < pageCount - 1) {
                Spacer(modifier = GlanceModifier.width(4.dp))
            }
        }
    }
}
