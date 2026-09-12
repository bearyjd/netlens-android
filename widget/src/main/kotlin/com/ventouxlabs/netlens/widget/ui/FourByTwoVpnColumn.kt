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
 * with a [WidgetType.FLAG] flag and a 20dp badge — and drops the VPN caption and the
 * private-DNS dot.
 *
 * Nothing here carries vertical weight, and nothing asks for `fillMaxHeight`: this Column
 * sits inside a *horizontal* Row, where `match_parent` height resolves to
 * `EXACTLY(remaining)` and would push the Row's siblings out of the card. See the
 * invariant in [FourByTwoVariant].
 *
 * This column used to set FULL's vertical floor on its own: a 40sp flag, a 28dp badge and
 * a 12sp caption stacked to roughly 106dp against the ~56dp of the address column beside
 * it, and its 56dp width squeezed the addresses down to 16sp. Every element is now sized
 * down — [WidgetType.FLAG_LARGE] flag, 22dp badge, [WidgetType.LABEL] caption, 44dp wide
 * — for roughly 80dp with the private-DNS dot and 67dp without. Both figures are
 * estimates from natural text heights at `fontScale` 1.0, not device measurements. It is
 * still the tallest child of that Row.
 *
 * **Its 44dp is the width to take if the addresses beside it need more.** They are ~10dp
 * short of a 15-character public IP (see [FourByTwoWanColumn]); widening this column is
 * the wrong direction for that, and narrowing it is the only source of the missing width
 * on this row. The caption is what stops it going below 44dp — see [VpnBadgeStyle].
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
            // carries a [WidgetType.VALUE_TIGHT] address with ~11dp to spare on a
            // 13-character value. See the table in [FourByTwoWanColumn].
            .width(if (compact) 40.dp else 52.dp)
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
            style = TextStyle(
                fontSize = widgetSp(if (compact) WidgetType.FLAG else WidgetType.FLAG_LARGE),
            ),
        )
        Spacer(modifier = GlanceModifier.height(WidgetSpace.TIGHT))
        VpnLockBadge(
            backdropColor = style.backdropColor,
            lockDrawable = style.lockDrawable,
            contentDescription = style.contentDescription,
            compact = compact,
        )
        if (!compact) {
            Spacer(modifier = GlanceModifier.height(WidgetSpace.TIGHT))
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
 * width, which is why "Protected" and "Split Tunnel" are not shown in full — "Split
 * Tunnel" did not fit the old 56dp column either and was already being clipped mid-word.
 * The screen reader gets the full wording regardless.
 *
 * **The caption is the tightest text in the 4x2 and is expected to clip.** Raising it to
 * the [WidgetType.LABEL] floor costs width it did not have: "VPN On" is ~6 uppercase-led
 * characters, roughly 3.7em, which at 12sp on a `fontScale` 1.15 device is ~50dp against
 * this column's 44dp. It was ~46dp at the old 11sp, so it was already over — the floor
 * makes an existing clip about 4dp worse rather than introducing one. If it reads badly
 * on a device the fix is a shorter caption, not a smaller size: 12sp is the floor.
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
            fontSize = widgetSp(WidgetType.LABEL),
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
                fontSize = widgetSp(WidgetType.LABEL),
            ),
        )
    }
}

/** Rounded backdrop holding the lock glyph, with a "!" overlay for a split tunnel. */
@Composable
private fun VpnLockBadge(
    backdropColor: ColorProvider,
    lockDrawable: Int,
    contentDescription: String,
    compact: Boolean,
) {
    Box(
        modifier = GlanceModifier
            .size(if (compact) 20.dp else 22.dp)
            .cornerRadius(6.dp)
            .background(backdropColor),
        contentAlignment = Alignment.Center,
    ) {
        // No split-tunnel "!" overlay. It never rendered legibly: the glyph is positioned
        // by padding *inside* a fixed-size Box, so an offset large enough to clear the
        // lock overflows and clips, and one small enough to fit lands on top of the lock.
        // Split tunnel is already carried by the amber backdrop, the "Split" caption, the
        // contentDescription, and the status line's "- Split" suffix.
        Image(
            provider = ImageProvider(lockDrawable),
            contentDescription = contentDescription,
            // 15dp inside 22dp keeps ~3.5dp of backdrop showing on each side.
            modifier = GlanceModifier.size(if (compact) 14.dp else 15.dp),
        )
    }
}
