package com.ventouxlabs.netlens.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import com.ventouxlabs.netlens.widget.WidgetState

@Composable
fun FourByTwoWidgetContent(state: WidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(16.dp)
            .background(NetLensWidgetColors.background),
    ) {
        WidgetHeaderRow(
            state = state,
            modifier = GlanceModifier.padding(horizontal = 10.dp),
        )

        Spacer(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(NetLensWidgetColors.line),
        )

        DashboardWidgetContent(
            state = state,
            showHeader = false,
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )

        Spacer(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(NetLensWidgetColors.line),
        )

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusLineContent(
                state = state,
                modifier = GlanceModifier.defaultWeight(),
            )

            Spacer(
                modifier = GlanceModifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(NetLensWidgetColors.line),
            )

            ToolChipsRow(
                state = state,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}
