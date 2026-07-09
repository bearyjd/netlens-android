package com.ventouxlabs.netlens.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.ventouxlabs.netlens.core.ui.NetLensPalette

/**
 * Full Material 3 color schemes for the "paper & ink" language, built
 * exclusively from [NetLensPalette] — no hex literals here or anywhere
 * outside core:ui.
 *
 * Role mapping cheatsheet:
 *  - background/surface = paper (screens sit on paper)
 *  - surfaceVariant + surfaceContainer* = card (raised white/graphite cards)
 *  - primary = accent teal (also "normal/secure" status)
 *  - tertiary = warning amber
 *  - error = stamp red (also "active risk" status)
 *  - outline = hairline borders (dark mode elevation = cardDark + border,
 *    never shadows)
 *
 * Dark mode: primary/tertiary/error switch to the *Bright variants so text
 * and icons hold contrast. Components needing a SOLID accent fill in dark
 * (hero CTAs) keep NetLensPalette.accent via explicit component colors —
 * that mapping happens at the component level, not here.
 */
internal val LightNetLensColorScheme = lightColorScheme(
    primary = NetLensPalette.accent,
    onPrimary = NetLensPalette.card,
    primaryContainer = NetLensPalette.accentSoft,
    onPrimaryContainer = NetLensPalette.accentInk,
    inversePrimary = NetLensPalette.accentBright,
    secondary = NetLensPalette.inkSoft,
    onSecondary = NetLensPalette.card,
    secondaryContainer = NetLensPalette.accentSoft,
    onSecondaryContainer = NetLensPalette.accentInk,
    tertiary = NetLensPalette.warn,
    onTertiary = NetLensPalette.card,
    tertiaryContainer = NetLensPalette.warnSoft,
    onTertiaryContainer = NetLensPalette.warnInk,
    error = NetLensPalette.stamp,
    onError = NetLensPalette.card,
    errorContainer = NetLensPalette.stampSoft,
    onErrorContainer = NetLensPalette.stampInk,
    background = NetLensPalette.paper,
    onBackground = NetLensPalette.ink,
    surface = NetLensPalette.paper,
    onSurface = NetLensPalette.ink,
    surfaceVariant = NetLensPalette.card,
    onSurfaceVariant = NetLensPalette.inkSoft,
    surfaceTint = NetLensPalette.accent,
    surfaceBright = NetLensPalette.card,
    surfaceDim = NetLensPalette.line,
    surfaceContainerLowest = NetLensPalette.card,
    surfaceContainerLow = NetLensPalette.card,
    surfaceContainer = NetLensPalette.card,
    surfaceContainerHigh = NetLensPalette.card,
    surfaceContainerHighest = NetLensPalette.card,
    inverseSurface = NetLensPalette.ink,
    inverseOnSurface = NetLensPalette.paper,
    outline = NetLensPalette.line,
    outlineVariant = NetLensPalette.line,
)

internal val DarkNetLensColorScheme = darkColorScheme(
    primary = NetLensPalette.accentBright,
    onPrimary = NetLensPalette.accentInk,
    primaryContainer = NetLensPalette.accentSoftDark,
    onPrimaryContainer = NetLensPalette.accentBright,
    inversePrimary = NetLensPalette.accent,
    secondary = NetLensPalette.inkSoftOnDark,
    onSecondary = NetLensPalette.paperDark,
    secondaryContainer = NetLensPalette.accentSoftDark,
    onSecondaryContainer = NetLensPalette.accentBright,
    tertiary = NetLensPalette.warnBright,
    onTertiary = NetLensPalette.warnInk,
    tertiaryContainer = NetLensPalette.warnSoftDark,
    onTertiaryContainer = NetLensPalette.warnBright,
    error = NetLensPalette.stampBright,
    onError = NetLensPalette.stampInk,
    errorContainer = NetLensPalette.stampSoftDark,
    onErrorContainer = NetLensPalette.stampBright,
    background = NetLensPalette.paperDark,
    onBackground = NetLensPalette.inkOnDark,
    surface = NetLensPalette.paperDark,
    onSurface = NetLensPalette.inkOnDark,
    surfaceVariant = NetLensPalette.cardDark,
    onSurfaceVariant = NetLensPalette.inkSoftOnDark,
    surfaceTint = NetLensPalette.accentBright,
    surfaceBright = NetLensPalette.cardDark,
    surfaceDim = NetLensPalette.paperDark,
    surfaceContainerLowest = NetLensPalette.paperDark,
    surfaceContainerLow = NetLensPalette.cardDark,
    surfaceContainer = NetLensPalette.cardDark,
    surfaceContainerHigh = NetLensPalette.cardDark,
    surfaceContainerHighest = NetLensPalette.cardDark,
    inverseSurface = NetLensPalette.inkOnDark,
    inverseOnSurface = NetLensPalette.paperDark,
    outline = NetLensPalette.lineDark,
    outlineVariant = NetLensPalette.lineDark,
)
