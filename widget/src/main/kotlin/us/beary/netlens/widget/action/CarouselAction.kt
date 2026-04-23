package us.beary.netlens.widget.action

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import us.beary.netlens.widget.IpWidgetStateDefinition
import us.beary.netlens.widget.NetLensWidget
import us.beary.netlens.widget.data.WidgetPreferencesRepository

class CarouselNextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        advanceCarousel(context, 1)
    }
}

class CarouselPrevAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        advanceCarousel(context, -1)
    }
}

private suspend fun advanceCarousel(context: Context, delta: Int) {
    val pageCount = WidgetPreferencesRepository.get(context).pages.size
    if (pageCount <= 1) return

    val dataStore = IpWidgetStateDefinition.getDataStore(context, "ip_widget")
    dataStore.edit { prefs ->
        val current = prefs[IpWidgetStateDefinition.CAROUSEL_PAGE_KEY] ?: 0
        prefs[IpWidgetStateDefinition.CAROUSEL_PAGE_KEY] = (current + delta + pageCount) % pageCount
    }
    NetLensWidget().updateAll(context)
}
