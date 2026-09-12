package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.TextUnit
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
 * vanished from the view tree on a short box. [FourByTwoVariant.COMPACT] keeps both and
 * merely shrinks them: 11sp labels over 15sp addresses instead of 13/18, and no ISP name.
 *
 * Every modifier here is a Row-child modifier and therefore safe: the horizontal
 * `defaultWeight` in the root [modifier] divides *width*, never the scarce axis (the
 * fixed siblings are the 40/64dp flag column and two 6dp spacers, well inside even the
 * 250dp minWidth), and `fillMaxHeight` lowers to `match_parent` inside a horizontal
 * LinearLayout. What was unsafe was the vertical weight on the Row *above* these
 * columns; that is gone. See the invariant in [FourByTwoVariant].
 *
 * The weight comes from the caller because `defaultWeight` is `RowScope`-scoped and only
 * resolves on a direct child of the parent Row.
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
        AddressLabel(text = "WAN", fontSize = widgetSp(if (compact) 11f else 13f))
        AddressValue(
            text = state.publicIp.ifEmpty { "—.—.—.—" },
            fontSize = widgetSp(if (compact) 15f else 16f),
        )
        if (!compact && state.ispName.isNotEmpty()) {
            Text(
                text = state.ispName,
                style = TextStyle(
                    color = NetLensWidgetColors.inkSoft,
                    fontSize = widgetSp(12f),
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
        AddressLabel(text = "LAN", fontSize = widgetSp(if (compact) 11f else 13f))
        AddressValue(
            text = state.localIp.ifEmpty { "—" },
            fontSize = widgetSp(if (compact) 15f else 16f),
        )
    }
}

@Composable
private fun AddressLabel(text: String, fontSize: TextUnit) {
    Text(
        text = text,
        style = TextStyle(
            color = NetLensWidgetColors.inkSoft,
            fontSize = fontSize,
        ),
    )
}

@Composable
private fun AddressValue(text: String, fontSize: TextUnit) {
    Text(
        text = text,
        style = TextStyle(
            color = NetLensWidgetColors.ink,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
        ),
        // An IPv4 address wrapping mid-value ("192.168.1.12" / "9") is worse than an
        // ellipsis: it reads as a different address.
        maxLines = 1,
    )
}
