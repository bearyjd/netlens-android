package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
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
import com.ventouxlabs.netlens.widget.WidgetState

/**
 * Flag/VPN, WAN and LAN across the top; signal, SSID and RSSI along the bottom.
 *
 * The address Row is deliberately unweighted on **both** paths. It used to carry a
 * vertical `defaultWeight()`, and that is precisely where the addresses were lost: at a
 * 158dp box it was handed 18dp, `"WAN"` ate ~17dp of it, and the IP collapsed out of the
 * view tree. [FourByTwoVariant] has the full measurement and the invariant.
 *
 * Its `verticalAlignment` is unaffected by that removal — the Row's height is set by its
 * tallest child, the ~106dp VPN column, so the addresses are still centred against the
 * flag exactly as before. They were never centred against surplus.
 */
@Composable
fun DashboardWidgetContent(
    state: WidgetState,
    showHeader: Boolean = true,
    compact: Boolean = false,
    modifier: GlanceModifier = GlanceModifier
        .fillMaxSize()
        .cornerRadius(16.dp)
        .background(NetLensWidgetColors.background)
        .padding(horizontal = 10.dp, vertical = 6.dp),
) {
    Column(modifier = modifier) {
        if (showHeader) {
            WidgetHeaderRow(state = state)
            Spacer(modifier = GlanceModifier.height(2.dp))
        }
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FourByTwoVpnColumn(state = state, compact = compact)
            Spacer(modifier = GlanceModifier.width(6.dp))
            // defaultWeight is RowScope-scoped, so the address columns take it from here
            // rather than building it themselves.
            FourByTwoWanColumn(
                state = state,
                modifier = GlanceModifier.defaultWeight(),
                compact = compact,
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            FourByTwoLanColumn(
                state = state,
                modifier = GlanceModifier.defaultWeight(),
                compact = compact,
            )
        }
        if (!compact) {
            Spacer(modifier = GlanceModifier.height(2.dp))
            FourByTwoBottomRow(state = state)
        }
    }
}
