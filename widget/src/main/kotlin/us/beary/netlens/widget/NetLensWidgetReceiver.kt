package us.beary.netlens.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class NetLensWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = NetLensWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val workRequest = OneTimeWorkRequestBuilder<IpWidgetRefreshWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
