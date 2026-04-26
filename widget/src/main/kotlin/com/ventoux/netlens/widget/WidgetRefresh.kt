package com.ventoux.netlens.widget

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.glance.appwidget.updateAll
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

suspend fun refreshAllWidgets(context: Context) {
    CompactWidget().updateAll(context)
    StandardWidget().updateAll(context)
    DashboardWidget().updateAll(context)
}

fun enqueueWidgetRefresh(context: Context) {
    val workRequest = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
    WorkManager.getInstance(context).enqueue(workRequest)
}

fun registerWidgetNetworkCallback(
    context: Context,
    current: ConnectivityManager.NetworkCallback?,
): ConnectivityManager.NetworkCallback {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    current?.let {
        try { cm.unregisterNetworkCallback(it) } catch (_: IllegalArgumentException) { }
    }
    val cb = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = enqueueWidgetRefresh(context)
        override fun onLost(network: Network) = enqueueWidgetRefresh(context)
    }
    cm.registerNetworkCallback(
        NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build(),
        cb,
    )
    return cb
}

fun unregisterWidgetNetworkCallback(
    context: Context,
    callback: ConnectivityManager.NetworkCallback?,
) {
    val cb = callback ?: return
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    try { cm.unregisterNetworkCallback(cb) } catch (_: IllegalArgumentException) { }
}
