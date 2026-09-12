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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
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
 * [compact] collapses the two-per-row stack into a single row for
 * [FourByTwoVariant.COMPACT], and drops the chip text from [WidgetType.BODY] to
 * [WidgetType.LABEL] — the only difference between the two forms now that padding is one
 * rhythm step everywhere.
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
        StackedToolChips(
            chipRows = resolveToolChips(state.chipRoutes).chunked(2),
            modifier = modifier,
            portalBackground = portalBackground,
            portalOnBackground = portalOnBackground,
        )
    }
}

@Composable
private fun StackedToolChips(
    chipRows: List<List<ChipDefinition>>,
    modifier: GlanceModifier,
    portalBackground: ColorProvider,
    portalOnBackground: ColorProvider,
) {
    Column(
        modifier = modifier
            .padding(start = WidgetSpace.TIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chipRows.forEachIndexed { rowIndex, rowChips ->
            if (rowIndex > 0) Spacer(GlanceModifier.height(WidgetSpace.TIGHT))
            Row(verticalAlignment = Alignment.CenterVertically) {
                rowChips.forEachIndexed { index, chip ->
                    if (index > 0) Spacer(GlanceModifier.width(WidgetSpace.TIGHT))
                    ToolChip(
                        label = chip.shortLabel,
                        action = deeplinkAction(chip),
                        background = NetLensWidgetColors.accentSoft,
                        onBackground = NetLensWidgetColors.onAccentSoft,
                    )
                }
            }
        }
        if (chipRows.isNotEmpty()) Spacer(GlanceModifier.height(WidgetSpace.TIGHT))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolChip(
                label = PORTAL_LABEL,
                action = actionRunCallback<OpenPortalAction>(),
                background = portalBackground,
                onBackground = portalOnBackground,
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
            compact = true,
        )
        chips.forEach { chip ->
            Spacer(GlanceModifier.width(WidgetSpace.TIGHT))
            ToolChip(
                label = chip.shortLabel,
                action = deeplinkAction(chip),
                background = NetLensWidgetColors.accentSoft,
                onBackground = NetLensWidgetColors.onAccentSoft,
                compact = true,
            )
        }
    }
}

private fun deeplinkAction(chip: ChipDefinition): Action = actionRunCallback<OpenDeeplinkAction>(
    actionParametersOf(DeeplinkUriKey to Deeplink.forRoute(chip.route)),
)

@Composable
private fun ToolChip(
    label: String,
    action: Action,
    background: ColorProvider,
    onBackground: ColorProvider,
    compact: Boolean = false,
) {
    Text(
        text = label,
        modifier = GlanceModifier
            .cornerRadius(4.dp)
            .background(background)
            .padding(horizontal = WidgetSpace.TIGHT, vertical = WidgetSpace.TIGHT)
            .clickable(action),
        style = TextStyle(
            fontSize = widgetSp(if (compact) WidgetType.LABEL else WidgetType.BODY),
            color = onBackground,
        ),
        maxLines = 1,
    )
}
