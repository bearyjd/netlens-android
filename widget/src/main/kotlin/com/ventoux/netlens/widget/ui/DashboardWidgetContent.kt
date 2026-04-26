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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ventoux.netlens.widget.WidgetState
import com.ventoux.netlens.widget.action.DeeplinkUriKey
import com.ventoux.netlens.widget.action.OpenDeeplinkAction
import com.ventoux.netlens.widget.action.TriggerScanAction
import com.ventoux.netlens.widget.util.Deeplink

@Composable
fun DashboardWidgetContent(state: WidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(WidgetTheme.CORNER_RADIUS)
            .background(ColorProvider(WidgetTheme.BACKGROUND))
            .padding(WidgetTheme.PADDING),
    ) {
        DashboardHeader(state)
        Spacer(modifier = GlanceModifier.height(6.dp))
        NetworkRow(state)
        Spacer(modifier = GlanceModifier.height(6.dp))
        IpRow(state)
        Spacer(modifier = GlanceModifier.height(6.dp))
        SpeedRow(state)
        Spacer(modifier = GlanceModifier.height(6.dp))
        AlertRow(state)
        Spacer(modifier = GlanceModifier.height(6.dp))
        DashboardStatsRow(state)
        Spacer(modifier = GlanceModifier.defaultWeight())
        DashboardFooter(state)
    }
}

@Composable
private fun DashboardHeader(state: WidgetState) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Netlens",
            style = TextStyle(
                color = ColorProvider(WidgetTheme.TEXT_PRIMARY),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )

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
    }
}

@Composable
private fun NetworkRow(state: WidgetState) {
    Column(
        modifier = GlanceModifier.fillMaxWidth().clickable(
            actionRunCallback<OpenDeeplinkAction>(
                actionParametersOf(DeeplinkUriKey to Deeplink.WIFI_AUDIT),
            ),
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val networkName = when {
                !state.isConnected -> "Disconnected"
                state.ssid != null -> state.ssid
                else -> "Mobile"
            }
            Text(
                text = networkName,
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.TEXT_PRIMARY),
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
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

        val ispAsn = listOfNotNull(
            state.ispName.ifEmpty { null },
            state.asnName.ifEmpty { null },
        ).joinToString(" · ")
        if (ispAsn.isNotEmpty()) {
            Text(
                text = ispAsn,
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.TEXT_MUTED),
                    fontSize = 11.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun IpRow(state: WidgetState) {
    WidgetIpRow(state = state, showCountryName = true)
}

@Composable
private fun SpeedRow(state: WidgetState) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().clickable(
            actionRunCallback<OpenDeeplinkAction>(
                actionParametersOf(DeeplinkUriKey to Deeplink.SPEED_TEST),
            ),
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.hasSpeed) {
            val icon = WidgetTheme.speedIcon(state.speedLabel)
            val color = WidgetTheme.speedColor(state.speedLabel)
            val mbps = "%.0f Mbps".format(state.speedMbps)
            Text(
                text = "$icon ${state.speedLabel} · $mbps",
                style = TextStyle(
                    color = ColorProvider(color),
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                ),
            )
        } else {
            Text(
                text = "Not tested",
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.TEXT_MUTED),
                    fontSize = 13.sp,
                ),
            )
        }
    }
}

@Composable
private fun AlertRow(state: WidgetState) {
    if (state.hasIssues && state.topIssue.isNotEmpty()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.issue(state.topIssueId)),
                ),
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "● ",
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.SCORE_RED),
                    fontSize = 10.sp,
                ),
            )
            Text(
                text = state.topIssue,
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.TEXT_PRIMARY),
                    fontSize = 12.sp,
                ),
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1,
            )
            Text(
                text = " →",
                style = TextStyle(
                    color = ColorProvider(WidgetTheme.TEXT_MUTED),
                    fontSize = 12.sp,
                ),
            )
        }
    } else {
        Text(
            text = "✓ No threats detected",
            style = TextStyle(
                color = ColorProvider(WidgetTheme.SCORE_GREEN.copy(alpha = 0.7f)),
                fontSize = 12.sp,
            ),
        )
    }
}

@Composable
private fun DashboardStatsRow(state: WidgetState) {
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
private fun DashboardFooter(state: WidgetState) {
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
