package com.ventoux.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
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
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ventoux.netlens.widget.WidgetState
import com.ventoux.netlens.widget.action.DeeplinkUriKey
import com.ventoux.netlens.widget.action.OpenDeeplinkAction
import com.ventoux.netlens.widget.action.OpenPortalAction
import com.ventoux.netlens.widget.action.RunDnsCheckAction
import com.ventoux.netlens.widget.action.RunPingAction
import com.ventoux.netlens.widget.util.Deeplink

@Composable
fun ToolChipsRow(state: WidgetState, modifier: GlanceModifier = GlanceModifier) {
    val pingBg = when (state.chipPingResult) {
        "fail" -> WidgetTheme.CHIP_BAD
        "", "running" -> WidgetTheme.CHIP_DEFAULT
        else -> WidgetTheme.CHIP_GOOD
    }
    val pingLabel = when (state.chipPingResult) {
        "" -> "Ping"
        "running" -> "..."
        "fail" -> "✗"
        else -> "${state.chipPingResult}ms"
    }
    val dnsBg = when (state.chipDnsResult) {
        "leak" -> WidgetTheme.CHIP_BAD
        "clean" -> WidgetTheme.CHIP_GOOD
        else -> WidgetTheme.CHIP_DEFAULT
    }
    val dnsLabel = when (state.chipDnsResult) {
        "" -> "DNS"
        "running" -> "..."
        "clean" -> "Clean"
        "leak" -> "Leak!"
        else -> "DNS"
    }
    val portalBg = if (state.isCaptivePortal) {
        WidgetTheme.CHIP_PORTAL_ACTIVE
    } else {
        WidgetTheme.CHIP_DEFAULT
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(start = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolChip(
                icon = "🔍",
                label = "LAN",
                action = actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.DEVICES),
                ),
                background = WidgetTheme.CHIP_DEFAULT,
            )
            Spacer(GlanceModifier.width(4.dp))
            ToolChip(
                icon = "📡",
                label = pingLabel,
                action = actionRunCallback<RunPingAction>(),
                background = pingBg,
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolChip(
                icon = "🌐",
                label = dnsLabel,
                action = actionRunCallback<RunDnsCheckAction>(),
                background = dnsBg,
            )
            Spacer(GlanceModifier.width(4.dp))
            ToolChip(
                icon = "🚪",
                label = "Portal",
                action = actionRunCallback<OpenPortalAction>(),
                background = portalBg,
            )
        }
    }
}

@Composable
private fun ToolChip(icon: String, label: String, action: Action, background: Color) {
    Text(
        text = "$icon$label",
        modifier = GlanceModifier
            .cornerRadius(4.dp)
            .background(ColorProvider(background))
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clickable(action),
        style = TextStyle(
            fontSize = 20.sp,
            color = ColorProvider(WidgetTheme.TEXT_PRIMARY),
        ),
        maxLines = 1,
    )
}
