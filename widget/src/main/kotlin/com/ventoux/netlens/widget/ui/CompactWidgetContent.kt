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
import androidx.glance.layout.fillMaxWidth
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
fun CompactWidgetContent(state: WidgetState) {
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
            .padding(WidgetTheme.PADDING),
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
                fontSize = 24.sp,
            ),
        )

        Spacer(modifier = GlanceModifier.width(10.dp))

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
                fontSize = 14.sp,
            ),
            modifier = GlanceModifier.defaultWeight(),
            maxLines = 1,
        )

        Spacer(modifier = GlanceModifier.width(8.dp))

        val statusText = when {
            !state.hasScore -> "Not scanned"
            state.hasIssues -> "${state.issueCount} issue${if (state.issueCount != 1) "s" else ""}"
            else -> "All clear"
        }
        val statusColor = when {
            !state.hasScore -> WidgetTheme.TEXT_MUTED
            state.hasIssues -> WidgetTheme.SCORE_AMBER
            else -> WidgetTheme.SCORE_GREEN
        }
        Text(
            text = statusText,
            style = TextStyle(
                color = ColorProvider(statusColor),
                fontSize = 12.sp,
            ),
            maxLines = 1,
        )
    }
}
