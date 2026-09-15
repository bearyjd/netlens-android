package com.ventouxlabs.netlens.widget.ui

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FourByTwoVariantTest {

    // The 4x2 declares three responsive height buckets: 110dp, 200dp, and 260dp. LocalSize
    // reports a bucket rather than the true box, while the non-bucket cases pin thresholds.
    //
    // The threshold picks which layout *reads* better, not which one survives: neither
    // variant carries vertical weight on a content path, so neither can drop children.
    // These tests exist to keep the two buckets mapping to different layouts, which is
    // the one thing a careless edit to the number would break.

    @Test
    fun `the 110dp bucket selects COMPACT`() {
        assertEquals(FourByTwoVariant.COMPACT, fourByTwoVariant(110.dp))
    }

    @Test
    fun `the 200dp bucket selects SHORT`() {
        assertEquals(FourByTwoVariant.SHORT, fourByTwoVariant(200.dp))
    }

    @Test
    fun `a 158dp fold-outer row selects COMPACT`() {
        // The box that started this: a 4x2 placed 5 cols x 1 row on a Pixel 9 Pro Fold
        // outer display, 427x158dp. Asserted at the real box height as well as at the
        // 110dp bucket, so the mapping holds whichever of the two LocalSize reports.
        assertEquals(FourByTwoVariant.COMPACT, fourByTwoVariant(158.dp))
    }

    @Test
    fun `a measured 245dp allocation selects SHORT`() {
        assertEquals(FourByTwoVariant.SHORT, fourByTwoVariant(245.dp))
    }

    @Test
    fun `the 260dp bucket and 306dp allocation select FULL`() {
        assertEquals(FourByTwoVariant.FULL, fourByTwoVariant(260.dp))
        assertEquals(FourByTwoVariant.FULL, fourByTwoVariant(306.dp))
    }

    @Test
    fun `a degenerate zero height selects COMPACT rather than throwing`() {
        assertEquals(FourByTwoVariant.COMPACT, fourByTwoVariant(0.dp))
    }
}
