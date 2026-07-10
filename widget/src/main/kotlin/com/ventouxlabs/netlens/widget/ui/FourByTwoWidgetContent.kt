package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider
import com.ventouxlabs.netlens.widget.WidgetState

@Composable
fun FourByTwoWidgetContent(state: WidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .widgetBackground(),
    ) {
        WidgetHeaderRow(
            state = state,
            modifier = GlanceModifier.padding(horizontal = 10.dp),
        )

        Spacer(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(NetLensWidgetColors.line),
        )

        DashboardWidgetContent(
            state = state,
            showHeader = false,
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )

        Spacer(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(NetLensWidgetColors.line),
        )

        if (state.latencyHistoryMs.size >= 2) {
            LatencySparkline(
                history = state.latencyHistoryMs,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusLineContent(
                state = state,
                modifier = GlanceModifier.defaultWeight(),
            )

            Spacer(
                modifier = GlanceModifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(NetLensWidgetColors.line),
            )

            ToolChipsRow(
                state = state,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

/**
 * Compact bar sparkline of the most recent latency samples (ms), bottom-aligned.
 * Bars are colored by the three-state status semantics (teal/amber/red) and
 * sized 4..16dp relative to the largest sample in the visible history.
 */
@Composable
private fun LatencySparkline(history: List<Int>, modifier: GlanceModifier) {
    val maxSample = (history.maxOrNull() ?: 1).coerceAtLeast(1)
    Row(
        modifier = modifier.height(SPARKLINE_MAX_BAR_HEIGHT),
        verticalAlignment = Alignment.Bottom,
    ) {
        history.forEach { sample ->
            Box(
                modifier = GlanceModifier
                    .width(SPARKLINE_BAR_WIDTH)
                    .height(sparklineBarHeight(sample, maxSample))
                    .background(sparklineBarColor(sample)),
            ) {}
            Spacer(modifier = GlanceModifier.width(SPARKLINE_BAR_SPACING))
        }
    }
}

private val SPARKLINE_MIN_BAR_HEIGHT = 4.dp
private val SPARKLINE_MAX_BAR_HEIGHT = 16.dp
private val SPARKLINE_BAR_WIDTH = 4.dp
private val SPARKLINE_BAR_SPACING = 2.dp

/** Scales [sample] relative to [maxSample] into the 4..16dp bar height range. */
internal fun sparklineBarHeight(sample: Int, maxSample: Int): Dp {
    val range = SPARKLINE_MAX_BAR_HEIGHT - SPARKLINE_MIN_BAR_HEIGHT
    val fraction = (sample.toFloat() / maxSample.coerceAtLeast(1)).coerceIn(0f, 1f)
    return SPARKLINE_MIN_BAR_HEIGHT + range * fraction
}

private fun sparklineBarColor(sample: Int): ColorProvider = when {
    sample > 400 -> NetLensWidgetColors.stamp
    sample > 150 -> NetLensWidgetColors.warn
    else -> NetLensWidgetColors.accent
}
