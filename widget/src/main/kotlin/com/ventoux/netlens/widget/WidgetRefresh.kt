package com.ventoux.netlens.widget

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

suspend fun refreshAllWidgets(context: Context) {
    CompactWidget().updateAll(context)
    StandardWidget().updateAll(context)
    DashboardWidget().updateAll(context)
    FourByTwoWidget().updateAll(context)
}

fun enqueueWidgetRefresh(context: Context) {
    val workRequest = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        )
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        "widget_refresh",
        ExistingWorkPolicy.REPLACE,
        workRequest,
    )
}

// The XML updatePeriodMillis=30min broadcast is unreliable under Doze (idle
// devices coalesce it into maintenance windows that can be hours apart), so
// schedule the refresh through WorkManager too. KEEP preserves an existing
// cadence — safe to call from every receiver onEnabled() and from
// Application.onCreate().
fun enqueuePeriodicWidgetRefresh(context: Context) {
    val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
        30, TimeUnit.MINUTES,
        5, TimeUnit.MINUTES,
    )
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        )
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "widget_refresh_periodic",
        ExistingPeriodicWorkPolicy.KEEP,
        request,
    )
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
