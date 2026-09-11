package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ventouxlabs.netlens.core.network.VpnState
import com.ventouxlabs.netlens.widget.R
import com.ventouxlabs.netlens.widget.WidgetState
import com.ventouxlabs.netlens.widget.action.DeeplinkUriKey
import com.ventouxlabs.netlens.widget.action.OpenDeeplinkAction
import com.ventouxlabs.netlens.widget.util.Deeplink

/**
 * Country flag over the VPN lock badge; the whole column opens the VPN Status screen.
 *
 * [compact] halves the column's footprint for [FourByTwoVariant.COMPACT] — a 40dp column
 * with a 20sp flag and a 20dp badge — and drops the VPN text label and the private-DNS
 * dot.
 *
 * The `fillMaxHeight` on the FULL path is safe and stays: this Column is a child of a
 * *horizontal* Row, so it lowers to `match_parent` where nothing competes for height.
 * See the invariant in [FourByTwoVariant].
 *
 * This column is also why FULL is tall. Six stacked children — 40sp flag, spacer, 28dp
 * badge, spacer, 12sp caption, private-DNS dot — come to roughly 106dp against the ~56dp
 * of the three-Text address column beside it. A flag glyph and a three-valued enum label
 * set the widget's vertical floor; the addresses do not.
 */
@Composable
internal fun FourByTwoVpnColumn(state: WidgetState, compact: Boolean = false) {
    val (backdropColor, lockDrawable, vpnLabel) = when (state.vpnState) {
        VpnState.FullTunnel -> Triple(NetLensWidgetColors.accent, R.drawable.ic_lock_closed_white, "Protected")
        VpnState.SplitTunnel -> Triple(NetLensWidgetColors.warn, R.drawable.ic_lock_closed_white, "Split Tunnel")
        VpnState.None -> Triple(NetLensWidgetColors.stamp, R.drawable.ic_lock_open_white, "No VPN")
    }

    Column(
        modifier = GlanceModifier
            .width(if (compact) 40.dp else 64.dp)
            .then(if (compact) GlanceModifier else GlanceModifier.fillMaxHeight())
            .clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.VPNSTATUS),
                ),
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.countryFlag.ifEmpty { "—" },
            style = TextStyle(fontSize = widgetSp(if (compact) 20f else 40f)),
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        VpnLockBadge(
            backdropColor = backdropColor,
            lockDrawable = lockDrawable,
            vpnLabel = vpnLabel,
            isSplitTunnel = state.vpnState is VpnState.SplitTunnel,
            compact = compact,
        )
        if (!compact) {
            Spacer(modifier = GlanceModifier.height(2.dp))
            VpnLabelAndDnsDot(
                vpnLabel = vpnLabel,
                labelColor = backdropColor,
                hasPrivateDns = state.hasPrivateDns,
            )
        }
    }
}

/** The badge's caption, plus a dot marking a private-DNS resolver. */
@Composable
private fun VpnLabelAndDnsDot(
    vpnLabel: String,
    labelColor: ColorProvider,
    hasPrivateDns: Boolean,
) {
    Text(
        text = vpnLabel,
        style = TextStyle(
            color = labelColor,
            fontSize = widgetSp(12f),
            fontWeight = FontWeight.Bold,
        ),
        maxLines = 1,
    )
    if (hasPrivateDns) {
        Text(
            text = "●",
            style = TextStyle(
                color = NetLensWidgetColors.accent,
                fontSize = widgetSp(12f),
            ),
        )
    }
}

/** Rounded backdrop holding the lock glyph, with a "!" overlay for a split tunnel. */
@Composable
private fun VpnLockBadge(
    backdropColor: ColorProvider,
    lockDrawable: Int,
    vpnLabel: String,
    isSplitTunnel: Boolean,
    compact: Boolean,
) {
    Box(
        modifier = GlanceModifier
            .size(if (compact) 20.dp else 28.dp)
            .cornerRadius(6.dp)
            .background(backdropColor),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(lockDrawable),
            contentDescription = vpnLabel,
            modifier = GlanceModifier.size(if (compact) 14.dp else 18.dp),
        )
        if (isSplitTunnel) {
            // Offsets are the badge size less the glyph's own box, so they track it down.
            val overlayOffset = if (compact) 10.dp else 14.dp
            Text(
                text = "!",
                style = TextStyle(
                    color = NetLensWidgetColors.onAccentFill,
                    fontSize = widgetSp(12f),
                    fontWeight = FontWeight.Bold,
                ),
                modifier = GlanceModifier.padding(start = overlayOffset, bottom = overlayOffset),
            )
        }
    }
}
