package com.ventouxlabs.netlens.widget.ui

import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import com.ventouxlabs.netlens.core.ui.NetLensPalette

/**
 * Day/night color tokens for the Glance widgets, bridged from
 * [NetLensPalette] so widgets share the exact palette and three-state status
 * semantics (teal = normal, amber = warning, stamp red = alert) as the app.
 * Glance resolves the day/night pair against the system theme at render
 * time — widgets get full dark-mode support with no manual switching.
 *
 * This replaces the hardcoded, dark-only [WidgetTheme] as widget layouts are
 * redesigned; do not add new usages of [WidgetTheme].
 */
object NetLensWidgetColors {
    val background = ColorProvider(day = NetLensPalette.paper, night = NetLensPalette.paperDark)
    val card = ColorProvider(day = NetLensPalette.card, night = NetLensPalette.cardDark)
    val ink = ColorProvider(day = NetLensPalette.ink, night = NetLensPalette.inkOnDark)
    val inkSoft = ColorProvider(day = NetLensPalette.inkSoft, night = NetLensPalette.inkSoftOnDark)
    val line = ColorProvider(day = NetLensPalette.line, night = NetLensPalette.lineDark)

    val accent = ColorProvider(day = NetLensPalette.accent, night = NetLensPalette.accentBright)
    val accentSoft =
        ColorProvider(day = NetLensPalette.accentSoft, night = NetLensPalette.accentSoftDark)
    val onAccentSoft =
        ColorProvider(day = NetLensPalette.accentInk, night = NetLensPalette.accentBright)

    val warn = ColorProvider(day = NetLensPalette.warn, night = NetLensPalette.warnBright)
    val warnSoft =
        ColorProvider(day = NetLensPalette.warnSoft, night = NetLensPalette.warnSoftDark)
    val onWarnSoft =
        ColorProvider(day = NetLensPalette.warnInk, night = NetLensPalette.warnBright)

    val stamp = ColorProvider(day = NetLensPalette.stamp, night = NetLensPalette.stampBright)
    val stampSoft =
        ColorProvider(day = NetLensPalette.stampSoft, night = NetLensPalette.stampSoftDark)
    val onStampSoft =
        ColorProvider(day = NetLensPalette.stampInk, night = NetLensPalette.stampBright)

    /** Solid accent fill (stays deep teal in both modes) with legible text. */
    val accentFill = ColorProvider(NetLensPalette.accent)
    val onAccentFill = ColorProvider(NetLensPalette.card)
}
