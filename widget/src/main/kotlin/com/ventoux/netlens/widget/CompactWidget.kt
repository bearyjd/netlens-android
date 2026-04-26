package com.ventoux.netlens.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import com.ventoux.netlens.widget.ui.CompactWidgetContent

class CompactWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<Preferences> = WidgetStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            CompactWidgetContent(state = prefs.toWidgetState())
        }
    }
}
