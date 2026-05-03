package com.ventoux.netlens.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import com.ventoux.netlens.widget.ui.FourByTwoWidgetContent

class FourByTwoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override val stateDefinition: GlanceStateDefinition<Preferences> = WidgetStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            FourByTwoWidgetContent(state = prefs.toWidgetState())
        }
    }
}
