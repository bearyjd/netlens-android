package com.ventoux.netlens.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.flow.first
import com.ventoux.netlens.widget.action.CarouselNextAction
import com.ventoux.netlens.widget.action.CarouselPrevAction
import com.ventoux.netlens.widget.action.CopyGatewayAction
import com.ventoux.netlens.widget.action.CopyPublicIpAction
import com.ventoux.netlens.widget.action.DeeplinkUriKey
import com.ventoux.netlens.widget.action.OpenAppAction
import com.ventoux.netlens.widget.action.OpenDeeplinkAction
import com.ventoux.netlens.widget.data.WidgetPreferencesRepository
import com.ventoux.netlens.widget.model.WidgetColor
import com.ventoux.netlens.widget.model.WidgetPage
import com.ventoux.netlens.widget.model.WidgetPreferences
import com.ventoux.netlens.widget.model.WidgetSize
import com.ventoux.netlens.widget.model.WidgetTextSize
import com.ventoux.netlens.widget.util.Deeplink
import com.ventoux.netlens.widget.util.toFlagEmoji

private const val FLAG_EMOJI_SIZE_OFFSET = 4
private const val LABEL_SIZE_OFFSET = -2
private const val DETAIL_SIZE_OFFSET = -1

private enum class NavDirection { PREV, NEXT }

class NetLensWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<Preferences> = IpWidgetStateDefinition

    // widgetPrefs is a snapshot — changes only apply after refreshAllWidgets() re-triggers provideGlance.
    // WidgetSettingsViewModel.applyToWidget() handles this by calling refreshAllWidgets().
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
    val textColor = if (widgetPrefs.backgroundColor == WidgetColor.WHITE) Color.Black else Color.White
    val textSizeSp = widgetPrefs.textSize.sp.sp

    val baseModifier = GlanceModifier
        .fillMaxSize()
        .cornerRadius(widgetPrefs.cornerRadius.dp)
        .background(ColorProvider(bgColor))

    when (widgetPrefs.widgetSize) {
        WidgetSize.SMALL -> SmallWidgetContent(
            state = state,
            prefs = widgetPrefs,
            pageIndex = pageIndex,
            textColor = textColor,
            accentColor = accentColor,
            textSizeSp = textSizeSp,
            modifier = baseModifier,
        )
        WidgetSize.MEDIUM -> MediumWidgetContent(
            state = state,
            prefs = widgetPrefs,
            pageIndex = pageIndex,
            textColor = textColor,
            accentColor = accentColor,
            textSizeSp = textSizeSp,
            modifier = baseModifier,
        )
        WidgetSize.WIDE -> WideWidgetContent(
            state = state,
            textColor = textColor,
            accentColor = accentColor,
            textSizeSp = textSizeSp,
            modifier = baseModifier,
        )
        WidgetSize.BANNER -> BannerWidgetContent(
            state = state,
            textColor = textColor,
            accentColor = accentColor,
            modifier = baseModifier,
        )
    }
}

@Composable
private fun SmallWidgetContent(
    state: IpWidgetState,
    prefs: WidgetPreferences,
    pageIndex: Int,
    textColor: Color,
    accentColor: Color,
    textSizeSp: androidx.compose.ui.unit.TextUnit,
    modifier: GlanceModifier = GlanceModifier,
) {
    val pages = prefs.pages
    val safeIndex = if (pages.isEmpty()) 0 else pageIndex.coerceIn(0, pages.lastIndex)
    val currentPage = pages.getOrNull(safeIndex) ?: WidgetPage.CONNECTION

    val clickAction = if (state.isConnected && pages.size > 1) {
        actionRunCallback<CarouselNextAction>()
    } else {
        actionRunCallback<OpenAppAction>()
    }

    Column(
        modifier = modifier
            .clickable(clickAction)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!state.isConnected) {
            DisconnectedContent(textColor = textColor, textSizeSp = textSizeSp)
        } else {
            when (currentPage) {
                WidgetPage.CONNECTION -> ConnectionContent(
                    state = state,
                    textColor = textColor,
                    textSizeSp = textSizeSp,
                    accentColor = accentColor,
                    showNavArrows = false,
                )
                WidgetPage.NETWORK -> NetworkContent(
                    state = state,
                    textColor = textColor,
                    textSizeSp = textSizeSp,
                    accentColor = accentColor,
                    showNavArrows = false,
                )
            }
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
private fun MediumWidgetContent(
    state: IpWidgetState,
    prefs: WidgetPreferences,
    pageIndex: Int,
    textColor: Color,
    accentColor: Color,
    textSizeSp: androidx.compose.ui.unit.TextUnit,
    modifier: GlanceModifier = GlanceModifier,
) {
    val pages = prefs.pages
    val safeIndex = if (pages.isEmpty()) 0 else pageIndex.coerceIn(0, pages.lastIndex)
    val currentPage = pages.getOrNull(safeIndex) ?: WidgetPage.CONNECTION
    val showArrows = pages.size > 1

    Column(
        modifier = modifier
            .clickable(actionRunCallback<OpenAppAction>())
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!state.isConnected) {
            DisconnectedContent(textColor = textColor, textSizeSp = textSizeSp)
        } else {
            when (currentPage) {
                WidgetPage.CONNECTION -> ConnectionContent(
                    state = state,
                    textColor = textColor,
                    textSizeSp = textSizeSp,
                    accentColor = accentColor,
                    showNavArrows = showArrows,
                )
                WidgetPage.NETWORK -> NetworkContent(
                    state = state,
                    textColor = textColor,
                    textSizeSp = textSizeSp,
                    accentColor = accentColor,
                    showNavArrows = showArrows,
                )
            }
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
private fun WideWidgetContent(
    state: IpWidgetState,
    textColor: Color,
    accentColor: Color,
    textSizeSp: androidx.compose.ui.unit.TextUnit,
    modifier: GlanceModifier = GlanceModifier,
) {
    Column(
        modifier = modifier
            .clickable(actionRunCallback<OpenAppAction>())
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!state.isConnected) {
            DisconnectedContent(textColor = textColor, textSizeSp = textSizeSp)
        } else {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = GlanceModifier.defaultWeight()) {
                    Column(verticalAlignment = Alignment.CenterVertically) {
                        ConnectionContent(
                            state = state,
                            textColor = textColor,
                            textSizeSp = textSizeSp,
                            accentColor = accentColor,
                            showNavArrows = false,
                        )
                    }
                }

                Box(
                    modifier = GlanceModifier
                        .width(1.dp)
                        .height(48.dp)
                        .background(ColorProvider(textColor.copy(alpha = 0.2f))),
                ) {}

                Box(modifier = GlanceModifier.defaultWeight()) {
                    Column(
                        modifier = GlanceModifier.padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NetworkContent(
                            state = state,
                            textColor = textColor,
                            textSizeSp = textSizeSp,
                            accentColor = accentColor,
                            showNavArrows = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BannerWidgetContent(
    state: IpWidgetState,
    textColor: Color,
    accentColor: Color,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    val textSizeSp = WidgetTextSize.SMALL.sp.sp

    Row(
        modifier = modifier
            .clickable(actionRunCallback<OpenAppAction>())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!state.isConnected) {
            DisconnectedContent(textColor = textColor, textSizeSp = textSizeSp)
        } else {
            if (state.countryCode.isNotEmpty()) {
                Text(
                    text = state.countryCode.toFlagEmoji(),
                    style = TextStyle(fontSize = (textSizeSp.value + FLAG_EMOJI_SIZE_OFFSET).sp),
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
            }

            val vpnDot = if (state.isVpn) "●" else "○"
            Text(
                text = "${vpnDot}${context.getString(R.string.widget_vpn_label)}",
                style = TextStyle(
                    color = ColorProvider(if (state.isVpn) accentColor else textColor.copy(alpha = 0.5f)),
                    fontSize = (textSizeSp.value + LABEL_SIZE_OFFSET).sp,
                ),
            )

            Spacer(modifier = GlanceModifier.width(6.dp))

            Text(
                text = state.publicIp.ifEmpty { "—" },
                style = TextStyle(
                    color = ColorProvider(textColor),
                    fontWeight = FontWeight.Bold,
                    fontSize = textSizeSp,
                ),
                maxLines = 1,
            )

            DotSeparator(textColor, textSizeSp)

            Text(
                text = state.ssid ?: "—",
                style = TextStyle(
                    color = ColorProvider(textColor.copy(alpha = 0.7f)),
                    fontSize = textSizeSp,
                ),
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1,
            )

            DotSeparator(textColor, textSizeSp)

            Text(
                text = "${context.getString(R.string.widget_gateway_label)} ${state.gateway ?: "—"}",
                style = TextStyle(
                    color = ColorProvider(textColor.copy(alpha = 0.7f)),
                    fontSize = textSizeSp,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DotSeparator(textColor: Color, textSizeSp: androidx.compose.ui.unit.TextUnit) {
    Text(
        text = " · ",
        style = TextStyle(
            color = ColorProvider(textColor.copy(alpha = 0.4f)),
            fontSize = textSizeSp,
        ),
    )
}

@Composable
private fun DisconnectedContent(
    textColor: Color,
    textSizeSp: androidx.compose.ui.unit.TextUnit,
) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "○",
            style = TextStyle(
                color = ColorProvider(textColor.copy(alpha = 0.4f)),
                fontSize = (textSizeSp.value + FLAG_EMOJI_SIZE_OFFSET).sp,
            ),
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = context.getString(R.string.widget_no_connection),
            style = TextStyle(
                color = ColorProvider(textColor.copy(alpha = 0.6f)),
                fontSize = textSizeSp,
            ),
        )
    }
}

@Composable
private fun ConnectionContent(
    state: IpWidgetState,
    textColor: Color,
    textSizeSp: androidx.compose.ui.unit.TextUnit,
    accentColor: Color,
    showNavArrows: Boolean,
) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.countryCode.isNotEmpty()) {
            Text(
                text = state.countryCode.toFlagEmoji(),
                style = TextStyle(fontSize = (textSizeSp.value + FLAG_EMOJI_SIZE_OFFSET).sp),
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
            text = "${vpnDot}${context.getString(R.string.widget_vpn_label)}",
            style = TextStyle(
                color = ColorProvider(if (state.isVpn) accentColor else textColor.copy(alpha = 0.5f)),
                fontSize = (textSizeSp.value + LABEL_SIZE_OFFSET).sp,
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

        if (showNavArrows) {
            Spacer(modifier = GlanceModifier.width(4.dp))
            NavArrow(direction = NavDirection.NEXT, textColor = textColor)
        }
    }

    Spacer(modifier = GlanceModifier.height(2.dp))

    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ssidText = state.ssid ?: context.getString(R.string.widget_not_connected)
        val localIpText = state.localIp ?: "—"
        Text(
            text = "$ssidText · $localIpText",
            style = TextStyle(
                color = ColorProvider(textColor.copy(alpha = 0.7f)),
                fontSize = (textSizeSp.value + LABEL_SIZE_OFFSET).sp,
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
private fun NetworkContent(
    state: IpWidgetState,
    textColor: Color,
    textSizeSp: androidx.compose.ui.unit.TextUnit,
    accentColor: Color,
    showNavArrows: Boolean,
) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showNavArrows) {
            NavArrow(direction = NavDirection.PREV, textColor = textColor)
            Spacer(modifier = GlanceModifier.width(4.dp))
        }

        Text(
            text = context.getString(R.string.widget_gateway_label),
            style = TextStyle(
                color = ColorProvider(textColor.copy(alpha = 0.6f)),
                fontSize = (textSizeSp.value + LABEL_SIZE_OFFSET).sp,
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
            text = context.getString(R.string.widget_dns_label),
            style = TextStyle(
                color = ColorProvider(textColor.copy(alpha = 0.6f)),
                fontSize = (textSizeSp.value + LABEL_SIZE_OFFSET).sp,
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
                fontSize = (textSizeSp.value + DETAIL_SIZE_OFFSET).sp,
            ),
            modifier = dnsModifier,
            maxLines = 1,
        )
    }
}

@Composable
private fun NavArrow(direction: NavDirection, textColor: Color) {
    val action = when (direction) {
        NavDirection.NEXT -> actionRunCallback<CarouselNextAction>()
        NavDirection.PREV -> actionRunCallback<CarouselPrevAction>()
    }
    Text(
        text = if (direction == NavDirection.NEXT) "›" else "‹",
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
