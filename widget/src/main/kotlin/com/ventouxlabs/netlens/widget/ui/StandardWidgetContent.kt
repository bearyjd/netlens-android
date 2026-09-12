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
import com.ventouxlabs.netlens.widget.util.Deeplink
import com.ventouxlabs.netlens.widget.util.relativeTimeLabel

/**
 * 2x2 widget content — grade and network, the public address, stats, chips, footer.
 *
 * ## On the dead band
 *
 * The single [SectionGap] below pools this card's surplus into one band above the chips,
 * and that band is large: ~282dp of usable height against ~150dp of content even after
 * this pass. It is a genuine mismatch between what the 2x2 shows and how tall it is, and
 * the type scale can only narrow it — the 147dp width caps the address at
 * [WidgetType.VALUE_TIGHT] and the chips at [WidgetType.LABEL], so there is no size left
 * to spend. Closing it properly needs either another row of content or a shorter box;
 * both are decisions above this file.
 *
 * What the gap must *not* become is a fixed spacer. It is a childless [SectionGap]
 * precisely so an overrun collapses it instead of deleting a sibling — see the invariant
 * in [FourByTwoVariant].
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
        Spacer(modifier = GlanceModifier.height(WidgetSpace.BASE))
        IpRow(state)
        Spacer(modifier = GlanceModifier.height(WidgetSpace.BASE))
        StatsRow(state)
        SectionGap()
        ChipsRow(state)
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
private fun ChipsRow(state: WidgetState) {
    val chips = resolveToolChips(state.chipRoutes).take(2)
    if (chips.isEmpty()) return
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

@Composable
private fun IpRow(state: WidgetState) {
    WidgetIpRow(state = state, showCountryName = false)
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
