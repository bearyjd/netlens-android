package com.ventouxlabs.netlens.widget.ui

import android.os.Build
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import com.ventouxlabs.netlens.widget.R

/**
 * Rounded widget background that works on API 29/30, where
 * [androidx.glance.appwidget.cornerRadius] is a no-op (it requires API 31+).
 * On API 31+ this uses the day/night [NetLensWidgetColors.background] token
 * with a real corner radius; below that it falls back to a pre-rounded
 * drawable, since Glance can't apply a corner radius to an arbitrary
 * background color pre-31.
 */
fun GlanceModifier.widgetBackground(): GlanceModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    this.background(NetLensWidgetColors.background).cornerRadius(16.dp)
} else {
    this.background(ImageProvider(R.drawable.widget_background))
}
