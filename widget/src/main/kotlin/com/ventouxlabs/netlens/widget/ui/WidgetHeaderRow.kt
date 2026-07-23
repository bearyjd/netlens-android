package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ventouxlabs.netlens.widget.R
import com.ventouxlabs.netlens.widget.WidgetState
import com.ventouxlabs.netlens.widget.action.TriggerScanAction
import com.ventouxlabs.netlens.widget.util.relativeTimeLabel

private val SHOW_TIMESTAMP_MIN_HEIGHT = 60.dp
private val GENEROUS_PAD_MIN_HEIGHT = 80.dp

internal fun shouldShowHeaderTimestamp(height: Dp): Boolean = height >= SHOW_TIMESTAMP_MIN_HEIGHT

internal fun headerVerticalPad(height: Dp): Dp = if (height >= GENEROUS_PAD_MIN_HEIGHT) 4.dp else 2.dp

@Composable
fun WidgetHeaderRow(state: WidgetState, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val size = LocalSize.current
    val showTimestamp = shouldShowHeaderTimestamp(size.height)
    val verticalPad = headerVerticalPad(size.height)

    val elapsed = relativeTimeLabel(state.lastRefreshMs)
    val stale = state.lastRefreshMs > 0L &&
        System.currentTimeMillis() - state.lastRefreshMs > WidgetState.STALE_ALERT_THRESHOLD_MS

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = verticalPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showTimestamp && elapsed.isNotEmpty()) {
            Text(
                text = "Scanned $elapsed",
                style = TextStyle(
                    color = if (stale) NetLensWidgetColors.warn else NetLensWidgetColors.inkSoft,
                    fontSize = widgetSp(11f),
                ),
                maxLines = 1,
            )
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
        Image(
            provider = ImageProvider(R.drawable.ic_refresh),
            contentDescription = context.getString(R.string.widget_refresh_content_description),
            colorFilter = ColorFilter.tint(NetLensWidgetColors.inkSoft),
            modifier = GlanceModifier
                .size(20.dp)
                .cornerRadius(10.dp)
                .padding(2.dp)
                .clickable(actionRunCallback<TriggerScanAction>()),
        )
    }
}
