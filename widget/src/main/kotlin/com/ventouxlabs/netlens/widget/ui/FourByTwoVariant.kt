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
 * `defaultWeight()` on a Column child is a defect unless that child is a bare Spacer.
 * Likewise, `fillMaxHeight()` on a *Row* child can consume all remaining height and
 * evict its vertical siblings; it is a defect when the Row shares a vertical container
 * with content that must remain visible.
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
 * Which of the 4x2 widget's three layouts to render — a purely cosmetic choice.
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
 * [FULL] is the design layout, and the three are no longer the same tree with sizes turned
 * down. FULL runs full-width stacked rows on the 4x1's pattern: the flag and lock inline
 * in its status row, the device count and encryption on a row of their own, and every
 * chip across one weighted row. [SHORT] preserves that header, full-width address hierarchy,
 * status, and action footer but omits the lowest-priority device/encryption detail row.
 * [COMPACT] keeps the side-by-side arrangement a short box needs — [FourByTwoVpnColumn]
 * beside the addresses, the status line beside a wrap-to-content chip row — and drops the
 * ISP name and device count with it.
 *
 * FULL now gives WAN and LAN separate full-width rows, with 28sp addresses. The Pixel 9
 * Pro Fold launcher’s observed 341×317dp widget minimum leaves ~325dp of address interior;
 * the ~246dp 15-character IPv4 estimate fits there and device verification found no
 * ellipsis. Another launcher could allocate the declared 250dp responsive bucket, leaving
 * ~234dp and potentially ellipsizing that address. Responsive Glance supplies the bucket
 * rather than the actual allocation, so no width branch can make that case safe; it remains
 * unverified.
 */
internal enum class FourByTwoVariant { COMPACT, SHORT, FULL }

/**
 * The 4x2 declares three [androidx.glance.appwidget.SizeMode.Responsive] buckets: 110dp,
 * 200dp, and 260dp tall. `LocalSize` reports the *bucket* rather than the true box, so the
 * thresholds select COMPACT below 200dp, SHORT from 200dp through 259dp, and FULL at 260dp
 * and above.
 *
 * This number is not load-bearing. Since neither layout can drop children, moving the
 * threshold (or the buckets) only changes which layout you see at a given size; it
 * cannot reintroduce the collapse above. Don't "fix" it expecting a correctness effect.
 */
private val SHORT_VARIANT_MIN_HEIGHT = 200.dp
private val FULL_VARIANT_MIN_HEIGHT = 260.dp

internal fun fourByTwoVariant(height: Dp): FourByTwoVariant =
    when {
        height < SHORT_VARIANT_MIN_HEIGHT -> FourByTwoVariant.COMPACT
        height < FULL_VARIANT_MIN_HEIGHT -> FourByTwoVariant.SHORT
        else -> FourByTwoVariant.FULL
    }
