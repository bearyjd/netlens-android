package com.ventouxlabs.netlens.core.ui

import androidx.compose.ui.graphics.Color

/**
 * Raw design tokens for the NetLens "paper & ink" visual language — the single
 * source of truth for every hex value in the app and the Glance widgets.
 * Nothing outside core:ui should declare a Color(0x…) literal.
 *
 * Compose screens must not read these directly either: go through
 * MaterialTheme.colorScheme (mapped in app ui/theme) or [LocalStatusColors].
 * The widget module bridges these into day/night Glance ColorProviders,
 * since Glance cannot consume MaterialTheme.
 *
 * The *Bright variants exist because the solid accent/stamp hues don't hold
 * contrast as text or icons on dark surfaces; solid fills keep the base hue
 * in both modes, text/icon usage switches to the bright variant in dark.
 */
object NetLensPalette {
    // ── Light ────────────────────────────────────────────────────────────
    val paper = Color(0xFFF5F2EA)
    val ink = Color(0xFF22201C)
    val inkSoft = Color(0xFF6B665C)
    val accent = Color(0xFF2C6155)
    val accentSoft = Color(0xFFDCE7E2)
    val card = Color(0xFFFFFFFF)
    val line = Color(0xFFE4DFD2)
    val stamp = Color(0xFFB4472E)
    val stampSoft = Color(0xFFF2DCD3)
    val warn = Color(0xFFB07C22)
    val warnSoft = Color(0xFFEFE3C8)

    // ── Dark ─────────────────────────────────────────────────────────────
    val paperDark = Color(0xFF17181B)
    val inkOnDark = Color(0xFFE9E6DD)
    val inkSoftOnDark = Color(0xFF8B887F)
    val accentBright = Color(0xFF8FCABB)
    val accentSoftDark = Color(0xFF1E3B34)
    val cardDark = Color(0xFF212226)
    val lineDark = Color(0xFF2E2F33)
    val stampBright = Color(0xFFDD8E75)
    val stampSoftDark = Color(0xFF44231A)
    val warnBright = Color(0xFFD9A84E)
    val warnSoftDark = Color(0xFF3A3020)

    // ── Derived "on" shades (deep, in-family text on tinted containers) ──
    val accentInk = Color(0xFF16352E) // text on accentSoft / onPrimary in dark
    val warnInk = Color(0xFF3E2C08) // text on warnSoft / onTertiary in dark
    val stampInk = Color(0xFF44160A) // text on stampSoft / onError in dark
}
