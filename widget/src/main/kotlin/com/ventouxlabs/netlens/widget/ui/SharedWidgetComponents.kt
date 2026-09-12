package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ventouxlabs.netlens.widget.WidgetState
import com.ventouxlabs.netlens.widget.action.DeeplinkUriKey
import com.ventouxlabs.netlens.widget.action.OpenDeeplinkAction
import com.ventouxlabs.netlens.widget.util.Deeplink

/**
 * The 2x2's public address, as a country marker line above the address itself.
 *
 * ## Why this is two lines and not one
 *
 * It was a Row — flag, then address — and that cost the address about 30dp of a 147dp
 * card, leaving ~117dp. A 15-character public IP (`185.199.108.153`, not just the
 * 13-character address a home network hands out) needs ~116dp at the 13sp this used to
 * carry, so the row was already at the edge of clipping *before* any of it grew, and
 * [WidgetType.VALUE_TIGHT] would have needed ~143dp and clipped outright. The address is
 * `maxLines = 1`, so clipping is what it would have done, and an ellipsized IP reads as a
 * different address.
 *
 * Dropping the flag to its own line hands the address the full 147dp, where
 * [WidgetType.VALUE_TIGHT] needs ~143dp on a 1.15 device. That is the tightest fit in this
 * change — the site to look at first if an address ellipsizes — but it is the only
 * arrangement in which the 2x2's payload can be the 2x2's biggest text, and the card has
 * ~157dp of vertical surplus to spend on the extra line.
 *
 * Nothing here carries vertical weight; the outer [Column] is a plain content child of the
 * 2x2's Column. See the invariant in [FourByTwoVariant].
 */
@Composable
internal fun WidgetIpRow(state: WidgetState, showCountryName: Boolean = false) {
    Column(
        modifier = GlanceModifier.fillMaxWidth().clickable(
            actionRunCallback<OpenDeeplinkAction>(
                actionParametersOf(DeeplinkUriKey to Deeplink.IPINFO),
            ),
        ),
    ) {
        val hasCountryName = showCountryName && state.countryName.isNotEmpty()
        if (state.countryFlag.isNotEmpty() || hasCountryName) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.countryFlag.isNotEmpty()) {
                    Text(
                        text = state.countryFlag,
                        style = TextStyle(fontSize = widgetSp(WidgetType.FLAG)),
                    )
                }
                if (hasCountryName) {
                    Spacer(modifier = GlanceModifier.width(WidgetSpace.TIGHT))
                    Text(
                        text = state.countryName,
                        style = TextStyle(
                            color = NetLensWidgetColors.inkSoft,
                            fontSize = widgetSp(WidgetType.LABEL),
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
        Text(
            text = state.publicIp.ifEmpty { "—" },
            style = TextStyle(
                color = NetLensWidgetColors.ink,
                fontWeight = FontWeight.Bold,
                fontSize = widgetSp(WidgetType.VALUE_TIGHT),
            ),
            maxLines = 1,
        )
    }
}

/**
 * Absorbs surplus height so it is shared between a Column's sections instead of pooling
 * in a dead band at the bottom edge. Interleave one between and around the sections — N
 * sections take N+1 gaps — to get the even distribution that weighting the sections
 * themselves would give.
 *
 * This is the one sanctioned vertical weight: a Spacer has no children, so when an
 * overrun drives it to zero it takes nothing with it — unlike a weighted content Column,
 * which is deleted from the view tree along with its payload. See the invariant in
 * [FourByTwoVariant].
 */
@Composable
internal fun ColumnScope.SectionGap() {
    Spacer(modifier = GlanceModifier.defaultWeight())
}

/**
 * Full-width hairline rule between widget sections.
 *
 * [WidgetSpace.HAIRLINE], not a spacing step: a rule is a mark on the card rather than a
 * gap between things, so it does not round up to the rhythm's visible 4dp.
 */
@Composable
internal fun WidgetSectionDivider() {
    Spacer(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(WidgetSpace.HAIRLINE)
            .background(NetLensWidgetColors.line),
    )
}
