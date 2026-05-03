package com.ventoux.netlens.widget.action

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

class OpenPortalAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PORTAL_DETECT_URL)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    private companion object {
        const val PORTAL_DETECT_URL = "http://captive.apple.com"
    }
}
