package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import com.ventouxlabs.netlens.widget.WidgetState

/**
 * Dispatches on the responsive size bucket `LocalSize` reports. Both trees survive a
 * short box; the bucket only decides which one reads better. See [FourByTwoVariant].
 */
@Composable
fun FourByTwoWidgetContent(state: WidgetState) {
    when (fourByTwoVariant(LocalSize.current.height)) {
        FourByTwoVariant.COMPACT -> FourByTwoCompactContent(state = state)
        FourByTwoVariant.SHORT -> FourByTwoShortContent(state = state)
        FourByTwoVariant.FULL -> FourByTwoFullContent(state = state)
    }
}

/**
 * The design layout: full-width stacked rows, on [DashboardFullContent]'s pattern.
 *
 * ## Why it is shaped like the 4x1
 *
 * It used to be a side-by-side split — a 52dp flag/badge/caption column beside the
 * addresses, and a status half beside a 2/1/1 chip stack, divided by a vertical hairline.
 * The 4x1 read as the better-composed card because of *structure*, not type size: it runs
 * its chips full width, keeps the flag and lock inline in its status row, and has no
 * sparkline. This tree now does the same three things, in the same order.
 *
 * ## Nine children, and why the count is load-bearing
 *
 * **Glance ships generated container layouts for 0..10 children only** — confirmed in the
 * 1.1.1 AAR, where `column_start_null_*children.xml` stops at `_10children`. An eleventh
 * child is dropped with no crash and no log. This Column had thirteen and lost its chip
 * row on device; the third failure mode in [FourByTwoVariant] records the measurement.
 *
 * So the budget is spent deliberately:
 *
 * ```
 *   1 header        5 gap            9 chips
 *   2 divider       6 status block
 *   3 gap           7 gap
 *   4 addresses     8 divider
 * ```
 *
 * The status line and the device/encryption line are one nested Column rather than two
 * children here, which buys a slot of headroom — a nested container has its own budget of
 * ten. Only three [SectionGap]s, where six is what an even N+1 distribution would want:
 * the space they used to fake now comes from real vertical padding on the rows, which is
 * attached to content and therefore reads as a band rather than as a void.
 *
 * What a gap still buys, and why they are not all padding: only the root Column is
 * `fillMaxSize`, so only weights *here* can absorb surplus, and they collapse to zero on
 * a short box where fixed padding would push the chip row off the bottom. Roughly 75dp
 * over three gaps is ~25dp each at a 306dp box — arithmetic on natural text heights, not
 * a measurement.
 *
 * ## The invariant
 *
 * The only vertical weight in this Column is on the childless [SectionGap] spacers, so an
 * overrun clips from the bottom instead of deleting children. Every other `defaultWeight`
 * below and in the rows this calls is a Row child dividing *width*. The nested status
 * block deliberately carries none: a weighted content Column is the first failure mode in
 * [FourByTwoVariant].
 */
@Composable
private fun FourByTwoFullContent(state: WidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .widgetBackground(),
    ) {
        // 1
        FourByTwoHeader(
            state = state,
            modifier = GlanceModifier.padding(horizontal = WidgetSpace.BASE, vertical = WidgetSpace.TIGHT),
        )

        // 2
        WidgetSectionDivider()

        // 3
        SectionGap()

        // 4 — the addresses are vertically stacked, so each gets the full interior width
        // rather than half of it. This nested container is still exactly one root child;
        // it cannot contain a SectionGap because only the root fills the widget's height.
        FourByTwoFullWidthAddresses(
            state = state,
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = WidgetSpace.BASE, vertical = WidgetSpace.LOOSE),
        )

        // 5
        SectionGap()

        // 6
        FourByTwoStatusBlock(
            state = state,
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = WidgetSpace.BASE, vertical = WidgetSpace.BASE),
        )

        // 7
        SectionGap()

        // 8
        WidgetSectionDivider()

        // 9 — last, and unpadded from below, so the chip row reads as a footer bar
        // against the bottom edge rather than floating above a void.
        ToolChipsRow(
            state = state,
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = WidgetSpace.BASE, vertical = WidgetSpace.LOOSE),
        )
    }
}

/**
 * The 200-259dp 4x2 form keeps the header, full-width addresses, status, and actions while
 * omitting only the lowest-priority detail row. Its root has six children, below Glance's
 * practical child-emission limit; the address column is nested rather than a root child pair.
 */
@Composable
private fun FourByTwoShortContent(state: WidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .widgetBackground(),
    ) {
        FourByTwoHeader(
            state = state,
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = WidgetSpace.BASE, vertical = WidgetSpace.TIGHT),
        )
        WidgetSectionDivider()
        FourByTwoFullWidthAddresses(
            state = state,
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = WidgetSpace.BASE, vertical = WidgetSpace.TIGHT),
        )
        FourByTwoStatusBlock(
            state = state,
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = WidgetSpace.BASE, vertical = WidgetSpace.TIGHT),
            includeDetail = false,
        )
        WidgetSectionDivider()
        ToolChipsRow(
            state = state,
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = WidgetSpace.BASE, vertical = WidgetSpace.TIGHT),
        )
    }
}

/** Keeps WAN and LAN full-width in one nested root child for the short and full layouts. */
@Composable
private fun FourByTwoFullWidthAddresses(
    state: WidgetState,
    modifier: GlanceModifier = GlanceModifier,
) {
    Column(modifier = modifier) {
        FourByTwoFullWidthWanAddress(
            state = state,
            modifier = GlanceModifier.fillMaxWidth(),
        )
        // Fixed spacing is valid in this nested container; only weighted SectionGaps need the
        // root Column's fillMaxSize surplus.
        Spacer(modifier = GlanceModifier.height(WidgetSpace.TIGHT))
        FourByTwoFullWidthLanAddress(
            state = state,
            modifier = GlanceModifier.fillMaxWidth(),
        )
    }
}
