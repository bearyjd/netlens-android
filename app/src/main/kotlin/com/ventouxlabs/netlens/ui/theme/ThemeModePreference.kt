package com.ventouxlabs.netlens.ui.theme

import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.core.ui.ThemeMode

/**
 * Maps between core:ui's [ThemeMode] and the plain string persisted by
 * core:data (which deliberately has no UI-layer dependency).
 */
fun String.toThemeMode(): ThemeMode = when (this) {
    UserPreferencesRepository.THEME_MODE_LIGHT -> ThemeMode.LIGHT
    UserPreferencesRepository.THEME_MODE_DARK -> ThemeMode.DARK
    else -> ThemeMode.SYSTEM
}

fun ThemeMode.toPreferenceValue(): String = when (this) {
    ThemeMode.SYSTEM -> UserPreferencesRepository.THEME_MODE_SYSTEM
    ThemeMode.LIGHT -> UserPreferencesRepository.THEME_MODE_LIGHT
    ThemeMode.DARK -> UserPreferencesRepository.THEME_MODE_DARK
}
