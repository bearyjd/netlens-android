package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ventouxlabs.netlens.widget.WidgetState
import com.ventouxlabs.netlens.widget.util.formatLinkSpeed

/**
 * Signal strength + link speed | SSID/cell/status | RSSI dBm | captive-portal flag.
 *
 * Secondary to the addresses above it, so [FourByTwoVariant.COMPACT] drops this row
 * whole rather than shrinking it.
 */
@Composable
internal fun FourByTwoBottomRow(state: WidgetState) {
    val bottomLabel = when {
        state.ssid != null -> "SSID: ${state.ssid}"
        state.cellGeneration.isNotEmpty() -> state.cellGeneration
        !state.isConnected -> "Offline"
        else -> "Connected"
    }
    val speedLabel = when {
        state.hasLinkSpeed -> formatLinkSpeed(state.linkSpeedMbps)
        state.cellGeneration.isNotEmpty() -> state.cellGeneration
        else -> ""
    }

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.hasRssi) {
            SignalBarsIcon(level = state.rssiLevel)
            Spacer(modifier = GlanceModifier.width(3.dp))
        }
        if (speedLabel.isNotEmpty()) {
            BottomRowText(text = speedLabel)
            Spacer(modifier = GlanceModifier.width(4.dp))
        }
        BottomRowText(text = bottomLabel, modifier = GlanceModifier.defaultWeight())
        if (state.hasRssi) {
            BottomRowText(
                text = "${state.rssi}",
                color = rssiTextColor(state.rssi),
                fontWeight = FontWeight.Bold,
            )
        }
        if (state.isCaptivePortal) {
            Spacer(modifier = GlanceModifier.width(4.dp))
            BottomRowText(text = "Portal", color = NetLensWidgetColors.warn)
        }
    }
}

private fun rssiTextColor(rssi: Int): ColorProvider = when {
    rssi >= -60 -> NetLensWidgetColors.accent
    rssi >= -70 -> NetLensWidgetColors.warn
    else -> NetLensWidgetColors.stamp
}

/** The row's shared 14sp label style; soft ink and normal weight unless overridden. */
@Composable
private fun BottomRowText(
    text: String,
    modifier: GlanceModifier = GlanceModifier,
    color: ColorProvider = NetLensWidgetColors.inkSoft,
    fontWeight: FontWeight? = null,
) {
    Text(
        text = text,
        style = TextStyle(
            color = color,
            fontSize = widgetSp(14f),
            fontWeight = fontWeight,
        ),
        modifier = modifier,
    )
}
