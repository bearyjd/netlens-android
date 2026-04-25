package us.beary.netlens.widget.action

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import us.beary.netlens.widget.IpWidgetStateDefinition
import us.beary.netlens.widget.NetLensWidget
import us.beary.netlens.widget.data.WidgetPreferencesRepository

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
