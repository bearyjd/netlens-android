package com.ventoux.netlens.widget.action

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import com.ventoux.netlens.widget.WidgetStateDefinition

class OpenPortalAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val prefs = getAppWidgetState(context, WidgetStateDefinition, glanceId)
        val url = prefs[WidgetStateDefinition.CAPTIVE_PORTAL_URL]
            ?.takeIf { it.isNotEmpty() }
            ?: "http://captive.apple.com"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
