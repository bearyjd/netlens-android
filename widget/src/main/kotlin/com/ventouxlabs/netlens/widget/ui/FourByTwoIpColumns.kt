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
 * The 4x2's address payload. FULL stacks full-width WAN and LAN blocks; COMPACT keeps
 * the two side-by-side columns that fit its short box.
 *
 * COMPACT receives Row-child modifiers, so its horizontal `defaultWeight` divides width,
 * never the scarce axis. FULL receives `fillMaxWidth` modifiers as children of its nested
 * Column and carries no vertical weight. See the invariant in [FourByTwoVariant].
 *
 * COMPACT's weight comes from its caller because `defaultWeight` is `RowScope`-scoped and
 * only resolves on a direct child of the parent Row.
 *
 * ## Address widths and type roles
 *
 * The Pixel 9 Pro Fold launcher’s observed widget minimum is 341×317dp, leaving 325dp per
 * FULL address after 16dp horizontal padding. A 427dp box remains a useful wider reference,
 * not the target calibration. COMPACT still splits its address width after the 40dp VPN
 * column, 16dp padding and 16dp gutters:
 *
 * ```
 *                     341dp observed min     427dp wider reference
 *   FULL              325dp each             411dp each
 *   COMPACT           134.5dp each           177.5dp each
 * ```
 *
 * On a `fontScale` 1.15 device, [widgetSp] renders a design size at 1.15x and a bold IPv4
 * runs about 0.51dp per character per sp. The 15-character worst case therefore needs
 * about 246dp at [WidgetType.ADDRESS_FULL]'s 28sp, leaving ~79dp at the observed 341dp
 * minimum; device verification found the full payload with no ellipsis there. A launcher
 * that actually allocates the declared 250dp responsive bucket would leave only ~234dp,
 * so that case could ellipsize and remains unverified.
 *
 * COMPACT stays at [WidgetType.VALUE_TIGHT]: it has only ~134.5dp at a 341dp box, and the
 * 15-character address needs ~141dp at 16sp. [AddressValue] remains `maxLines = 1`,
 * because an ellipsized IP reads as a different address and a wrapped one
 * (`"192.168.1.12"` / `"9"`) reads as a wrong one.
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
        AddressValue(
            text = state.publicIp.ifEmpty { "—.—.—.—" },
            type = if (compact) WidgetType.VALUE_TIGHT else WidgetType.VALUE,
        )
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
        AddressValue(
            text = state.localIp.ifEmpty { "—" },
            type = if (compact) WidgetType.VALUE_TIGHT else WidgetType.VALUE,
        )
    }
}

/** FULL's full-width WAN block, including the ISP line and its existing IP-info action. */
@Composable
internal fun FourByTwoFullWidthWanAddress(state: WidgetState, modifier: GlanceModifier) {
    Column(
        modifier = modifier.clickable(
            actionRunCallback<OpenDeeplinkAction>(
                actionParametersOf(DeeplinkUriKey to Deeplink.IPINFO),
            ),
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AddressLabel(text = "WAN")
        AddressValue(text = state.publicIp.ifEmpty { "—.—.—.—" }, type = WidgetType.ADDRESS_FULL)
        if (state.ispName.isNotEmpty()) {
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

/** FULL's full-width LAN block, retaining its existing devices action. */
@Composable
internal fun FourByTwoFullWidthLanAddress(state: WidgetState, modifier: GlanceModifier) {
    Column(
        modifier = modifier.clickable(
            actionRunCallback<OpenDeeplinkAction>(
                actionParametersOf(DeeplinkUriKey to Deeplink.DEVICES),
            ),
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AddressLabel(text = "LAN")
        AddressValue(text = state.localIp.ifEmpty { "—" }, type = WidgetType.ADDRESS_FULL)
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
private fun AddressValue(text: String, type: Float) {
    Text(
        text = text,
        style = TextStyle(
            color = NetLensWidgetColors.ink,
            fontWeight = FontWeight.Bold,
            fontSize = widgetSp(type),
        ),
        // An IPv4 address wrapping mid-value ("192.168.1.12" / "9") is worse than an
        // ellipsis: it reads as a different address.
        maxLines = 1,
    )
}
