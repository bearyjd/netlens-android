package com.ventouxlabs.netlens.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * A UI-facing string that is either raw dynamic text (e.g. an exception message)
 * or a localized string resource, resolved lazily at the point of display.
 */
sealed interface UiText {
    data class Dynamic(val value: String) : UiText
    data class Resource(@StringRes val resId: Int, val args: List<Any> = emptyList()) : UiText

    companion object {
        fun of(dynamic: String?, @StringRes fallbackResId: Int, vararg fallbackArgs: Any): UiText =
            dynamic?.let { Dynamic(it) } ?: Resource(fallbackResId, fallbackArgs.toList())
    }
}

@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Dynamic -> value
    is UiText.Resource -> stringResource(resId, *args.toTypedArray())
}
