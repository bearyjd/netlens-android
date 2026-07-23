package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ventouxlabs.netlens.widget.R
import com.ventouxlabs.netlens.widget.WidgetState
import com.ventouxlabs.netlens.widget.action.DeeplinkUriKey
import com.ventouxlabs.netlens.widget.action.OpenDeeplinkAction
import com.ventouxlabs.netlens.widget.action.TriggerScanAction
import com.ventouxlabs.netlens.widget.util.Deeplink
import com.ventouxlabs.netlens.widget.util.relativeTimeLabel

/**
 * 4x2-only header: leads with the security posture grade + its top issue (the
 * flagship signal the 4x1 doesn't surface), then the scan timestamp and refresh.
 * Tapping the grade/issue opens the security posture screen.
 */
@Composable
fun FourByTwoHeader(state: WidgetState, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val elapsed = relativeTimeLabel(state.lastRefreshMs)
    val stale = state.lastRefreshMs > 0L &&
        System.currentTimeMillis() - state.lastRefreshMs > WidgetState.STALE_ALERT_THRESHOLD_MS

    val gradeText = if (state.hasScore) state.scoreGrade else "?"
    val gradeColor = if (state.hasScore) {
        NetLensWidgetColors.scoreColor(state.scoreGrade)
    } else {
        NetLensWidgetColors.inkSoft
    }
    val (issueText, issueColor) = when {
        !state.hasScore -> "Tap to scan" to NetLensWidgetColors.inkSoft
        state.hasIssues -> state.topIssue to NetLensWidgetColors.scoreColor(state.scoreGrade)
        else -> "Secure" to NetLensWidgetColors.accent
    }

    val postureAction = actionRunCallback<OpenDeeplinkAction>(
        actionParametersOf(DeeplinkUriKey to Deeplink.POSTURE),
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = gradeText,
            style = TextStyle(
                color = gradeColor,
                fontWeight = FontWeight.Bold,
                fontSize = widgetSp(20f),
            ),
            modifier = GlanceModifier
                .cornerRadius(6.dp)
                .clickable(postureAction),
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = issueText,
            style = TextStyle(
                color = issueColor,
                fontWeight = FontWeight.Medium,
                fontSize = widgetSp(11f),
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight().clickable(postureAction),
        )
        if (elapsed.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = "Scanned $elapsed",
                style = TextStyle(
                    color = if (stale) NetLensWidgetColors.warn else NetLensWidgetColors.inkSoft,
                    fontSize = widgetSp(10f),
                ),
                maxLines = 1,
            )
        }
        Spacer(modifier = GlanceModifier.width(6.dp))
        Image(
            provider = ImageProvider(R.drawable.ic_refresh),
            contentDescription = context.getString(R.string.widget_refresh_content_description),
            colorFilter = ColorFilter.tint(NetLensWidgetColors.inkSoft),
            modifier = GlanceModifier
                .size(20.dp)
                .padding(2.dp)
                .clickable(actionRunCallback<TriggerScanAction>()),
        )
    }
}
