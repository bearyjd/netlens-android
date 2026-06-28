package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

@Composable
fun SignalBarsIcon(level: Int, modifier: GlanceModifier = GlanceModifier) {
    val bars = buildString {
        for (i in 0 until 4) {
            append(if (i < level) "▮" else "▯")
        }
    }
    Text(
        text = bars,
        style = TextStyle(
            color = ColorProvider(WidgetTheme.rssiColor(level)),
            fontSize = 10.sp,
        ),
        modifier = modifier,
    )
}
