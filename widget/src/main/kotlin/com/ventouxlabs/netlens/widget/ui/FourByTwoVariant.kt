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
 * So the check a reviewer runs is: grep `defaultWeight()` across `widget/.../ui`, and for
 * every hit ask which axis it divides. On a Row child it divides width and is fine. On a
 * Column child it divides height, and the only legal carriers are the childless
 * `SectionGap` spacers — used by the 4x2 FULL tree, the 4x1 and the 2x1 to spread surplus
 * evenly between sections without letting any content view bid for height.
 *
 * The grep is not self-explaining, because the same call means opposite things two lines
 * apart: [CompactFullContent] holds a `defaultWeight()` *inside* a Row — dividing width,
 * to push a section label to the trailing edge — directly above address `Text`s that are
 * Column children and deliberately carry none. Those addresses were Row children before
 * the 2x1 was restructured, and carried the modifier harmlessly. Moving a view between a
 * Row and a Column silently changes what its weight does; that is the edit to watch for.
 *
 * ── The third failure mode: more than ten children ──────────────────────────────────
 *
 * **A Glance container renders at most ten children. The eleventh onwards are dropped.**
 *
 * Glance ships *generated* layouts, one per child count, and they stop at ten. In the
 * 1.1.1 AAR:
 *
 * ```
 *   $ unzip -l glance-appwidget-1.1.1.aar | grep -oE "column_start_null_[0-9]+children"
 *   column_start_null_0children … column_start_null_10children     <- and no further
 * ```
 *
 * Eleven variants, for 0..10, and the same for every `row_*`, `box_*` and alignment
 * combination. A composable that emits an eleventh child has nowhere to put it: no crash,
 * no log, nothing a JVM test can observe — the same signature as the other two failures.
 *
 * Measured on device, with [FourByTwoWidgetContent]'s FULL Column at thirteen children
 * (header, divider, gap, addresses, gap, status, gap, detail, gap, divider, gap, chips,
 * gap):
 *
 * ```
 *   header      y=931..1021   90px
 *   hairline    y=1021..1023   2px
 *   [gap 97px]
 *   addresses   y=1120..1277 157px
 *   [gap 97px]
 *   status      y=1374..1433  59px
 *   [gap 98px]
 *   detail      y=1531..1577  46px
 *   [gap 98px]
 *   hairline    y=1675..1677   2px   <- child #10, the last one rendered
 *                                    <- chips row and its two gaps: never emitted
 * ```
 *
 * It is the *trailing* children that go, as with a RemoteViews row overflow, so the
 * ordering rule the COMPACT variant already follows — payload first, negotiable content
 * last — is the mitigation for this too.
 *
 * Two things to know when fixing it. Nesting buys budget: a child Column has its own ten,
 * which is why the 4x2 groups its status and detail lines ([FourByTwoStatusBlock]) and
 * the 2x2 groups each address pair and its foot. But nesting cannot hold a [SectionGap]:
 * only the root Column is `fillMaxSize`, so a weighted Spacer inside a wrap-content child
 * divides a surplus of zero. Gaps must stay in the root and are therefore the expensive
 * thing to spend slots on — which is why both widgets now take most of their spacing from
 * fixed vertical padding on the rows and keep only three gaps.
 *
 * The check a reviewer runs is to count the children each container emits, remembering
 * that a `SectionGap`, a divider and an `if`-guarded row are each one. The counts as of
 * this pass: 4x1 root 7, 4x2 FULL root 9, 4x2 COMPACT root 5, 2x2 root 7, 2x1 root 9.
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
 * [FourByTwoCompactAddressRow]. Both variants now clip from the bottom under overrun
 * rather than deleting children.
 *
 * The sparkline in that measurement no longer exists: five 4dp bars alone in a full-width
 * band read as a rendering artifact rather than as a chart, so FULL dropped it when it was
 * restructured. The measurement is kept as written because it is the record of the
 * failure, not a description of the current tree.
 *
 * ## What the variants actually differ in
 *
 * [FULL] is the design layout, and the two are no longer the same tree with sizes turned
 * down. FULL runs full-width stacked rows on the 4x1's pattern: the flag and lock inline
 * in its status row, the device count and encryption on a row of their own, and every
 * chip across one weighted row. [COMPACT] keeps the side-by-side arrangement a short box
 * needs — [FourByTwoVpnColumn] beside the addresses, the status line beside a
 * wrap-to-content chip row — and drops the ISP name and the device count with it.
 *
 * Neither height has been measured on a device. Adding up natural text heights puts FULL
 * near 176dp at `fontScale` 1.15 in a 306dp box, leaving ~130dp for its six [SectionGap]s
 * — about 22dp each, against roughly 16dp on the 4x1 it is modelled on. That is
 * arithmetic, not a measurement: treat it as the number to check if the card reads loose,
 * not as a target.
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
