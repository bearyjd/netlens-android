package com.ventouxlabs.netlens.core.ui

import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Real pointer dispatch against a real text field; the helper tests in [SwipeToFillTest] cannot see these. */
@RunWith(RobolectricTestRunner::class)
class SwipeToFillInteractionTest {
    @get:Rule
    val rule = createComposeRule()

    private fun setField(initial: String) {
        rule.setContent {
            var input by remember { mutableStateOf(initial) }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .width(300.dp)
                    .testTag("host")
                    .swipeToFillSuggestion(
                        suggestion = "8.8.8.8",
                        enabled = input.isEmpty(),
                        actionLabel = "Use example 8.8.8.8",
                        onFill = { input = it },
                    ),
            )
        }
    }

    private fun swipe(finish: androidx.compose.ui.test.TouchInjectionScope.() -> Unit) {
        val distance = with(rule.density) { 120.dp.toPx() }
        rule.onNodeWithTag("host").performTouchInput {
            down(centerLeft + Offset(10f, 0f))
            moveBy(Offset(distance, 0f))
            finish()
        }
    }

    @Test
    fun completedSwipeFillsEmptyField() {
        setField("")
        swipe { up() }
        rule.onNodeWithTag("host").assertTextEquals("8.8.8.8")
    }

    @Test
    fun cancelledSwipeDoesNotFill() {
        setField("")
        swipe { cancel() }
        rule.onNodeWithTag("host").assertTextEquals("")
    }

    @Test
    fun swipeNeverReplacesExistingText() {
        setField("custom.example")
        swipe { up() }
        rule.onNodeWithTag("host").assertTextEquals("custom.example")
    }
}
