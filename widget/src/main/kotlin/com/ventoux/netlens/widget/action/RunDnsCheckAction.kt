package com.ventoux.netlens.widget.action

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.datastore.preferences.core.edit
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.ventoux.netlens.widget.FourByTwoWidget
import com.ventoux.netlens.widget.WidgetStateDefinition
import com.ventoux.netlens.widget.util.DnsLeakDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RunDnsCheckAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val dataStore = WidgetStateDefinition.getDataStore(context, "")
        dataStore.edit { it[WidgetStateDefinition.CHIP_DNS_RESULT] = "running" }
        FourByTwoWidget().updateAll(context)

        val result = withContext(Dispatchers.IO) {
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val network = cm.activeNetwork ?: return@withContext "fail"
                val linkProps = cm.getLinkProperties(network)
                val caps = cm.getNetworkCapabilities(network)
                val isVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                val dnsServers = linkProps?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()
                val vpnIface = if (isVpn) linkProps?.interfaceName.orEmpty() else ""
                if (DnsLeakDetector.isLeaking(dnsServers, isVpn, vpnIface, null)) "leak" else "clean"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                "fail"
            }
        }

        dataStore.edit { it[WidgetStateDefinition.CHIP_DNS_RESULT] = result }
        FourByTwoWidget().updateAll(context)
    }
}
