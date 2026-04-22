package us.beary.netlens.widget

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
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

class CopyIpAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val prefs = getAppWidgetState(context, IpWidgetStateDefinition, glanceId)
        val ip = prefs[IpWidgetStateDefinition.IP_KEY].orEmpty()

        if (ip.isNotEmpty()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("IP Address", ip))
            Toast.makeText(context, "IP copied", Toast.LENGTH_SHORT).show()
        }
    }
}
