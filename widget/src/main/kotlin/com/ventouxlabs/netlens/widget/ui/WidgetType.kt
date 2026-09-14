package com.ventouxlabs.netlens.widget.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The type scale every widget text size is drawn from.
 *
 * Sizes are plain `Float` design sizes, never `TextUnit`, so that a call site **cannot**
 * use one without routing it through [widgetSp] — `fontSize = WidgetType.LABEL` does not
 * compile, `fontSize = widgetSp(WidgetType.LABEL)` does. That is the point: a raw `12.sp`
 * bypasses the font-scale cap entirely and overflows a fixed widget cell at accessibility
 * scales, which is the failure [widgetSp] exists to prevent. There is consequently no
 * `.sp` literal anywhere under `widget/.../ui` — `grep -n "[0-9]\.sp"` returns nothing,
 * including in this file.
 *
 * ## The floor
 *
 * **12sp is the floor. Nothing goes below it.** The 4x1/4x2 components were raised to it
 * deliberately for legibility; the 2x1 was missed in that pass and still carried 8sp and
 * 9sp text, which is what "fonts are not aligned" was describing.
 *
 * ## Picking a role
 *
 * Pick by what the text *is*, not by what fits — if a role does not fit, the layout is
 * wrong, not the role. The two address roles are the exception, and the distinction
 * between them is width, because an address is [maxLines][androidx.glance.text.Text] 1 and
 * an ellipsized IP reads as a different address:
 *
 * | Role          |    | Use for                                                     |
 * |---------------|----|-------------------------------------------------------------|
 * | [LABEL]       | 12 | "WAN"/"LAN", ISP, VPN caption, footer, scan time, chips-in-a-half-card |
 * | [BODY]        | 14 | status lines, the 4x2 bottom row, full-width chips           |
 * | [VALUE_TIGHT] | 16 | an address in a width-constrained column                     |
 * | [VALUE]       | 18 | an address where width allows (the 4x1's full-width columns) |
 * | [ADDRESS_FULL]| 28 | a 4x2 FULL address at the observed 341dp launcher minimum (250dp unverified) |
 * | [HERO]        | 20 | the security grade letter in the 4x2's single-line header    |
 * | [DISPLAY]     | 28 | the security grade letter as the 2x2's standalone anchor     |
 *
 * ## Sizing an address column
 *
 * Rendered width at `fontScale` f is `designSp * min(f, 1.15)` (see [widgetSp]), and a
 * bold IPv4 needs roughly `0.51dp` per character per sp of that. So a 13-character
 * `192.168.1.129` at [VALUE_TIGHT] on a 1.15 device needs `13 * 0.51 * 16 * 1.15` ≈
 * **122dp**, and a 15-character `185.199.108.153` ≈ **143dp**. Calibrated against the one
 * measurement there is: the 4x2 renders a 13-character address at 17sp in a ~132dp column
 * on a Pixel 10 Pro Fold at `fontScale` 1.15, which this model puts at 129dp — i.e. it
 * runs slightly pessimistic, so a site it says fits, fits.
 */
internal object WidgetType {
    /** Captions and secondary labels. The floor — nothing may go below this. */
    const val LABEL = 12f

    /** Ordinary reading text: status lines, stat rows, chips. */
    const val BODY = 14f

    /** An address sharing its row with something else, or in a half-card. */
    const val VALUE_TIGHT = 16f

    /** An address owning a column wide enough for the 15-character worst case. */
    const val VALUE = 18f

    /**
     * The 4x2 FULL's full-width address at the Pixel launcher’s observed 341dp minimum.
     *
     * This is intentionally distinct from [DISPLAY]: it is readable payload, not a
     * security-grade display glyph. The 325dp interior fits the ~246dp 15-character
     * estimate at `fontScale` 1.15 and was device-verified without ellipsis. Another
     * launcher could allocate the declared 250dp bucket (~234dp interior), where it may
     * ellipsize; see [FourByTwoFullWidthWanAddress].
     */
    const val ADDRESS_FULL = 28f

    /** A single character carrying a whole card's headline signal, inline in a row. */
    const val HERO = 20f

    /**
     * As [HERO], but standing alone rather than leading a row.
     *
     * Beyond the five roles the 2x2 needs it: its card has ~157dp of vertical surplus, and
     * the grade letter is its only anchor, so sizing it *down* to [HERO] would worsen the
     * emptiness this scale exists to fix. Nothing else should reach for it.
     */
    const val DISPLAY = 28f

    /**
     * Country-flag emoji. A glyph, not text — it carries no reading load, so it is sized
     * to sit with the type around it rather than to be read. It still goes through
     * [widgetSp]: an uncapped emoji grows the row it anchors just as text does.
     */
    const val FLAG = 16f

    /** Flag as the 4x2's standalone column anchor, where it is the column's whole subject. */
    const val FLAG_LARGE = 24f

    /**
     * The `▮▯▮▯` signal-bar run. Box-drawing glyphs rather than prose, but they sit beside
     * [LABEL] text in three layouts and were the last thing under the 12sp floor.
     */
    const val GLYPH_BARS = 12f
}

/**
 * The spacing rhythm. Three values, and a layout picks one rather than a number.
 *
 * What is *not* spacing, and therefore not drawn from here: element sizes (a 44dp column,
 * a 20dp icon, a 22dp badge), corner radii, and [WidgetSectionDivider]'s 1dp — a rule is a
 * mark on the card, not a gap between things, so it stays [HAIRLINE] rather than rounding
 * up to a visible 4dp band.
 *
 * Rounding an existing value that sat between two steps was decided by the width budget,
 * not by taste: on a row whose payload is a `maxLines = 1` address, the step that leaves
 * the address more room wins.
 */
internal object WidgetSpace {
    /** Between related things: a glyph and its label, chips in a row, chip padding. */
    val TIGHT: Dp = 4.dp

    /** Between sections of a card, and card edge padding on the wider widgets. */
    val BASE: Dp = 8.dp

    /** Card edge padding where the box can afford it. */
    val LOOSE: Dp = 12.dp

    /** A rule's thickness. Not a gap — see the note above. */
    val HAIRLINE: Dp = 1.dp
}
