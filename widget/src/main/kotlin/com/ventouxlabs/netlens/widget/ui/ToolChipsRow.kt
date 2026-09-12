package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ventouxlabs.netlens.widget.WidgetState
import com.ventouxlabs.netlens.widget.action.DeeplinkUriKey
import com.ventouxlabs.netlens.widget.action.OpenDeeplinkAction
import com.ventouxlabs.netlens.widget.action.OpenPortalAction
import com.ventouxlabs.netlens.widget.util.ChipCatalog
import com.ventouxlabs.netlens.widget.util.ChipDefinition
import com.ventouxlabs.netlens.widget.util.Deeplink

/**
 * How many tool chips the single-row compact form has width for beside [PORTAL_LABEL].
 *
 * Two, not three. [resolveToolChips] returns catalog order, so a user who selects
 * dnsleak + wifiaudit + ipinfo gets the three longest labels in the catalog — at
 * [WidgetType.LABEL] with [WidgetSpace.TIGHT] padding and spacers those run about 250dp,
 * against the ~203dp half-width available at the 427x158dp box this variant targets. The
 * third chip was being cut on the exact box the compact layout exists for. The rhythm
 * took chip padding from 3dp to 4dp, which widens each chip by 2dp and so only reinforces
 * the cap.
 */
private const val COMPACT_TOOL_CHIP_COUNT = 2

/** Also the user-visible chip label; the module hardcodes UI strings throughout. */
private const val PORTAL_LABEL = "Portal"

/**
 * Resolves [chipRoutes] against [ChipCatalog.ELIGIBLE], always in the catalog's declaration
 * order — never the input list's order — so the chip row stays visually stable across periodic
 * widget refreshes regardless of the `Set<String>` iteration order the routes were collected
 * from. Capped at [ChipCatalog.MAX_WIDGET_CHIPS].
 */
internal fun resolveToolChips(chipRoutes: List<String>): List<ChipDefinition> {
    val selected = chipRoutes.toSet()
    return ChipCatalog.ELIGIBLE
        .filter { it.route in selected }
        .take(ChipCatalog.MAX_WIDGET_CHIPS)
}

/**
 * Shortcut chips: up to [ChipCatalog.MAX_WIDGET_CHIPS] user-selected tools plus Portal.
 *
 * [compact] keeps the wrap-to-content row the COMPACT 4x2 needs beside its status line.
 * The default is the FULL form, which runs the full width of the card.
 */
@Composable
fun ToolChipsRow(
    state: WidgetState,
    compact: Boolean = false,
    modifier: GlanceModifier = GlanceModifier,
) {
    val portalBackground = if (state.isCaptivePortal) {
        NetLensWidgetColors.warnSoft
    } else {
        NetLensWidgetColors.accentSoft
    }
    val portalOnBackground = if (state.isCaptivePortal) {
        NetLensWidgetColors.onWarnSoft
    } else {
        NetLensWidgetColors.onAccentSoft
    }

    if (compact) {
        CompactToolChipsRow(
            chips = resolveToolChips(state.chipRoutes).take(COMPACT_TOOL_CHIP_COUNT),
            modifier = modifier,
            portalBackground = portalBackground,
            portalOnBackground = portalOnBackground,
        )
    } else {
        FullWidthToolChipsRow(
            chips = resolveToolChips(state.chipRoutes),
            modifier = modifier,
            portalBackground = portalBackground,
            portalOnBackground = portalOnBackground,
        )
    }
}

/**
 * Every chip on one full-width row, each cell an equal share of the card.
 *
 * This replaces a `chunked(2)` stack with a separate Portal row beneath it, which is what
 * the FULL 4x2 used while its chips lived in a narrow half-card beside the status line.
 * Stacking is what that width forced; at full width it just leaves a hole. The shape here
 * is [DashboardFullContent]'s — weighted cells, [WidgetSpace.TIGHT] between them — and at
 * a 427dp box five cells come out ~79dp each, which carries the catalog's longest label
 * ("WiFi Audit", ~77dp at [WidgetType.LABEL] on a `fontScale` 1.15 device) and not much
 * more. Narrower placements clip the label inside its cell rather than dropping it: a
 * weighted child is measured at the width the weight gives it, so the count of chips on
 * the row never changes with the box.
 *
 * Portal leads. That is the one deliberate difference from the 4x1, which renders it
 * last; it costs nothing here and keeps the chip the captive-portal state needs at the
 * end of the row that a right-to-left overrun would reach first.
 *
 * Every `defaultWeight()` below divides *width* and is built in the scope of the Row that
 * consumes it. See the invariant in [FourByTwoVariant].
 */
@Composable
private fun FullWidthToolChipsRow(
    chips: List<ChipDefinition>,
    modifier: GlanceModifier,
    portalBackground: ColorProvider,
    portalOnBackground: ColorProvider,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FullWidthChip(
            label = PORTAL_LABEL,
            action = actionRunCallback<OpenPortalAction>(),
            background = portalBackground,
            onBackground = portalOnBackground,
            modifier = GlanceModifier.defaultWeight(),
        )
        chips.forEach { chip ->
            Spacer(modifier = GlanceModifier.width(WidgetSpace.TIGHT))
            FullWidthChip(
                label = chip.shortLabel,
                action = deeplinkAction(chip),
                background = NetLensWidgetColors.accentSoft,
                onBackground = NetLensWidgetColors.onAccentSoft,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

/**
 * One row, Portal first.
 *
 * Portal leads deliberately, because a horizontal RemoteViews row that overflows drops
 * its *trailing* children. Width here is genuinely tight and varies with the placement:
 * this half is roughly 203dp at the 427dp box the compact variant targets, where two
 * chips plus Portal fit, but only about 115dp at the declared `minWidth` of 250dp, where
 * even two long labels overflow. Chips are the one thing in the family sized by what
 * fits rather than by what they are, which is why they sit at [WidgetType.LABEL] here.
 *
 * So an overflow is expected at narrow placements, and Portal-first decides what it
 * costs: a tool chip the user explicitly configured disappears rather than the Portal
 * chip. That is deliberate degradation, not a bug — the Portal chip is the one the
 * compact variant exists to rescue, and a dropped tool chip is still reachable from the
 * app. See [COMPACT_TOOL_CHIP_COUNT] for why the cap is two.
 *
 * Note that this is the *unweighted* row, and the only one that can drop a chip;
 * [FullWidthToolChipsRow] clips inside a cell instead.
 */
@Composable
private fun CompactToolChipsRow(
    chips: List<ChipDefinition>,
    modifier: GlanceModifier,
    portalBackground: ColorProvider,
    portalOnBackground: ColorProvider,
) {
    Row(
        modifier = modifier.padding(start = WidgetSpace.TIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolChip(
            label = PORTAL_LABEL,
            action = actionRunCallback<OpenPortalAction>(),
            background = portalBackground,
            onBackground = portalOnBackground,
        )
        chips.forEach { chip ->
            Spacer(GlanceModifier.width(WidgetSpace.TIGHT))
            ToolChip(
                label = chip.shortLabel,
                action = deeplinkAction(chip),
                background = NetLensWidgetColors.accentSoft,
                onBackground = NetLensWidgetColors.onAccentSoft,
            )
        }
    }
}

private fun deeplinkAction(chip: ChipDefinition): Action = actionRunCallback<OpenDeeplinkAction>(
    actionParametersOf(DeeplinkUriKey to Deeplink.forRoute(chip.route)),
)

/**
 * A chip that fills the cell its weight gives it, with the label centred in it.
 *
 * The Row wrapper is what centres the label; a weighted `Text` would sit against the
 * leading edge of its cell. Matches [DashboardFullContent]'s chip exactly — 6dp radius,
 * [WidgetSpace.TIGHT] padding, [WidgetType.LABEL] at [FontWeight.Medium].
 */
@Composable
private fun FullWidthChip(
    label: String,
    action: Action,
    background: ColorProvider,
    onBackground: ColorProvider,
    modifier: GlanceModifier,
) {
    Row(
        modifier = modifier
            .cornerRadius(6.dp)
            .background(background)
            .padding(horizontal = WidgetSpace.TIGHT, vertical = WidgetSpace.TIGHT)
            .clickable(action),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = onBackground,
                fontSize = widgetSp(WidgetType.LABEL),
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

/** The wrap-to-content chip the COMPACT row uses: no cell to fill, so no Row around it. */
@Composable
private fun ToolChip(
    label: String,
    action: Action,
    background: ColorProvider,
    onBackground: ColorProvider,
) {
    Text(
        text = label,
        modifier = GlanceModifier
            .cornerRadius(4.dp)
            .background(background)
            .padding(horizontal = WidgetSpace.TIGHT, vertical = WidgetSpace.TIGHT)
            .clickable(action),
        style = TextStyle(
            fontSize = widgetSp(WidgetType.LABEL),
            color = onBackground,
        ),
        maxLines = 1,
    )
}
