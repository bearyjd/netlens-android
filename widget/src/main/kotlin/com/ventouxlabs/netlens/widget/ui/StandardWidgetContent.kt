package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ventouxlabs.netlens.widget.WidgetState
import com.ventouxlabs.netlens.widget.action.DeeplinkUriKey
import com.ventouxlabs.netlens.widget.action.OpenDeeplinkAction
import com.ventouxlabs.netlens.widget.action.TriggerScanAction
import com.ventouxlabs.netlens.widget.util.ChipDefinition
import com.ventouxlabs.netlens.widget.util.Deeplink
import com.ventouxlabs.netlens.widget.util.relativeTimeLabel

/**
 * 2x2 widget content — grade and network, a WAN block, a LAN block, and the card's foot.
 *
 * ## What closed the dead band
 *
 * This card is ~171x306dp, and it used to fill about half of it: a header, one address, a
 * stats line, two chips and a footer, with everything the card could not fill pooled into
 * a single band above the chips. The band was structural, not a spacing bug — there were
 * five things to show in a box sized for eight.
 *
 * So the LAN address moved in, and both addresses now use [CompactFullContent]'s
 * two-block shape at the same 171dp width: a marker row — flag and lock, or signal bars —
 * with its section label pushed to the trailing edge, and the address on its own line
 * beneath at the card's full width.
 *
 * Showing `state.localIp` here is new. It was previously held back by a "the 2x2 adds no
 * data" rule; the value is already in [WidgetState], every sibling widget shows it, and
 * it is the honest way to fill 180dp of dead space rather than spacing five elements
 * further apart.
 *
 * ## Seven children, and why the count is load-bearing
 *
 * **Glance ships generated container layouts for 0..10 children only**, and drops the
 * eleventh silently — see the third failure mode in [FourByTwoVariant]. Written flat,
 * this card is thirteen: a header, five gaps, two marker rows, two addresses, stats,
 * chips and a footer. Each block is therefore a nested Column with its own budget of ten
 * — the two address pairs, and the stats/chips/footer foot — leaving the root:
 *
 * ```
 *   1 header    3 WAN block    5 LAN block    7 foot
 *   2 gap       4 gap          6 gap
 * ```
 *
 * ## The invariant
 *
 * Three childless [SectionGap]s share the surplus between the four sections — roughly
 * 80dp at `fontScale` 1.15, so about 27dp each, which is arithmetic on natural text
 * heights rather than a device measurement. No content child carries vertical weight,
 * nested blocks included: under an overrun the gaps collapse and the card clips from the
 * bottom, where the footer is, instead of deleting a payload view. The marker rows'
 * `defaultWeight()` spacers divide *width*. See the invariant in [FourByTwoVariant].
 */
@Composable
fun StandardWidgetContent(state: WidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .widgetBackground()
            .padding(WidgetSpace.LOOSE),
    ) {
        HeaderRow(state)
        SectionGap()
        WanBlock(state)
        SectionGap()
        LanBlock(state)
        SectionGap()
        FootBlock(state)
    }
}

/**
 * Flag and lock, "WAN" at the trailing edge, and the public address beneath.
 *
 * The marker and the label are separately clickable on purpose: one `clickable` spanning
 * the Row would send a tap on "WAN" to VPN Status instead of IP Info.
 */
@Composable
private fun WanBlock(state: WidgetState) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetFlagAndLock(state = state)
            Spacer(modifier = GlanceModifier.defaultWeight())
            SectionLabel(text = "WAN", deeplink = Deeplink.IPINFO)
        }
        AddressLine(
            value = state.publicIp.ifEmpty { "—.—.—.—" },
            deeplink = Deeplink.IPINFO,
        )
    }
}

/** Signal, "LAN" at the trailing edge, and the local address beneath. */
@Composable
private fun LanBlock(state: WidgetState) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
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
    }
}

/** Signal bars, or the cell generation, or an empty-bars placeholder. */
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
 * 147dp inside the card's [WidgetSpace.LOOSE] padding, against the ~141dp a 15-character
 * public IP needs at [WidgetType.VALUE_TIGHT] on a `fontScale` 1.15 device. That is the
 * tightest fit on this widget and there are now two of them — but it is the only
 * arrangement in which the 2x2's payload is the 2x2's biggest text, and the marker row
 * above is what hands it the full width.
 *
 * Deliberately carries no `defaultWeight()`: it is a Column child here, where the
 * modifier would divide *height* and let a RemoteViews overrun delete the widget's
 * payload. See [FourByTwoVariant].
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

/**
 * The card's foot: latency and device count, the chips, and the scan timestamp.
 *
 * One nested Column rather than three children of the root — see the child-count note on
 * [StandardWidgetContent]. Its internal spacing is fixed rather than weighted, because a
 * nested Column wraps its content and a weighted Spacer inside one has no surplus to
 * divide: only the root is `fillMaxSize`, so only the root's gaps absorb anything. Three
 * children, or five with chips.
 */
@Composable
private fun FootBlock(state: WidgetState) {
    val chips = resolveToolChips(state.chipRoutes).take(2)
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        StatsRow(state)
        if (chips.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(WidgetSpace.BASE))
            ChipsRow(chips)
        }
        Spacer(modifier = GlanceModifier.height(WidgetSpace.BASE))
        FooterRow(state)
    }
}

// User-selected shortcut chips (Widget Chips setting, same chipRoutes preference the 4x1
// and 4x2 read). The 2x2 is half the width of those, so only the first two selections fit
// on its single chip row — weighted so two chips split the card evenly.
//
// [WidgetType.LABEL], not BODY, and this is the one place a chip stays at the floor: two
// chips split 147dp into ~71dp each, and the longest catalog labels ("WiFi Audit", "DNS
// Leak") already run close to that at 12sp. BODY would ellipsize labels that currently
// fit. The weights divide width, which is safe — see [FourByTwoVariant].
@Composable
private fun ChipsRow(chips: List<ChipDefinition>) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEachIndexed { index, chip ->
            if (index > 0) Spacer(modifier = GlanceModifier.width(WidgetSpace.TIGHT))
            Text(
                text = chip.shortLabel,
                style = TextStyle(
                    color = NetLensWidgetColors.onAccentSoft,
                    fontSize = widgetSp(WidgetType.LABEL),
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
                modifier = GlanceModifier
                    .defaultWeight()
                    .cornerRadius(6.dp)
                    .background(NetLensWidgetColors.accentSoft)
                    .padding(horizontal = WidgetSpace.TIGHT, vertical = WidgetSpace.TIGHT)
                    .clickable(
                        actionRunCallback<OpenDeeplinkAction>(
                            actionParametersOf(DeeplinkUriKey to Deeplink.forRoute(chip.route)),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun HeaderRow(state: WidgetState) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val gradeText = if (state.hasScore) state.scoreGrade else "?"
        val gradeColor = if (state.hasScore) {
            NetLensWidgetColors.scoreColor(state.scoreGrade)
        } else {
            NetLensWidgetColors.inkSoft
        }
        Text(
            text = gradeText,
            style = TextStyle(
                color = gradeColor,
                fontWeight = FontWeight.Bold,
                fontSize = widgetSp(WidgetType.DISPLAY),
            ),
            modifier = GlanceModifier.clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.POSTURE),
                ),
            ),
        )

        Spacer(modifier = GlanceModifier.width(WidgetSpace.BASE))

        Column(
            modifier = GlanceModifier.defaultWeight().clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.WIFI_AUDIT),
                ),
            ),
        ) {
            val networkName = when {
                !state.isConnected -> "Disconnected"
                state.ssid != null -> state.ssid
                else -> "Mobile"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = networkName,
                    style = TextStyle(
                        color = NetLensWidgetColors.ink,
                        fontWeight = FontWeight.Medium,
                        fontSize = widgetSp(WidgetType.BODY),
                    ),
                    maxLines = 1,
                )
                if (state.encryptionType.isNotEmpty()) {
                    val suffix = encryptionSuffix(state.encryptionType)
                    val color = encryptionColor(state.encryptionType)
                    Text(
                        text = " ${state.encryptionType}$suffix",
                        style = TextStyle(
                            color = color,
                            fontSize = widgetSp(WidgetType.LABEL),
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Latency and device count.
 *
 * Held at [WidgetType.LABEL] rather than promoted to BODY. These Texts have no
 * `maxLines`, so overflow wraps rather than clips and would break the row: at 14sp a
 * realistic `"120 ms · 24 devices"` needs ~145dp of the card's 147dp, which three-digit
 * values exceed. 12sp leaves ~16dp of slack on the same string.
 */
@Composable
private fun StatsRow(state: WidgetState) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val latencyText = if (state.hasLatency) "${state.latencyMs} ms" else "— ms"
        Text(
            text = latencyText,
            style = TextStyle(
                color = NetLensWidgetColors.inkSoft,
                fontSize = widgetSp(WidgetType.LABEL),
            ),
            modifier = GlanceModifier.clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.LATENCY),
                ),
            ),
        )

        Text(
            text = " · ",
            style = TextStyle(
                color = NetLensWidgetColors.inkSoft,
                fontSize = widgetSp(WidgetType.LABEL),
            ),
        )

        val deviceText = "${state.deviceCount} device${if (state.deviceCount != 1) "s" else ""}"
        Text(
            text = deviceText,
            style = TextStyle(
                color = NetLensWidgetColors.inkSoft,
                fontSize = widgetSp(WidgetType.LABEL),
            ),
            modifier = GlanceModifier.clickable(
                actionRunCallback<OpenDeeplinkAction>(
                    actionParametersOf(DeeplinkUriKey to Deeplink.DEVICES),
                ),
            ),
        )
    }
}

@Composable
private fun FooterRow(state: WidgetState) {
    val footerText = when {
        state.isScanRunning -> "Scanning…"
        state.lastScanTimestamp > 0L -> {
            val rel = relativeTimeLabel(state.lastScanTimestamp)
            if (state.isStale()) "Stale · $rel" else "Scanned $rel"
        }
        else -> "Not scanned"
    }
    val footerColor = if (state.isStale()) NetLensWidgetColors.warn else NetLensWidgetColors.inkSoft
    Text(
        text = footerText,
        style = TextStyle(
            color = footerColor,
            fontSize = widgetSp(WidgetType.LABEL),
        ),
        modifier = GlanceModifier.fillMaxWidth().clickable(
            actionRunCallback<TriggerScanAction>(),
        ),
    )
}

private fun encryptionSuffix(type: String): String = when (type.uppercase()) {
    "WPA3" -> " (secure)"
    "WEP" -> " (weak)"
    else -> ""
}

private fun encryptionColor(type: String): ColorProvider = when (type.uppercase()) {
    "WPA3" -> NetLensWidgetColors.accent
    "WEP" -> NetLensWidgetColors.stamp
    else -> NetLensWidgetColors.inkSoft
}
