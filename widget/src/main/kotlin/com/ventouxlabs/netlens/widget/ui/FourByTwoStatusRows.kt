package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ventouxlabs.netlens.widget.WidgetState
import com.ventouxlabs.netlens.widget.action.DeeplinkUriKey
import com.ventouxlabs.netlens.widget.action.OpenDeeplinkAction
import com.ventouxlabs.netlens.widget.util.Deeplink
import com.ventouxlabs.netlens.widget.util.formatLinkSpeed

/**
 * How the network is behaving: the status row, and the device/encryption line under it.
 *
 * The two are one nested Column rather than two children of the 4x2's root, and that is a
 * budget decision, not a visual one — Glance drops a container's eleventh child silently,
 * the root had thirteen, and these two read as one block anyway. A nested container gets
 * its own budget of ten; this one uses three. See [FourByTwoWidgetContent] for how the
 * root's nine are spent and [FourByTwoVariant] for the failure.
 *
 * It carries no vertical weight, which for a *content* Column is the invariant rather
 * than a preference: a weighted one is deleted from the view tree under overrun.
 * Vertical padding comes from the caller.
 */
@Composable
internal fun FourByTwoStatusBlock(state: WidgetState, modifier: GlanceModifier) {
    Column(modifier = modifier) {
        FourByTwoStatusRow(state = state)
        Spacer(modifier = GlanceModifier.height(WidgetSpace.TIGHT))
        FourByTwoDetailRow(state = state)
    }
}

/**
 * Where traffic goes on the left, how good the radio is on the right.
 *
 * This is [DashboardFullContent]'s status row — flag, lock badge and DNS state inline,
 * signal on the trailing edge — rather than the old arrangement, where the flag and badge
 * stood in a column of their own beside the addresses and the radio figures sat in a
 * separate bottom row. Inline is what frees the 52dp that column was holding.
 *
 * Two clickables, deliberately not one spanning the Row: the flag and badge open VPN
 * Status and the status text opens DNS, so a single Row-wide clickable would send a tap
 * on "DNS → …" to the wrong screen. The same split is why [CompactFullContent] keeps its
 * marker and its section label separately clickable.
 *
 * The status text is the only weighted child, so it is the one that yields width when the
 * row is tight — an ellipsis there is recoverable, a dropped trailing child is not. Width
 * at a 427dp box: 411dp inside the padding, less ~40dp of flag and badge, ~8dp of gutter
 * and ~124dp of bars, speed and RSSI, leaves ~235dp. `"DNS → 192.168.1.1 · VPN routed"`
 * needs about 241dp at [WidgetType.BODY] on a `fontScale` 1.15 device, so the worst case
 * clips by a few dp; the common `"· Direct"` form needs ~209dp and is comfortable.
 *
 * Nothing here carries vertical weight, and nothing asks for `fillMaxHeight`. See the
 * invariant in [FourByTwoVariant].
 */
@Composable
private fun FourByTwoStatusRow(state: WidgetState) {
    val speedLabel = when {
        state.hasLinkSpeed -> formatLinkSpeed(state.linkSpeedMbps)
        state.cellGeneration.isNotEmpty() -> state.cellGeneration
        else -> ""
    }

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetFlagAndLock(state = state)
        Spacer(modifier = GlanceModifier.width(WidgetSpace.BASE))
        StatusLineContent(state = state, modifier = GlanceModifier.defaultWeight())
        if (state.hasRssi) {
            SignalBarsIcon(level = state.rssiLevel)
        }
        if (speedLabel.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.width(WidgetSpace.TIGHT))
            StatusRowText(text = speedLabel)
        }
        if (state.hasRssi) {
            Spacer(modifier = GlanceModifier.width(WidgetSpace.TIGHT))
            StatusRowText(
                text = "${state.rssi}",
                color = rssiTextColor(state.rssi),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Device count and encryption, on their own full-width row.
 *
 * These two used to sit under the status line inside a shared column, which is why a tap
 * on them opened DNS. On a row of their own they route where they read: the count is the
 * subject, so the row opens Devices.
 */
@Composable
private fun FourByTwoDetailRow(state: WidgetState) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.DEVICES),
                ),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val deviceText = "${state.deviceCount} device${if (state.deviceCount != 1) "s" else ""}"
        Text(
            text = deviceText,
            style = TextStyle(
                color = NetLensWidgetColors.inkSoft,
                fontSize = widgetSp(WidgetType.LABEL),
            ),
            maxLines = 1,
        )
        if (state.encryptionType.isNotEmpty()) {
            val secure = state.isEncryptionSecure
            Text(
                text = " · ${state.encryptionType}",
                style = TextStyle(
                    color = if (secure) NetLensWidgetColors.accent else NetLensWidgetColors.stamp,
                    fontSize = widgetSp(WidgetType.LABEL),
                ),
                maxLines = 1,
            )
        }
    }
}

private fun rssiTextColor(rssi: Int): ColorProvider = when {
    rssi >= -60 -> NetLensWidgetColors.accent
    rssi >= -70 -> NetLensWidgetColors.warn
    else -> NetLensWidgetColors.stamp
}

/** The trailing figures' shared style; soft ink and normal weight unless overridden. */
@Composable
private fun StatusRowText(
    text: String,
    color: ColorProvider = NetLensWidgetColors.inkSoft,
    fontWeight: FontWeight? = null,
) {
    Text(
        text = text,
        style = TextStyle(
            color = color,
            fontSize = widgetSp(WidgetType.BODY),
            fontWeight = fontWeight,
        ),
        maxLines = 1,
    )
}
