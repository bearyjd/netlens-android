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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ventouxlabs.netlens.core.network.VpnState
import com.ventouxlabs.netlens.widget.R
import com.ventouxlabs.netlens.widget.WidgetState
import com.ventouxlabs.netlens.widget.action.DeeplinkUriKey
import com.ventouxlabs.netlens.widget.action.OpenDeeplinkAction
import com.ventouxlabs.netlens.widget.util.Deeplink

/**
 * Country flag over the VPN lock badge, opening VPN Status — the COMPACT 4x2 only.
 *
 * ## What went, and why the caption is not coming back
 *
 * This used to have a FULL form as well: 52dp wide, a [WidgetType.FLAG_LARGE] flag, a
 * 22dp badge, a bold caption ("VPN On" / "Split" / "No VPN") and a private-DNS dot. The
 * caption never fit — ~6 uppercase-led characters is roughly 3.7em, which at the 12sp
 * floor on a `fontScale` 1.15 device is ~50dp against a 44dp column, and it was already
 * clipping at 11sp before the type scale raised it. Shortening the wording and widening
 * the column were both tried against the same 52dp the addresses beside it needed.
 *
 * FULL now puts the flag and lock inline in its status row ([WidgetFlagAndLock]) and has
 * no column here at all, so the caption has no site left to clip in: it is gone with the
 * branch that drew it, not merely hidden. That is the whole of the fix — there is no
 * width arithmetic left to get wrong.
 *
 * Nothing here carries vertical weight, and nothing asks for `fillMaxHeight`: this Column
 * sits inside a *horizontal* Row, where `match_parent` height resolves to
 * `EXACTLY(remaining)` and would push the Row's siblings out of the card. See the
 * invariant in [FourByTwoVariant].
 */
@Composable
internal fun FourByTwoVpnColumn(state: WidgetState) {
    val style = vpnBadgeStyle(state.vpnState)

    Column(
        modifier = GlanceModifier
            // 40dp leaves the two address columns ~134dp each inside a 341dp box, which
            // is what holds them at [WidgetType.VALUE_TIGHT]. See [FourByTwoWanColumn].
            .width(40.dp)
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
            style = TextStyle(fontSize = widgetSp(WidgetType.FLAG)),
        )
        Spacer(modifier = GlanceModifier.height(WidgetSpace.TIGHT))
        VpnLockBadge(
            backdropColor = style.backdropColor,
            lockDrawable = style.lockDrawable,
            contentDescription = style.contentDescription,
        )
    }
}

/**
 * How one [VpnState] presents in the badge.
 *
 * There is no caption field any more — see above — so the wording survives only as
 * [contentDescription], where it costs no width and the screen reader gets it in full.
 */
private data class VpnBadgeStyle(
    val backdropColor: ColorProvider,
    val lockDrawable: Int,
    val contentDescription: String,
)

private fun vpnBadgeStyle(vpnState: VpnState): VpnBadgeStyle = when (vpnState) {
    VpnState.FullTunnel -> VpnBadgeStyle(
        backdropColor = NetLensWidgetColors.accent,
        lockDrawable = R.drawable.ic_lock_closed_white,
        contentDescription = "Protected",
    )
    VpnState.SplitTunnel -> VpnBadgeStyle(
        backdropColor = NetLensWidgetColors.warn,
        lockDrawable = R.drawable.ic_lock_closed_white,
        contentDescription = "Split Tunnel",
    )
    VpnState.None -> VpnBadgeStyle(
        backdropColor = NetLensWidgetColors.stamp,
        lockDrawable = R.drawable.ic_lock_open_white,
        contentDescription = "No VPN",
    )
}

/** Rounded backdrop holding the lock glyph. */
@Composable
private fun VpnLockBadge(
    backdropColor: ColorProvider,
    lockDrawable: Int,
    contentDescription: String,
) {
    Box(
        modifier = GlanceModifier
            .size(20.dp)
            .cornerRadius(6.dp)
            .background(backdropColor),
        contentAlignment = Alignment.Center,
    ) {
        // No split-tunnel "!" overlay. It never rendered legibly: the glyph is positioned
        // by padding *inside* a fixed-size Box, so an offset large enough to clear the
        // lock overflows and clips, and one small enough to fit lands on top of the lock.
        // Split tunnel is already carried by the amber backdrop, the contentDescription,
        // and the status line's "· Split" suffix.
        Image(
            provider = ImageProvider(lockDrawable),
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(14.dp),
        )
    }
}
