package com.ventouxlabs.netlens.core.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Three-state diagnostic status palette — the ONLY way to color status in
 * NetLens. teal = normal/secure, amber = warning/attention, stamp red =
 * active risk/alert. Provided theme-aware via [LocalStatusColors] (the app
 * theme swaps [LightStatusColors]/[DarkStatusColors]); never hardcode status
 * hex in a screen.
 *
 * [info] is retained for source compatibility with existing screens and maps
 * to the teal family (informational == nothing is wrong). It will be folded
 * into [pass] during the per-screen redesign passes.
 */
@Immutable
data class StatusColors(
    val pass: Color,
    val warn: Color,
    val fail: Color,
    val info: Color,
    val muted: Color,
    val passContainer: Color,
    val warnContainer: Color,
    val failContainer: Color,
    val onPassContainer: Color,
    val onWarnContainer: Color,
    val onFailContainer: Color,
)

val LightStatusColors = StatusColors(
    pass = NetLensPalette.accent,
    warn = NetLensPalette.warn,
    fail = NetLensPalette.stamp,
    info = NetLensPalette.accent,
    muted = NetLensPalette.inkSoft,
    passContainer = NetLensPalette.accentSoft,
    warnContainer = NetLensPalette.warnSoft,
    failContainer = NetLensPalette.stampSoft,
    onPassContainer = NetLensPalette.accentInk,
    onWarnContainer = NetLensPalette.warnInk,
    onFailContainer = NetLensPalette.stampInk,
)

val DarkStatusColors = StatusColors(
    pass = NetLensPalette.accentBright,
    warn = NetLensPalette.warnBright,
    fail = NetLensPalette.stampBright,
    info = NetLensPalette.accentBright,
    muted = NetLensPalette.inkSoftOnDark,
    passContainer = NetLensPalette.accentSoftDark,
    warnContainer = NetLensPalette.warnSoftDark,
    failContainer = NetLensPalette.stampSoftDark,
    onPassContainer = NetLensPalette.accentBright,
    onWarnContainer = NetLensPalette.warnBright,
    onFailContainer = NetLensPalette.stampBright,
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }
