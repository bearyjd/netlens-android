package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ventouxlabs.netlens.widget.WidgetState
import com.ventouxlabs.netlens.widget.action.DeeplinkUriKey
import com.ventouxlabs.netlens.widget.action.OpenDeeplinkAction
import com.ventouxlabs.netlens.widget.util.Deeplink

/**
 * The 4x2's two address columns — the widget's whole payload, and the pair that vanished
 * from the view tree on a short box.
 *
 * Every modifier here is a Row-child modifier and therefore safe: the horizontal
 * `defaultWeight` in the root [modifier] divides *width*, never the scarce axis. What was
 * unsafe was the vertical weight on the Row *above* these columns; that is gone. See the
 * invariant in [FourByTwoVariant].
 *
 * The weight comes from the caller because `defaultWeight` is `RowScope`-scoped and only
 * resolves on a direct child of the parent Row.
 *
 * ## Why the address is [WidgetType.VALUE] on FULL and [WidgetType.VALUE_TIGHT] on COMPACT
 *
 * The columns split what the padding, the gutters and — on COMPACT only — the 40dp VPN
 * column leave. FULL dropped that column when the flag and lock moved inline into its
 * status row, and the ~52dp it gave back is what pays for the larger size:
 *
 * ```
 *                     427dp box              341dp box
 *   FULL              201.5dp each           158.5dp each
 *   COMPACT           177.5dp each           134.5dp each
 * ```
 *
 * Against that, on a `fontScale` 1.15 device where [widgetSp] renders a design size at
 * 1.15x and a bold IPv4 runs ~0.51dp per character per sp:
 *
 * ```
 *   "192.168.1.129"   13 chars   ~122dp at 16sp   ~137dp at 18sp
 *   "185.199.108.153" 15 chars   ~141dp at 16sp   ~158dp at 18sp
 * ```
 *
 * So FULL carries [WidgetType.VALUE] with ~43dp to spare on the 15-character worst case
 * at a 427dp box — and with **none** at 341dp, where 158.5dp of column meets a 158.4dp
 * address. That is the first site to check if a public IP ellipsizes: the model is
 * calibrated slightly pessimistic (a 13-character address at 17sp measured ~132dp where
 * this puts it at 129dp), but a break-even is a break-even. Stepping back down is a
 * one-word change here.
 *
 * COMPACT stays at [WidgetType.VALUE_TIGHT] because it keeps the VPN column: 18sp needs
 * 137dp for even the common 13-character address, against 134.5dp at a 341dp box.
 *
 * [AddressValue] is `maxLines = 1` throughout, because an ellipsized IP reads as a
 * different address and a wrapped one ("192.168.1.12" / "9") reads as a wrong one.
 */
@Composable
internal fun FourByTwoWanColumn(
    state: WidgetState,
    modifier: GlanceModifier,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier
            .clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.IPINFO),
                ),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AddressLabel(text = "WAN")
        AddressValue(text = state.publicIp.ifEmpty { "—.—.—.—" }, compact = compact)
        if (!compact && state.ispName.isNotEmpty()) {
            Text(
                text = state.ispName,
                style = TextStyle(
                    color = NetLensWidgetColors.inkSoft,
                    fontSize = widgetSp(WidgetType.LABEL),
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun FourByTwoLanColumn(
    state: WidgetState,
    modifier: GlanceModifier,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier
            .clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.DEVICES),
                ),
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.End,
    ) {
        AddressLabel(text = "LAN")
        AddressValue(text = state.localIp.ifEmpty { "—" }, compact = compact)
    }
}

@Composable
private fun AddressLabel(text: String) {
    Text(
        text = text,
        style = TextStyle(
            color = NetLensWidgetColors.inkSoft,
            fontSize = widgetSp(WidgetType.LABEL),
        ),
    )
}

@Composable
private fun AddressValue(text: String, compact: Boolean) {
    Text(
        text = text,
        style = TextStyle(
            color = NetLensWidgetColors.ink,
            fontWeight = FontWeight.Bold,
            fontSize = widgetSp(if (compact) WidgetType.VALUE_TIGHT else WidgetType.VALUE),
        ),
        // An IPv4 address wrapping mid-value ("192.168.1.12" / "9") is worse than an
        // ellipsis: it reads as a different address.
        maxLines = 1,
    )
}
