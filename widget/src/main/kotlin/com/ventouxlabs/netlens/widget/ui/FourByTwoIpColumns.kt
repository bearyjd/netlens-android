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
 * The dashboard's two address columns — the widget's whole payload, and the pair that
 * vanished from the view tree on a short box. Both variants now render at the same sizes;
 * [FourByTwoVariant.COMPACT] differs only in dropping the ISP name, because the 1sp the
 * old 11/15 pair saved over 13/17 was worth about 1dp of height and cost the compact
 * variant a size of its own to reason about.
 *
 * Every modifier here is a Row-child modifier and therefore safe: the horizontal
 * `defaultWeight` in the root [modifier] divides *width*, never the scarce axis (the
 * fixed siblings are the 40/44dp flag column and two [WidgetSpace.BASE] spacers, well
 * inside even the 250dp minWidth). What was unsafe was the vertical weight on the Row
 * *above* these columns; that is gone. See the invariant in [FourByTwoVariant].
 *
 * The weight comes from the caller because `defaultWeight` is `RowScope`-scoped and only
 * resolves on a direct child of the parent Row.
 *
 * ## Why the address is [WidgetType.VALUE_TIGHT] and not [WidgetType.VALUE]
 *
 * The two columns split what the flag column and the spacers leave: inside the 341dp box
 * these were measured on, 341 - 16 (padding) - 44 (flag) - 16 (spacers) = 265dp, or
 * ~132dp each. That is the same ~132dp the columns had at the old 10dp padding and 6dp
 * spacers — the rhythm moved 4dp from the card edge into the gutters and the column
 * budget did not change.
 *
 * Against that 132dp, on a `fontScale` 1.15 device where [widgetSp] renders a design size
 * at 1.15x:
 *
 * ```
 *   "192.168.1.129"   13 chars   ~122dp at 16sp   ~129dp at 17sp   ~137dp at 18sp
 *   "185.199.108.153" 15 chars   ~143dp at 16sp   ~152dp at 17sp   ~161dp at 18sp
 * ```
 *
 * So [WidgetType.VALUE] never fits this column and 16sp is the role that does. The
 * 13-character case gains real margin in the process — ~11dp, against the ~3dp it had at
 * 17sp, which is why the previous size was described as fitting "with no margin".
 *
 * **A 15-character public IP still does not fit, and did not at 17sp either.** It is
 * ~10dp over, [AddressValue] is `maxLines = 1`, and an ellipsized IP reads as a different
 * address. Closing that needs width, not a smaller size — 14sp would fit it but would put
 * the widget's payload below its own status line. The width to take is the 44dp flag
 * column's; see [FourByTwoVpnColumn].
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
        AddressValue(text = state.publicIp.ifEmpty { "—.—.—.—" })
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
        AddressValue(text = state.localIp.ifEmpty { "—" })
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
private fun AddressValue(text: String) {
    Text(
        text = text,
        style = TextStyle(
            color = NetLensWidgetColors.ink,
            fontWeight = FontWeight.Bold,
            fontSize = widgetSp(WidgetType.VALUE_TIGHT),
        ),
        // An IPv4 address wrapping mid-value ("192.168.1.12" / "9") is worse than an
        // ellipsis: it reads as a different address.
        maxLines = 1,
    )
}
