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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ventouxlabs.netlens.core.network.VpnState
import com.ventouxlabs.netlens.widget.R
import com.ventouxlabs.netlens.widget.WidgetState
import com.ventouxlabs.netlens.widget.action.DeeplinkUriKey
import com.ventouxlabs.netlens.widget.action.OpenDeeplinkAction
import com.ventouxlabs.netlens.widget.util.Deeplink

/**
 * 2x1 widget content — a WAN block over a LAN block, each a marker row above its address.
 *
 * ## Why the address gets its own line
 *
 * This layout used to be two single lines, `[flag][lock] WAN 192.168.1.129` and
 * `[bars] LAN 192.168.1.44`, and it was the emptiest widget in the family: 37dp of ink in
 * a 153dp box, 24% fill, because the address had to share ~120dp of a 171dp card with a
 * flag, a badge and a label and so could only be 11sp — below the 12sp legibility floor,
 * as were the 8sp labels and the 9sp cell-generation text beside them.
 *
 * Splitting each row into a marker line (flag/lock or signal bars, with the section label
 * pushed to the far end) and an address line hands the address the card's **full** width.
 * At [WidgetSpace.BASE] edge padding that is 155dp against the 143dp a 15-character public
 * IP needs at [WidgetType.VALUE_TIGHT] on a 1.15 device — so the widget's payload is now
 * the biggest thing on it, at a size the old layout had no room for.
 *
 * ## The invariant
 *
 * Neither address carries vertical weight. Glance lowers a Column child's
 * `defaultWeight()` to `height=0dp` + `layout_weight=1`, and a RemoteViews LinearLayout
 * resolves such a child to zero under overrun and drops it from the view tree outright —
 * where a fixed-height child merely clips. `widget_compact_info.xml` declares
 * `minHeight="40dp"`, so the launcher may legally hand this layout a box shorter than its
 * four lines. See the invariant and the measured failure in [FourByTwoVariant].
 *
 * Note the two weights below divide *opposite* axes, which is the whole distinction: the
 * `defaultWeight()` inside each marker Row divides **width** and is what pushes the label
 * to the trailing edge, while the childless [SectionGap] spacers divide **height** and may
 * do so precisely because they have nothing to lose when driven to zero. The address
 * `Text`s are Column children and carry no weight at all — in the old layout they were Row
 * children, where the same modifier was harmless.
 */
@Composable
fun CompactFullContent(state: WidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .widgetBackground()
            .padding(horizontal = WidgetSpace.BASE, vertical = WidgetSpace.TIGHT),
    ) {
        SectionGap()

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlagAndLock(state)
            Spacer(modifier = GlanceModifier.defaultWeight())
            SectionLabel(text = "WAN", deeplink = Deeplink.IPINFO)
        }
        AddressLine(
            value = state.publicIp.ifEmpty { "—.—.—.—" },
            deeplink = Deeplink.IPINFO,
        )

        SectionGap()
        WidgetSectionDivider()
        SectionGap()

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SignalBlock(state)
            Spacer(modifier = GlanceModifier.defaultWeight())
            SectionLabel(text = "LAN", deeplink = Deeplink.DEVICES)
        }
        AddressLine(
            value = state.localIp.ifEmpty { "—" },
            deeplink = Deeplink.DEVICES,
        )

        SectionGap()
    }
}

/**
 * The flag and VPN lock, opening VPN Status.
 *
 * It keeps its own `clickable` and the section label keeps a separate one: the label now
 * shares a Row with this block, and a single clickable spanning the Row would send a tap
 * on "WAN" to VPN Status instead of IP Info.
 */
@Composable
private fun FlagAndLock(state: WidgetState) {
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
        // 16dp/11dp matches the 4x1's badge. The 2x1 could not previously afford it and
        // ran 13dp/9dp; with the address on its own line the marker row has the room.
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
 * Signal bars, or the cell generation, or an empty-bars placeholder.
 *
 * Known asymmetry: this is the LAN block's marker, and it is shorter than the WAN block's.
 * [FlagAndLock] stacks a [WidgetType.FLAG] glyph against a 16dp badge for a ~22dp row,
 * while four bar glyphs beside a [WidgetType.LABEL] label make ~16dp. The old layout hid
 * it, because both markers shared a row with an address that was taller than either. It is
 * a consequence of giving the address its own line, not of the sizes — closing it would
 * mean a taller marker on this side, which is content this widget does not have.
 */
@Composable
private fun SignalBlock(state: WidgetState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (state.hasRssi) {
            SignalBarsIcon(level = state.rssiLevel)
        } else if (state.cellGeneration.isNotEmpty()) {
            Text(
                text = state.cellGeneration,
                style = TextStyle(
                    color = NetLensWidgetColors.inkSoft,
                    fontSize = widgetSp(WidgetType.LABEL),
                    fontWeight = FontWeight.Medium,
                ),
            )
        } else {
            Text(
                text = "▯▯▯▯",
                style = TextStyle(
                    color = NetLensWidgetColors.inkSoft,
                    fontSize = widgetSp(WidgetType.GLYPH_BARS),
                ),
            )
        }
    }
}

/** "WAN"/"LAN" at the trailing edge of its marker row, routing where its address does. */
@Composable
private fun SectionLabel(text: String, deeplink: String) {
    Text(
        text = text,
        style = TextStyle(
            color = NetLensWidgetColors.inkSoft,
            fontSize = widgetSp(WidgetType.LABEL),
            fontWeight = FontWeight.Medium,
        ),
        maxLines = 1,
        modifier = GlanceModifier.clickable(
            actionRunCallback<OpenDeeplinkAction>(
                actionParametersOf(DeeplinkUriKey to deeplink),
            ),
        ),
    )
}

/**
 * The address, owning the full card width.
 *
 * Deliberately carries no `defaultWeight()`. It is a Column child here — the modifier
 * would divide *height* and let a RemoteViews overrun delete the widget's payload, which
 * is the exact failure [FourByTwoVariant] records. It was safe on the old layout only
 * because it was a Row child there.
 */
@Composable
private fun AddressLine(value: String, deeplink: String) {
    Text(
        text = value,
        style = TextStyle(
            color = NetLensWidgetColors.ink,
            fontWeight = FontWeight.Bold,
            fontSize = widgetSp(WidgetType.VALUE_TIGHT),
        ),
        // An IPv4 wrapping mid-value ("192.168.1.12" / "9") reads as a different address.
        maxLines = 1,
        modifier = GlanceModifier.fillMaxWidth().clickable(
            actionRunCallback<OpenDeeplinkAction>(
                actionParametersOf(DeeplinkUriKey to deeplink),
            ),
        ),
    )
}
