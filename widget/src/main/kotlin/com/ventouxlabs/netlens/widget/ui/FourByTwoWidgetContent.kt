package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider
import com.ventouxlabs.netlens.widget.WidgetState

/**
 * Dispatches on the responsive size bucket `LocalSize` reports. Both trees survive a
 * short box; the bucket only decides which one reads better. See [FourByTwoVariant].
 */
@Composable
fun FourByTwoWidgetContent(state: WidgetState) {
    when (fourByTwoVariant(LocalSize.current.height)) {
        FourByTwoVariant.COMPACT -> FourByTwoCompactContent(state = state)
        FourByTwoVariant.FULL -> FourByTwoFullContent(state = state)
    }
}

/**
 * The design layout. Every *section* sits at its natural height: the only vertical weight
 * in this Column is on the childless [SectionGap] spacers, so an overrun clips from the
 * bottom instead of deleting children. See [FourByTwoVariant] for the invariant and the
 * measurements from when the sections themselves were weighted.
 */
@Composable
private fun FourByTwoFullContent(state: WidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .widgetBackground(),
    ) {
        FourByTwoHeader(
            state = state,
            modifier = GlanceModifier.padding(horizontal = 10.dp, vertical = 2.dp),
        )

        WidgetSectionDivider()

        SectionGap()

        DashboardWidgetContent(
            state = state,
            showHeader = false,
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )

        SectionGap()

        WidgetSectionDivider()

        if (state.latencyHistoryMs.size >= SPARKLINE_MIN_SAMPLES) {
            LatencySparkline(
                history = state.latencyHistoryMs,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }

        SectionGap()

        StatusAndChipsRow(state = state)

        SectionGap()
    }
}

/** DNS/device status beside the shortcut chips, split down the middle by a hairline. */
@Composable
private fun StatusAndChipsRow(state: WidgetState) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusLineContent(
            state = state,
            modifier = GlanceModifier.defaultWeight(),
        )

        // Fixed height, deliberately not fillMaxHeight. `match_parent` on a child of a
        // *wrap_content* Row does not resolve to the row's content height: LinearLayout
        // measures it with getChildMeasureSpec(AT_MOST(remaining), MATCH_PARENT), which
        // returns EXACTLY(remaining) — so the child inflates to every pixel left in the
        // column and pushes its siblings out. That is the opposite failure from a
        // weighted child collapsing to zero, and it is why nothing on this path asks
        // for height it cannot justify.
        Spacer(
            modifier = GlanceModifier
                .width(1.dp)
                .height(STATUS_DIVIDER_HEIGHT)
                .background(NetLensWidgetColors.line),
        )

        // Chips wrap to their content so the status line gets the leftover width; an
        // even split truncated "DNS -> x.x.x.x - Direct" while the chip half sat empty.
        ToolChipsRow(state = state)
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

/**
 * Samples needed before the sparkline reads as a trend rather than as a rendering
 * artifact. Glance state is per widget *instance*, so a freshly placed widget starts
 * with an empty history and `WidgetRefreshWorker` adds at most one sample per 30-minute
 * run (`appendLatencySample`, capped at 12). At the old threshold of 2 the first couple
 * of hours after placement drew two 4dp bars alone in a full-width band, which looks
 * like a bug rather than like a chart with little data. Five bars is the point the shape
 * carries information.
 */
private const val SPARKLINE_MIN_SAMPLES = 5

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

/** Height of the rule between the status line and the chip stack. */
private val STATUS_DIVIDER_HEIGHT = 36.dp
