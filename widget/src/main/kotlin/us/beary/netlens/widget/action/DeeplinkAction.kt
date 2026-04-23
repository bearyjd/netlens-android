package us.beary.netlens.widget.action

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import us.beary.netlens.widget.IpWidgetStateDefinition
import us.beary.netlens.widget.util.Deeplink

val DeeplinkUriKey = ActionParameters.Key<String>("deeplink_uri")
val CopyTextKey = ActionParameters.Key<String>("copy_text")
val CopyLabelKey = ActionParameters.Key<String>("copy_label")

class OpenDeeplinkAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val uri = parameters[DeeplinkUriKey] ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(intent)
    }
}

class CopyAndToastAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val text = parameters[CopyTextKey] ?: return
        val label = parameters[CopyLabelKey] ?: "Copied"
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }
}

class CopyPublicIpAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val prefs = getAppWidgetState(context, IpWidgetStateDefinition, glanceId)
        val ip = prefs[IpWidgetStateDefinition.IP_KEY].orEmpty()
        if (ip.isNotEmpty()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Public IP", ip))
            Toast.makeText(context, "IP copied", Toast.LENGTH_SHORT).show()
        }
    }
}

class CopyGatewayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val prefs = getAppWidgetState(context, IpWidgetStateDefinition, glanceId)
        val gw = prefs[IpWidgetStateDefinition.GATEWAY_KEY].orEmpty()
        if (gw.isNotEmpty()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Gateway", gw))
            Toast.makeText(context, "Gateway copied", Toast.LENGTH_SHORT).show()
        }
    }
}

class OpenAppAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        } ?: return
        context.startActivity(intent)
    }
}
