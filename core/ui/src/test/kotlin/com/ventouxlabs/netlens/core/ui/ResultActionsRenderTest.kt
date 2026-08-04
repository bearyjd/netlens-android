package com.ventouxlabs.netlens.core.ui

import androidx.compose.foundation.layout.Row
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

/**
 * Composition smoke tests — see `AndroidScreenshotConventionPlugin`.
 *
 * [ResultActions] is reached from 14 screens' `TopAppBar`, and until this file existed none of
 * them covered it: every `*ContentRenderTest` renders the stateless `*Content` that sits *below*
 * the Scaffold's `topBar`, and no test in the repo renders a `*Screen`. So the actions row was
 * verified only by "it compiles".
 *
 * These render each branch. They deliberately do **not** assert that the Pro gate hides the share
 * button — a JVM render cannot inspect the emitted tree, and the repo has no Robolectric or
 * instrumentation. That gate is instead structural: [ResultActions] takes `isPro` with no default,
 * so a call site cannot omit it.
 */
class ResultActionsRenderTest {

    @get:Rule
    val paparazzi = Paparazzi()

    // A Row mirrors TopAppBar's `actions` slot, which is where every caller puts this.
    private fun render(hasResults: Boolean, isPro: Boolean) = paparazzi.snapshot {
        Row {
            ResultActions(
                hasResults = hasResults,
                isPro = isPro,
                onCopy = {},
                copyContentDescription = "Copy results",
                onShare = {},
                shareContentDescription = "Share results",
            )
        }
    }

    @Test
    fun `nothing renders before there are results`() {
        render(hasResults = false, isPro = false)
    }

    @Test
    fun `nothing renders before there are results even for pro`() {
        render(hasResults = false, isPro = true)
    }

    @Test
    fun `a free user gets copy only`() {
        render(hasResults = true, isPro = false)
    }

    @Test
    fun `a pro user gets copy and share`() {
        render(hasResults = true, isPro = true)
    }
}
