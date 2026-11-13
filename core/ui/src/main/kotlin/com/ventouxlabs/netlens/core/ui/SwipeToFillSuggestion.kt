package com.ventouxlabs.netlens.core.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Lets the user swipe toward the end edge (right in LTR) across an empty text field to fill in its
 * example value — e.g. Ping's `8.8.8.8` placeholder — instead of typing it.
 *
 * Only active while [enabled] (callers pass "the field is empty"), so a swipe can never replace text
 * the user typed. The gesture is observed in the [PointerEventPass.Initial] pass and consumed only
 * once it is clearly a horizontal swipe toward the end edge: taps still focus the field, and
 * vertical drags still scroll the screen. Swipe gestures are invisible to TalkBack, so the same
 * fill is also exposed as a custom accessibility action labelled [actionLabel].
 */
fun Modifier.swipeToFillSuggestion(
    suggestion: String,
    enabled: Boolean,
    actionLabel: String,
    onFill: (String) -> Unit,
): Modifier = composed {
    val currentOnFill by rememberUpdatedState(onFill)
    val currentSuggestion by rememberUpdatedState(suggestion)
    val haptics = LocalHapticFeedback.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val thresholdPx = with(LocalDensity.current) { SwipeToFill.THRESHOLD.toPx() }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f

    if (!enabled) return@composed this

    this
        .semantics {
            customActions = listOf(
                CustomAccessibilityAction(actionLabel) {
                    currentOnFill(currentSuggestion)
                    true
                },
            )
        }
        .pointerInput(direction, touchSlop, thresholdPx) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var dx = 0f
                var dy = 0f
                var claimed = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        // Consume the release too, so the field's own tap handling does not treat a
                        // completed swipe as a tap and move the cursor.
                        if (claimed) change.consume()
                        break
                    }
                    val delta = change.positionChangeIgnoreConsumed()
                    dx += delta.x * direction
                    dy += delta.y
                    if (!claimed) {
                        when (SwipeToFill.classify(dx, dy, touchSlop)) {
                            SwipeToFill.Intent.Undecided -> Unit
                            SwipeToFill.Intent.Other -> break
                            SwipeToFill.Intent.FillSwipe -> claimed = true
                        }
                    }
                    if (claimed) change.consume()
                }
                if (claimed && SwipeToFill.completes(dx, dy, thresholdPx)) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnFill(currentSuggestion)
                }
            }
        }
}

/** Gesture thresholds, kept free of Compose types so they can be unit-tested. */
internal object SwipeToFill {
    /** Distance toward the end edge a swipe must travel to fill the field. */
    val THRESHOLD = 56.dp

    enum class Intent { Undecided, FillSwipe, Other }

    /**
     * Decides, once the pointer has moved past [touchSlop], whether the gesture is a fill swipe
     * (mostly horizontal, toward the end edge) or something else (a scroll, or a swipe toward the
     * start edge) that must be left alone. [dx] is already normalised so positive means "toward
     * the end edge".
     */
    fun classify(dx: Float, dy: Float, touchSlop: Float): Intent = when {
        abs(dx) <= touchSlop && abs(dy) <= touchSlop -> Intent.Undecided
        dx > 0f && dx > abs(dy) * 2f -> Intent.FillSwipe
        else -> Intent.Other
    }

    /** Whether a claimed swipe travelled far enough, still mostly horizontally, to fill. */
    fun completes(dx: Float, dy: Float, thresholdPx: Float): Boolean =
        dx >= thresholdPx && dx > abs(dy) * 2f
}
