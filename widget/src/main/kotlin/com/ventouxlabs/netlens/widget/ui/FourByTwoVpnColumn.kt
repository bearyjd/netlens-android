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
 * [compact] shrinks the column further for [FourByTwoVariant.COMPACT] — a 40dp column
 * with a 20sp flag and a 20dp badge — and drops the VPN caption and the private-DNS dot.
 *
 * Nothing here carries vertical weight, and nothing asks for `fillMaxHeight`: this Column
 * sits inside a *horizontal* Row, where `match_parent` height resolves to
 * `EXACTLY(remaining)` and would push the Row's siblings out of the card. See the
 * invariant in [FourByTwoVariant].
 *
 * This column used to set FULL's vertical floor on its own: a 40sp flag, a 28dp badge and
 * a 12sp caption stacked to roughly 106dp against the ~56dp of the address column beside
 * it, and its 56dp width squeezed the addresses down to 16sp. Every element is now sized
 * down — 24sp flag, 22dp badge, 11sp caption, 44dp wide — for roughly 80dp with the
 * private-DNS dot and 67dp without. Both figures are estimates from natural text heights
 * at `fontScale` 1.0, not device measurements. It is still the tallest child of that Row.
 *
 * No state is dropped in the process: all three VPN states keep a visible caption, and
 * the screen reader keeps the unabbreviated wording (see [VpnBadgeStyle]).
 */
@Composable
internal fun FourByTwoVpnColumn(state: WidgetState, compact: Boolean = false) {
    val style = vpnBadgeStyle(state.vpnState)

    Column(
        modifier = GlanceModifier
            // 44dp leaves the two address columns ~132dp each inside a 341dp box, which
            // is what lets them carry a 17sp address instead of 16sp.
            .width(if (compact) 40.dp else 44.dp)
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
            style = TextStyle(fontSize = widgetSp(if (compact) 20f else 24f)),
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        VpnLockBadge(
            backdropColor = style.backdropColor,
            lockDrawable = style.lockDrawable,
            contentDescription = style.contentDescription,
            isSplitTunnel = state.vpnState is VpnState.SplitTunnel,
            compact = compact,
        )
        if (!compact) {
            Spacer(modifier = GlanceModifier.height(2.dp))
            VpnCaptionAndDnsDot(
                caption = style.caption,
                captionColor = style.backdropColor,
                hasPrivateDns = state.hasPrivateDns,
            )
        }
    }
}

/**
 * How one [VpnState] presents in the badge.
 *
 * [caption] and [contentDescription] are separate because only the caption pays for
 * width. At 11sp inside a 44dp column roughly six characters fit, so "Protected" and
 * "Split Tunnel" cannot be shown in full — "Split Tunnel" in fact did not fit the old
 * 56dp column either and was already being clipped mid-word. The screen reader gets the
 * full wording regardless.
 */
private data class VpnBadgeStyle(
    val backdropColor: ColorProvider,
    val lockDrawable: Int,
    val caption: String,
    val contentDescription: String,
)

private fun vpnBadgeStyle(vpnState: VpnState): VpnBadgeStyle = when (vpnState) {
    VpnState.FullTunnel -> VpnBadgeStyle(
        backdropColor = NetLensWidgetColors.accent,
        lockDrawable = R.drawable.ic_lock_closed_white,
        caption = "VPN On",
        contentDescription = "Protected",
    )
    VpnState.SplitTunnel -> VpnBadgeStyle(
        backdropColor = NetLensWidgetColors.warn,
        lockDrawable = R.drawable.ic_lock_closed_white,
        caption = "Split",
        contentDescription = "Split Tunnel",
    )
    VpnState.None -> VpnBadgeStyle(
        backdropColor = NetLensWidgetColors.stamp,
        lockDrawable = R.drawable.ic_lock_open_white,
        caption = "No VPN",
        contentDescription = "No VPN",
    )
}

/** The badge's caption, plus a dot marking a private-DNS resolver. */
@Composable
private fun VpnCaptionAndDnsDot(
    caption: String,
    captionColor: ColorProvider,
    hasPrivateDns: Boolean,
) {
    Text(
        text = caption,
        style = TextStyle(
            color = captionColor,
            fontSize = widgetSp(CAPTION_SIZE_SP),
            fontWeight = FontWeight.Bold,
        ),
        maxLines = 1,
    )
    if (hasPrivateDns) {
        Text(
            text = "●",
            style = TextStyle(
                color = NetLensWidgetColors.accent,
                // Matches the caption so the two read as one block, and so the dot costs
                // ~13dp of the column's height rather than ~14dp.
                fontSize = widgetSp(CAPTION_SIZE_SP),
            ),
        )
    }
}

/** Caption and private-DNS dot size. See [VpnBadgeStyle] for what it has to fit. */
private const val CAPTION_SIZE_SP = 11f

/** Rounded backdrop holding the lock glyph, with a "!" overlay for a split tunnel. */
@Composable
private fun VpnLockBadge(
    backdropColor: ColorProvider,
    lockDrawable: Int,
    contentDescription: String,
    isSplitTunnel: Boolean,
    compact: Boolean,
) {
    Box(
        modifier = GlanceModifier
            .size(if (compact) 20.dp else 22.dp)
            .cornerRadius(6.dp)
            .background(backdropColor),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(lockDrawable),
            contentDescription = contentDescription,
            // 15dp inside 22dp keeps ~3.5dp of backdrop showing on each side.
            modifier = GlanceModifier.size(if (compact) 14.dp else 15.dp),
        )
        if (isSplitTunnel) {
            // Padding on the opposite two sides is what pushes the glyph into the
            // top-right corner, so the offset is the badge size less the glyph's own box:
            // a 12sp "!" occupies ~14dp, which at a 22dp badge leaves 8dp, not the 14dp
            // a 28dp badge could afford. Overshoot here does not reposition the glyph, it
            // overflows the fixed-size Box and clips it.
            val overlayOffset = if (compact) 10.dp else 8.dp
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
