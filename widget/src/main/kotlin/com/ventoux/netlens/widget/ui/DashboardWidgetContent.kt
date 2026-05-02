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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ventoux.netlens.widget.WidgetState
import com.ventoux.netlens.widget.action.DeeplinkUriKey
import com.ventoux.netlens.widget.action.OpenDeeplinkAction
import com.ventoux.netlens.widget.util.Deeplink

@Composable
fun DashboardWidgetContent(state: WidgetState) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(WidgetTheme.CORNER_RADIUS)
            .background(ColorProvider(WidgetTheme.BACKGROUND))
            .clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.HOME),
                ),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.publicIp.ifEmpty { "—.—.—.—" },
            style = TextStyle(
                color = ColorProvider(WidgetTheme.TEXT_PRIMARY),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            ),
            maxLines = 1,
        )

        Spacer(modifier = GlanceModifier.width(10.dp))

        val lanLabel = when {
            !state.isConnected -> "No network"
            state.ssid != null -> state.ssid
            else -> "Mobile"
        }
        Text(
            text = lanLabel,
            style = TextStyle(
                color = ColorProvider(WidgetTheme.TEXT_SECONDARY),
                fontSize = 12.sp,
            ),
            modifier = GlanceModifier.defaultWeight(),
            maxLines = 1,
        )

        Spacer(modifier = GlanceModifier.width(10.dp))

        if (state.vpnActive) {
            Text(
                text = "🛡️",
                style = TextStyle(fontSize = 14.sp),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
        }

        if (state.countryFlag.isNotEmpty()) {
            Text(
                text = state.countryFlag,
                style = TextStyle(fontSize = 16.sp),
            )
        } else {
            Text(
                text = "🌐",
                style = TextStyle(fontSize = 14.sp),
            )
        }
    }
}
