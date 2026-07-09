package com.ventouxlabs.netlens.core.ui

/**
 * User-selectable theme override. SYSTEM follows the OS light/dark setting;
 * LIGHT/DARK force a mode. Persisted via user preferences and consumed by
 * the app theme (`NetLensTheme`).
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}
