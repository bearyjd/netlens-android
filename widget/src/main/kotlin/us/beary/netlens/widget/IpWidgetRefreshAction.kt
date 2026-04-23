package us.beary.netlens.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class IpWidgetRefreshAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val workRequest = OneTimeWorkRequestBuilder<IpWidgetRefreshWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
