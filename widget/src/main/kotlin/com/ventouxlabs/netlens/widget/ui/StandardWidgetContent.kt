package com.ventouxlabs.netlens.widget.ui

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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ventouxlabs.netlens.widget.WidgetState
import com.ventouxlabs.netlens.widget.action.DeeplinkUriKey
import com.ventouxlabs.netlens.widget.action.OpenDeeplinkAction
import com.ventouxlabs.netlens.widget.action.TriggerScanAction
import com.ventouxlabs.netlens.widget.util.Deeplink

@Composable
fun StandardWidgetContent(state: WidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(WidgetTheme.CORNER_RADIUS)
            .background(ColorProvider(WidgetTheme.BACKGROUND))
            .padding(WidgetTheme.PADDING),
    ) {
        HeaderRow(state)
        Spacer(modifier = GlanceModifier.height(8.dp))
        IpRow(state)
        Spacer(modifier = GlanceModifier.height(8.dp))
        StatsRow(state)
        Spacer(modifier = GlanceModifier.defaultWeight())
        FooterRow(state)
    }
}

@Composable
private fun HeaderRow(state: WidgetState) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val gradeText = if (state.hasScore) state.scoreGrade else "?"
        val gradeColor = if (state.hasScore) {
            WidgetTheme.scoreColor(state.scoreGrade)
        } else {
            WidgetTheme.SCORE_GRAY
        }
        Text(
            text = gradeText,
            style = TextStyle(
                color = ColorProvider(gradeColor),
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
            ),
            modifier = GlanceModifier.clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.POSTURE),
                ),
            ),
        )

        Spacer(modifier = GlanceModifier.width(10.dp))

        Column(
            modifier = GlanceModifier.defaultWeight().clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.WIFI_AUDIT),
                ),
            ),
        ) {
            val networkName = when {
                !state.isConnected -> "Disconnected"
                state.ssid != null -> state.ssid
                else -> "Mobile"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = networkName,
                    style = TextStyle(
                        color = ColorProvider(WidgetTheme.TEXT_PRIMARY),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    ),
                    maxLines = 1,
                )
                if (state.encryptionType.isNotEmpty()) {
                    val suffix = WidgetTheme.encryptionSuffix(state.encryptionType)
                    val color = WidgetTheme.encryptionColor(state.encryptionType)
                    Text(
                        text = " ${state.encryptionType}$suffix",
                        style = TextStyle(
                            color = ColorProvider(color),
                            fontSize = 11.sp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun IpRow(state: WidgetState) {
    WidgetIpRow(state = state, showCountryName = false)
}

@Composable
private fun StatsRow(state: WidgetState) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val latencyText = if (state.hasLatency) "${state.latencyMs} ms" else "— ms"
        Text(
            text = latencyText,
            style = TextStyle(
                color = ColorProvider(WidgetTheme.TEXT_SECONDARY),
                fontSize = 12.sp,
            ),
            modifier = GlanceModifier.clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.LATENCY),
                ),
            ),
        )

        Text(
            text = " · ",
            style = TextStyle(
                color = ColorProvider(WidgetTheme.TEXT_MUTED),
                fontSize = 12.sp,
            ),
        )

        val deviceText = "${state.deviceCount} device${if (state.deviceCount != 1) "s" else ""}"
        Text(
            text = deviceText,
            style = TextStyle(
                color = ColorProvider(WidgetTheme.TEXT_SECONDARY),
                fontSize = 12.sp,
            ),
            modifier = GlanceModifier.clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.DEVICES),
                ),
            ),
        )
    }
}

@Composable
private fun FooterRow(state: WidgetState) {
    val footerText = when {
        state.isScanRunning -> "Scanning…"
        state.lastScanTimestamp > 0L -> {
            val rel = WidgetTheme.relativeTime(state.lastScanTimestamp)
            if (state.isStale()) "Stale · $rel" else "Scanned $rel"
        }
        else -> "Not scanned"
    }
    val footerColor = if (state.isStale()) WidgetTheme.SCORE_AMBER else WidgetTheme.TEXT_MUTED
    Text(
        text = footerText,
        style = TextStyle(
            color = ColorProvider(footerColor),
            fontSize = 11.sp,
        ),
        modifier = GlanceModifier.fillMaxWidth().clickable(
            actionRunCallback<TriggerScanAction>(),
        ),
    )
}
