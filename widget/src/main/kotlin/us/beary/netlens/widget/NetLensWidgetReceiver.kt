package us.beary.netlens.widget

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class NetLensWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = NetLensWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueRefresh(context)
        schedulePeriodicRefresh(context)
        registerNetworkCallback(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        unregisterNetworkCallback(context)
    }

    private fun enqueueRefresh(context: Context) {
        val workRequest = OneTimeWorkRequestBuilder<IpWidgetRefreshWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }

    private fun schedulePeriodicRefresh(context: Context) {
        val periodicWork = PeriodicWorkRequestBuilder<IpWidgetRefreshWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork,
        )
    }

    private fun registerNetworkCallback(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                enqueueRefresh(context)
            }

            override fun onLost(network: Network) {
                enqueueRefresh(context)
            }
        }
        networkCallback = callback
        cm.registerNetworkCallback(request, callback)
    }

    private fun unregisterNetworkCallback(context: Context) {
        val callback = networkCallback ?: return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (_: IllegalArgumentException) {
            // Already unregistered
        }
        networkCallback = null
    }

    private companion object {
        const val PERIODIC_WORK_NAME = "netlens_widget_periodic_refresh"
        var networkCallback: ConnectivityManager.NetworkCallback? = null
    }
}
