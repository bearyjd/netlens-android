package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ventouxlabs.netlens.widget.WidgetState
import com.ventouxlabs.netlens.widget.action.DeeplinkUriKey
import com.ventouxlabs.netlens.widget.action.OpenDeeplinkAction
import com.ventouxlabs.netlens.widget.util.Deeplink

@Composable
internal fun WidgetIpRow(state: WidgetState, showCountryName: Boolean = false) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().clickable(
            actionRunCallback<OpenDeeplinkAction>(
                actionParametersOf(DeeplinkUriKey to Deeplink.IPINFO),
            ),
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.countryFlag.isNotEmpty()) {
            Text(
                text = state.countryFlag,
                style = TextStyle(fontSize = if (showCountryName) 14.sp else 16.sp),
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
        }
        Text(
            text = state.publicIp.ifEmpty { "—" },
            style = TextStyle(
                color = ColorProvider(WidgetTheme.TEXT_PRIMARY),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            ),
            maxLines = 1,
        )
        if (showCountryName && state.countryName.isNotEmpty()) {
            Text(
                text = " · ${state.countryName}",
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.TEXT_SECONDARY),
                    fontSize = 12.sp,
                ),
                maxLines = 1,
            )
        }
    }
}
