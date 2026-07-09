package com.ventouxlabs.netlens.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.ventouxlabs.netlens.core.ui.DarkStatusColors
import com.ventouxlabs.netlens.core.ui.LightStatusColors
import com.ventouxlabs.netlens.core.ui.LocalStatusColors
import com.ventouxlabs.netlens.core.ui.ThemeMode

/**
 * NetLens theme. Follows the system light/dark setting by default; a manual
 * override arrives via [themeMode] (persisted in Settings).
 *
 * Dynamic (wallpaper) color is intentionally NOT supported: it silently
 * replaced the brand palette on Android 12+ and broke the fixed three-state
 * status semantics (teal = normal, amber = warning, stamp red = alert).
 */
@Composable
fun NetLensTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkNetLensColorScheme else LightNetLensColorScheme,
        typography = NetLensTypography,
        shapes = NetLensShapes,
    ) {
        CompositionLocalProvider(
            LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors,
            content = content,
        )
    }
}
