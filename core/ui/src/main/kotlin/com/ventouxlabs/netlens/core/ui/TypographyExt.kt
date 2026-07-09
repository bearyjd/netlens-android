package com.ventouxlabs.netlens.core.ui

import androidx.compose.ui.text.TextStyle

/**
 * Tabular (fixed-width) figures for live or column-aligned numerics — ping
 * times, speeds, signal levels — so updating digits don't reflow the layout.
 * Usage: `MaterialTheme.typography.titleMedium.withTabularFigures()`.
 * JetBrains Mono (the `labelSmall` role) stays the convention for machine
 * strings (IPs, MACs, hex); this is for human-readable metrics.
 */
fun TextStyle.withTabularFigures(): TextStyle = copy(fontFeatureSettings = "tnum")
