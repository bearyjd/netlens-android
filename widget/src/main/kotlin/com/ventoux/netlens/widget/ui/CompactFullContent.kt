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

/**
 * 2x1 widget content with feature parity to the 2x2:
 *   row 1: score · flag · WAN IP
 *   row 2: latency · devices · scanned-ago (or "Tap to scan" when never scanned)
 */
@Composable
fun CompactFullContent(state: WidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(WidgetTheme.CORNER_RADIUS)
            .background(ColorProvider(WidgetTheme.BACKGROUND))
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        TopRow(state)
        Spacer(modifier = GlanceModifier.height(1.dp))
        BottomRow(state)
    }
}

@Composable
private fun TopRow(state: WidgetState) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
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
                fontSize = 14.sp,
            ),
            modifier = GlanceModifier.clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.POSTURE),
                ),
            ),
        )

        Spacer(modifier = GlanceModifier.width(4.dp))

        if (state.countryFlag.isNotEmpty()) {
            Text(
                text = state.countryFlag,
                style = TextStyle(fontSize = 12.sp),
            )
            Spacer(modifier = GlanceModifier.width(3.dp))
        }

        Text(
            text = state.publicIp.ifEmpty { "—.—.—.—" },
            style = TextStyle(
                color = ColorProvider(WidgetTheme.TEXT_PRIMARY),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            ),
            modifier = GlanceModifier.defaultWeight().clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.IPINFO),
                ),
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun BottomRow(state: WidgetState) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val latencyText = if (state.hasLatency) "${state.latencyMs}ms" else "—ms"
        Text(
            text = latencyText,
            style = TextStyle(
                color = ColorProvider(WidgetTheme.TEXT_SECONDARY),
                fontSize = 10.sp,
            ),
            modifier = GlanceModifier.clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.LATENCY),
                ),
            ),
        )
        Dot()
        Text(
            text = "${state.deviceCount}dev",
            style = TextStyle(
                color = ColorProvider(WidgetTheme.TEXT_SECONDARY),
                fontSize = 10.sp,
            ),
            modifier = GlanceModifier.clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.DEVICES),
                ),
            ),
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        val elapsed = WidgetTheme.relativeTime(state.lastScanTimestamp)
        val isStale = state.isStale()
        val footerText = when {
            state.isScanRunning -> "…"
            elapsed.isNotEmpty() -> elapsed
            else -> "tap"
        }
        val footerColor = when {
            isStale -> WidgetTheme.SCORE_AMBER
            else -> WidgetTheme.TEXT_MUTED
        }
        Text(
            text = footerText,
            style = TextStyle(
                color = ColorProvider(footerColor),
                fontSize = 10.sp,
            ),
            modifier = GlanceModifier.clickable(actionRunCallback<TriggerScanAction>()),
            maxLines = 1,
        )
    }
}

@Composable
private fun Dot() {
    Text(
        text = " · ",
        style = TextStyle(
            color = ColorProvider(WidgetTheme.TEXT_MUTED),
            fontSize = 10.sp,
        ),
    )
}
