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
        val raw = prefs[WidgetStateDefinition.CAPTIVE_PORTAL_URL]?.takeIf { it.isNotEmpty() }
        val url = raw?.let { Uri.parse(it) }
            ?.takeIf { it.scheme == "http" || it.scheme == "https" }
            ?.toString()
            ?: FALLBACK_PORTAL_URL
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    private companion object {
        const val FALLBACK_PORTAL_URL = "http://captive.apple.com"
    }
}
