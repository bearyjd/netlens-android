package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.Alignment
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ventouxlabs.netlens.widget.WidgetState
import com.ventouxlabs.netlens.widget.action.DeeplinkUriKey
import com.ventouxlabs.netlens.widget.action.OpenDeeplinkAction
import com.ventouxlabs.netlens.widget.util.Deeplink

@Composable
internal fun WidgetIpRow(state: WidgetState, showCountryName: Boolean = false) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().clickable(
            actionRunCallback<OpenDeeplinkAction>(
                actionParametersOf(DeeplinkUriKey to Deeplink.IPINFO),
            ),
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.countryFlag.isNotEmpty()) {
            Text(
                text = state.countryFlag,
                style = TextStyle(fontSize = if (showCountryName) 14.sp else 16.sp),
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
        }
        Text(
            text = state.publicIp.ifEmpty { "—" },
            style = TextStyle(
                color = NetLensWidgetColors.ink,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            ),
            maxLines = 1,
        )
        if (showCountryName && state.countryName.isNotEmpty()) {
            Text(
                text = " · ${state.countryName}",
                style = TextStyle(
                    color = NetLensWidgetColors.inkSoft,
                    fontSize = 12.sp,
                ),
                maxLines = 1,
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

/** Full-width hairline rule between widget sections. */
@Composable
internal fun WidgetSectionDivider() {
    Spacer(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(1.dp)
            .background(NetLensWidgetColors.line),
    )
}
