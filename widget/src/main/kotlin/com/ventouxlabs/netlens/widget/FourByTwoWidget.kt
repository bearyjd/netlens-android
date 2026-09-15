package com.ventouxlabs.netlens.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import com.ventouxlabs.netlens.widget.ui.FourByTwoWidgetContent

class FourByTwoWidget : GlanceAppWidget() {

    /**
     * Three height buckets let RemoteViews select compact, short, or full content. A launcher
     * that squashes the 4x2 into a short box (a 158dp fold-outer row) selects the 110dp bucket
     * and [FourByTwoWidgetContent] renders its compact variant; a 200-259dp allocation renders
     * the detail-free short variant, and 260dp or more renders full content.
     *
     * This is a cosmetic choice, not the fix for the dropped-children bug — neither
     * variant carries vertical weight on a content path any more, so both clip rather
     * than delete. See `FourByTwoVariant` for the measurements.
     */
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(250.dp, 110.dp), DpSize(250.dp, 200.dp), DpSize(250.dp, 260.dp)),
    )

    override val stateDefinition: GlanceStateDefinition<Preferences> = WidgetStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            FourByTwoWidgetContent(state = prefs.toWidgetState())
        }
    }
}
