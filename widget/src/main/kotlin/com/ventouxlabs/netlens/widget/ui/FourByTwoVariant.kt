package com.ventouxlabs.netlens.widget.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * ── The invariant this whole file exists to protect ─────────────────────────────────
 *
 * Vertical weight is allowed only on a view with nothing to lose.
 *
 * A childless `Spacer` collapsing to zero under overrun costs nothing; a content
 * `Column` collapsing to zero deletes its payload from the view tree. So
 * `defaultWeight()` on a Column child is a defect unless that child is a bare Spacer —
 * and `fillMaxHeight()` on a *Row* child is always fine, since it lowers to
 * `match_parent` where nothing competes for height.
 *
 * This tree takes no advantage of the Spacer carve-out — it contains no weighted views
 * on the vertical axis at all — so the check a reviewer actually runs is unconditional:
 * no `defaultWeight()` on any Column child, anywhere in the 4x2. Surplus height is left
 * as a band at the bottom rather than distributed, which is a deliberate trade of a
 * cosmetic for an invariant that needs no case analysis.
 *
 * Nothing enforces it. It is a review rule, not a CI check: a JVM test cannot observe
 * RemoteViews collapse, because the collapse happens in the launcher's LinearLayout
 * measure pass, and Glance composables are not renderable by Paparazzi (`:widget` does
 * not apply `netlens.android.screenshot`). The device-level check is a `uiautomator
 * dump` at a known box size, asserting the payload nodes are present in the tree — an
 * absent node means dropped, a present node with small bounds means merely clipped.
 */

/**
 * Which of the 4x2 widget's two layouts to render — a purely cosmetic choice.
 *
 * ## The failure this is *not* the fix for
 *
 * Glance lowers a Column child's `defaultWeight()` to `layout_weight=1` with
 * `height=0dp`. When a RemoteViews `LinearLayout` is overrun, such a child resolves to
 * zero height and **disappears from the view tree entirely** — no crash, no log. A
 * fixed-height child clips instead, which is recoverable.
 *
 * Measured on a Pixel 9 Pro Fold outer display, where the launcher placed the 4x2 in a
 * single 158dp grid row (root 386px = 158.4dp):
 *
 * ```
 *   header      80px = 32.8dp   fixed
 *   dividers     2px x 2        fixed
 *   sparkline   39px = 16dp     fixed
 *   remainder        105.6dp    split 50/50 between two weighted children
 *                               (measured 53.7dp and 54.2dp)
 * ```
 *
 * Inside the dashboard's 53.7dp, the Row holding the two addresses was *itself*
 * weighted and received 44px = 18dp. `"WAN"` at 13sp took ~17dp of that, so the IP
 * address got ~1dp and was dropped from the tree. The symptom looked intermittent
 * because the sparkline is conditional (`latencyHistoryMs.size >= 2`) and its 20dp
 * moves between the two weighted children.
 *
 * The fix was to delete three vertical weights — the two sections in
 * [FourByTwoWidgetContent]'s Column and the address Row inside
 * [DashboardWidgetContent]. Both variants now clip from the bottom under overrun
 * rather than deleting children.
 *
 * ## What the variants actually differ in
 *
 * [FULL] is the design layout. [COMPACT] is the same information reduced for a short
 * box: smaller type, no sparkline, no ISP name, no VPN caption, no device count, one
 * chip row instead of two.
 *
 * FULL's height requirement has never been measured on a device. Adding up natural
 * heights puts it near 261dp at `fontScale` 1.0 and near 285dp at `fontScale` >= 1.15,
 * where [widgetSp]'s ceiling stops scaling text but the 28dp VPN badge and 16dp
 * sparkline stay fixed. Both figures are estimates and both are larger than the 220dp
 * that used to be asserted here without a source — treat them as a reason to measure,
 * not as a target.
 *
 * Note where that height comes from: [FourByTwoVpnColumn] stacks six children for
 * roughly 106dp, against roughly 56dp for the address column beside it. A flag glyph
 * and a three-valued enum caption set the floor, not the payload.
 */
internal enum class FourByTwoVariant { COMPACT, FULL }

/**
 * The 4x2 declares two [androidx.glance.appwidget.SizeMode.Responsive] buckets, 110dp
 * and 200dp tall, and `LocalSize` reports the *bucket* rather than the true box — so any
 * threshold strictly between the two separates them. 180dp sits in that gap.
 *
 * This number is not load-bearing. Since neither layout can drop children, moving the
 * threshold (or the buckets) only changes which layout you see at a given size; it
 * cannot reintroduce the collapse above. Don't "fix" it expecting a correctness effect.
 */
private val FULL_VARIANT_MIN_HEIGHT = 180.dp

internal fun fourByTwoVariant(height: Dp): FourByTwoVariant =
    if (height < FULL_VARIANT_MIN_HEIGHT) FourByTwoVariant.COMPACT else FourByTwoVariant.FULL
