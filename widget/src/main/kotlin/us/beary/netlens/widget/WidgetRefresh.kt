package us.beary.netlens.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.glance.appwidget.updateAll

suspend fun refreshAllWidgets(context: Context) {
    NetLensWidget().updateAll(context)
}

suspend fun resetCarouselAndRefreshWidgets(context: Context) {
    val dataStore = IpWidgetStateDefinition.getDataStore(context, "")
    dataStore.edit { prefs ->
        prefs[IpWidgetStateDefinition.CAROUSEL_PAGE_KEY] = 0
    }
    NetLensWidget().updateAll(context)
}
