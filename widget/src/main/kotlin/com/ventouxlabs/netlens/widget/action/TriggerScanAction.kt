package com.ventouxlabs.netlens.widget.action

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.ventouxlabs.netlens.widget.enqueueWidgetRefresh

class TriggerScanAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        enqueueWidgetRefresh(context)
    }
}
