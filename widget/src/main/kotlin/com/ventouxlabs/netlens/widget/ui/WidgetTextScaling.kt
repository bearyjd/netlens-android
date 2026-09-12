package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.glance.LocalContext

/**
 * Resolves a widget text size that resists the system font scale.
 *
 * Home-screen widgets are laid out for a fixed cell and cannot scroll, so a large
 * accessibility font scale (these test phones run 1.3x) overflows the 4x2's fixed
 * 250x110dp card — text clips and whole sections fall off the bottom. Glance renders
 * an `sp` size by multiplying it by the user's `fontScale`, so dividing the design
 * size by `fontScale` pins the rendered pixel size back to the design, matching how
 * the platform clock/weather widgets behave.
 *
 * [maxScale] caps how much the widget honors the user's preference before clamping:
 * `1f` pins fully (never grows), `1.15f` allows up to 15% growth. The default allows
 * 15%: a full pin rendered every label ~13% smaller than the launcher text around it on
 * a 1.15x device, which read as "can't see the fonts" rather than as tidy density. The
 * cap still holds the line against the 1.3x+ scales that actually overflow the card.
 */
@Composable
fun widgetSp(designSp: Float, maxScale: Float = 1.15f): TextUnit {
    val fontScale = LocalContext.current.resources.configuration.fontScale
    if (fontScale <= 1f) return designSp.sp
    val effectiveScale = minOf(fontScale, maxScale)
    return (designSp * effectiveScale / fontScale).sp
}
