package com.ventoux.netlens.core.ui

import androidx.compose.ui.unit.dp

// Compact scale derived from observed usage (4/8/12/16/24/32 dp dominate ~290 of
// ~300 inline call sites). Resist adding more steps — outliers like 6, 18, 100
// should stay inline with a comment, not bloat the scale.
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}
