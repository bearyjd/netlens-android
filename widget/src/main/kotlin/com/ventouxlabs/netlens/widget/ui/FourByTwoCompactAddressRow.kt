package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.width
import com.ventouxlabs.netlens.widget.WidgetState

/**
 * The COMPACT 4x2's addresses: flag and lock in a column, then WAN and LAN.
 *
 * This was `DashboardWidgetContent`, shared by both 4x2 variants behind `showHeader` and
 * `compact` flags. FULL no longer uses it — it composes two full-width stacked address
 * blocks without the VPN column ([FourByTwoWidgetContent]) — so both flags had exactly
 * one value left and are gone with the branches they selected. The Row itself, and the
 * scope its two
 * `defaultWeight()`s are built in, are untouched: that pairing is the one thing in this
 * module that has rendered a widget blank when disturbed.
 *
 * The address Row is deliberately unweighted. It used to carry a vertical
 * `defaultWeight()`, and that is precisely where the addresses were lost: at a 158dp box
 * it was handed 18dp, `"WAN"` ate ~17dp of it, and the IP collapsed out of the view tree.
 * [FourByTwoVariant] has the full measurement and the invariant.
 *
 * Its `verticalAlignment` is unaffected by that removal — the Row's height is set by its
 * tallest child, the VPN column, so the addresses are still centred against the flag
 * exactly as before. They were never centred against surplus.
 */
@Composable
internal fun FourByTwoCompactAddressRow(state: WidgetState, modifier: GlanceModifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FourByTwoVpnColumn(state = state)
        Spacer(modifier = GlanceModifier.width(WidgetSpace.BASE))
        // defaultWeight is RowScope-scoped, so the address columns take it from here
        // rather than building it themselves.
        FourByTwoWanColumn(
            state = state,
            modifier = GlanceModifier.defaultWeight(),
            compact = true,
        )
        Spacer(modifier = GlanceModifier.width(WidgetSpace.BASE))
        FourByTwoLanColumn(
            state = state,
            modifier = GlanceModifier.defaultWeight(),
            compact = true,
        )
    }
}
