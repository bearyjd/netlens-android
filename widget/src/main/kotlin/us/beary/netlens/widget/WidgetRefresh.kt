package us.beary.netlens.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

suspend fun refreshAllWidgets(context: Context) {
    NetLensWidget().updateAll(context)
}
