package us.beary.netlens.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class WideWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NetLensWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueWidgetRefresh(context)
        schedulePeriodicWidgetRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelPeriodicWidgetRefresh(context)
    }
}
