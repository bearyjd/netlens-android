package com.ventouxlabs.netlens.widget.ui

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FourByTwoVariantTest {

    // The 4x2 declares two responsive height buckets, 110dp and 200dp, and LocalSize
    // reports the bucket rather than the true box — so 110 and 200 are the only two
    // heights this selector sees in production. The rest pin the threshold itself.
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
    fun `the 200dp bucket selects FULL`() {
        assertEquals(FourByTwoVariant.FULL, fourByTwoVariant(200.dp))
    }

    @Test
    fun `a 158dp fold-outer row selects COMPACT`() {
        // The box that started this: a 4x2 placed 5 cols x 1 row on a Pixel 9 Pro Fold
        // outer display, 427x158dp. Asserted at the real box height as well as at the
        // 110dp bucket, so the mapping holds whichever of the two LocalSize reports.
        assertEquals(FourByTwoVariant.COMPACT, fourByTwoVariant(158.dp))
    }

    @Test
    fun `179dp is still COMPACT and 180dp is FULL`() {
        assertEquals(FourByTwoVariant.COMPACT, fourByTwoVariant(179.dp))
        assertEquals(FourByTwoVariant.FULL, fourByTwoVariant(180.dp))
    }

    @Test
    fun `a tall resized box selects FULL`() {
        assertEquals(FourByTwoVariant.FULL, fourByTwoVariant(317.dp))
    }

    @Test
    fun `a degenerate zero height selects COMPACT rather than throwing`() {
        assertEquals(FourByTwoVariant.COMPACT, fourByTwoVariant(0.dp))
    }
}
