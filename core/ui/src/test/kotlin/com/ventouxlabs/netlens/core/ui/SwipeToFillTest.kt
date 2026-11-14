package com.ventouxlabs.netlens.core.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SwipeToFillTest {

    private val slop = 8f
    private val threshold = 150f

    @Test
    fun `movement within touch slop is undecided`() {
        assertEquals(SwipeToFill.Intent.Undecided, SwipeToFill.classify(dx = 5f, dy = -6f, touchSlop = slop))
    }

    @Test
    fun `mostly horizontal movement toward the end edge is a fill swipe`() {
        assertEquals(SwipeToFill.Intent.FillSwipe, SwipeToFill.classify(dx = 20f, dy = 4f, touchSlop = slop))
    }

    @Test
    fun `movement toward the start edge is left alone`() {
        assertEquals(SwipeToFill.Intent.Other, SwipeToFill.classify(dx = -20f, dy = 0f, touchSlop = slop))
    }

    @Test
    fun `vertical movement is left alone so the screen can scroll`() {
        assertEquals(SwipeToFill.Intent.Other, SwipeToFill.classify(dx = 2f, dy = 30f, touchSlop = slop))
    }

    @Test
    fun `diagonal movement is left alone`() {
        assertEquals(SwipeToFill.Intent.Other, SwipeToFill.classify(dx = 20f, dy = 15f, touchSlop = slop))
    }

    @Test
    fun `swipe past the threshold completes`() {
        assertTrue(SwipeToFill.completes(dx = 160f, dy = 10f, thresholdPx = threshold))
    }

    @Test
    fun `short swipe does not complete`() {
        assertFalse(SwipeToFill.completes(dx = 100f, dy = 0f, thresholdPx = threshold))
    }

    @Test
    fun `swipe that drifted vertically does not complete`() {
        assertFalse(SwipeToFill.completes(dx = 160f, dy = 100f, thresholdPx = threshold))
    }
}
