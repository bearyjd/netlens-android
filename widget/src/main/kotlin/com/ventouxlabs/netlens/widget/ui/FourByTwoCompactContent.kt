package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import com.ventouxlabs.netlens.widget.WidgetState

/**
 * The 4x2 rendered for a short box — see [FourByTwoVariant] for what separates the two
 * variants and why the choice between them is cosmetic.
 *
 * Like FULL, this Column carries no vertical weight, so content packs to the top and
 * any surplus sits in one band at the bottom. Two properties follow, and the second is
 * the reason the ordering is what it is:
 *
 * 1. **Nothing in this Column requests height**, so the worst case is content running
 *    past the bottom edge — never a child resolving to zero and vanishing.
 * 2. **The addresses come before anything negotiable.** A LinearLayout that overruns
 *    loses its trailing children, so ordering header → addresses → status/chips makes
 *    "the WAN and LAN addresses render" true by construction rather than by arithmetic.
 *
 * No dp budget is asserted here on purpose. The heights this layout needs have never
 * been measured on a device; only the failure it replaces was (see [FourByTwoVariant]),
 * and the guarantee above does not depend on the arithmetic coming out right.
 */
@Composable
internal fun FourByTwoCompactContent(state: WidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .widgetBackground(),
    ) {
        FourByTwoHeader(
            state = state,
            modifier = GlanceModifier.padding(horizontal = 10.dp, vertical = 1.dp),
        )

        WidgetSectionDivider()

        DashboardWidgetContent(
            state = state,
            showHeader = false,
            compact = true,
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )

        WidgetSectionDivider()

        // The `defaultWeight`s below divide *width*, which is never the scarce axis: this
        // row's only fixed content is 20dp of padding inside a box at least 250dp wide.
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusLineContent(
                state = state,
                compact = true,
                modifier = GlanceModifier.defaultWeight(),
            )

            ToolChipsRow(
                state = state,
                compact = true,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}
