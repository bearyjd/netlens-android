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
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ventouxlabs.netlens.core.network.VpnState
import com.ventouxlabs.netlens.widget.R
import com.ventouxlabs.netlens.widget.WidgetState
import com.ventouxlabs.netlens.widget.action.DeeplinkUriKey
import com.ventouxlabs.netlens.widget.action.OpenDeeplinkAction
import com.ventouxlabs.netlens.widget.util.Deeplink

/**
 * Country flag beside the VPN lock badge, opening VPN Status.
 *
 * The inline marker — a [WidgetType.FLAG] glyph and a 16dp badge, about 40dp wide — as
 * against the standalone column the 4x2 used to give it ([FourByTwoVpnColumn], now only
 * on the COMPACT path). Inline is what lets a row spend its width on an address or a
 * status line instead of on a caption.
 *
 * It keeps its own `clickable` rather than letting a caller wrap the whole row in one:
 * every row this appears on carries something that routes elsewhere — "WAN" to IP Info,
 * "DNS → …" to DNS — and a Row-wide clickable would send those taps to VPN Status.
 *
 * [DashboardFullContent] and [CompactFullContent] hold their own copies of this markup
 * and deliberately keep them. Both are reference layouts the user has signed off on
 * against a device, and a Glance composable moving between scopes is not a change this
 * module can verify on the JVM. New call sites use this one.
 */
@Composable
internal fun WidgetFlagAndLock(state: WidgetState) {
    val (backdrop, lockDrawable, vpnLabel) = when (state.vpnState) {
        VpnState.FullTunnel ->
            Triple(NetLensWidgetColors.accent, R.drawable.ic_lock_closed_white, "Protected")
        VpnState.SplitTunnel ->
            Triple(NetLensWidgetColors.warn, R.drawable.ic_lock_closed_white, "Split Tunnel")
        VpnState.None ->
            Triple(NetLensWidgetColors.stamp, R.drawable.ic_lock_open_white, "No VPN")
    }
    Row(
        modifier = GlanceModifier.clickable(
            actionRunCallback<OpenDeeplinkAction>(
                actionParametersOf(DeeplinkUriKey to Deeplink.VPNSTATUS),
            ),
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.countryFlag.ifEmpty { "—" },
            style = TextStyle(fontSize = widgetSp(WidgetType.FLAG)),
        )
        Spacer(modifier = GlanceModifier.width(WidgetSpace.TIGHT))
        Box(
            modifier = GlanceModifier
                .size(16.dp)
                .cornerRadius(4.dp)
                .background(backdrop),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(lockDrawable),
                contentDescription = vpnLabel,
                modifier = GlanceModifier.size(11.dp),
            )
        }
    }
}

/**
 * Absorbs surplus height so it is shared between a Column's sections instead of pooling
 * in a dead band at the bottom edge. Interleave one between and around the sections — N
 * sections take N+1 gaps — to get the even distribution that weighting the sections
 * themselves would give.
 *
 * This is the one sanctioned vertical weight: a Spacer has no children, so when an
 * overrun drives it to zero it takes nothing with it — unlike a weighted content Column,
 * which is deleted from the view tree along with its payload. See the invariant in
 * [FourByTwoVariant].
 */
@Composable
internal fun ColumnScope.SectionGap() {
    Spacer(modifier = GlanceModifier.defaultWeight())
}

/**
 * Full-width hairline rule between widget sections.
 *
 * [WidgetSpace.HAIRLINE], not a spacing step: a rule is a mark on the card rather than a
 * gap between things, so it does not round up to the rhythm's visible 4dp.
 */
@Composable
internal fun WidgetSectionDivider() {
    Spacer(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(WidgetSpace.HAIRLINE)
            .background(NetLensWidgetColors.line),
    )
}
