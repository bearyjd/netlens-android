package com.ventoux.netlens.widget.action

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import com.ventoux.netlens.widget.IpWidgetStateDefinition
import com.ventoux.netlens.widget.NetLensWidget
import com.ventoux.netlens.widget.data.WidgetPreferencesRepository

class CarouselNextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        advanceCarousel(context, glanceId, 1)
    }
}

class CarouselPrevAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        advanceCarousel(context, glanceId, -1)
    }
}

private suspend fun advanceCarousel(context: Context, glanceId: GlanceId, delta: Int) {
    val pageCount = WidgetPreferencesRepository.get(context).pages.size
    if (pageCount <= 1) return

    updateAppWidgetState(context, IpWidgetStateDefinition, glanceId) { prefs ->
        val mutable = prefs.toMutablePreferences()
        val current = mutable[IpWidgetStateDefinition.CAROUSEL_PAGE_KEY] ?: 0
        mutable[IpWidgetStateDefinition.CAROUSEL_PAGE_KEY] = (current + delta + pageCount) % pageCount
        mutable
    }
    NetLensWidget().update(context, glanceId)
}
