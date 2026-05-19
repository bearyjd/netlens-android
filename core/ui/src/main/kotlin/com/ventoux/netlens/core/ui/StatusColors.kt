package com.ventoux.netlens.core.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Diagnostic status palette. Covers the five recurring inline-hex values found
// across portscan / ipinfo / posture / vpnstatus / lanscan host detail screens
// (50 sites total before consolidation). VPN/posture screens that need a deeper,
// more "serious" variant of these colors keep their own inline values with a
// comment explaining why.
@Immutable
data class StatusColors(
    val pass: Color,
    val warn: Color,
    val fail: Color,
    val info: Color,
    val muted: Color,
)

val NetLensStatusColors = StatusColors(
    pass = Color(0xFF4CAF50),
    warn = Color(0xFFF59E0B),
    fail = Color(0xFFEF4444),
    info = Color(0xFF3B82F6),
    muted = Color(0xFF9E9E9E),
)

val LocalStatusColors = staticCompositionLocalOf { NetLensStatusColors }
