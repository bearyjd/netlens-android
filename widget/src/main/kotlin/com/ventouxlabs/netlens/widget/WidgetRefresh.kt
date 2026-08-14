package com.ventouxlabs.netlens.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Every widget receiver paired with the [GlanceAppWidget] it hosts. This is the
 * authoritative source of the receiver→widget mapping — the same wiring declared in
 * each receiver's `glanceAppWidget` override and in the manifest.
 */
internal val WIDGET_RECEIVERS: List<Pair<Class<out GlanceAppWidgetReceiver>, () -> GlanceAppWidget>> =
    listOf(
        CompactWidgetReceiver::class.java to ::CompactWidget,
        StandardWidgetReceiver::class.java to ::StandardWidget,
        DashboardWidgetReceiver::class.java to ::DashboardWidget,
        FourByTwoWidgetReceiver::class.java to ::FourByTwoWidget,
    )

/**
 * Re-renders every placed NetLens widget.
 *
 * Dispatch is keyed off each receiver's manifest [ComponentName] rather than
 * `GlanceAppWidget.updateAll()`. `updateAll` resolves ids through Glance's persisted
 * `providerToReceiver` DataStore map, which can go stale for a widget instance placed
 * under an earlier build — when it does, `CompactWidget().updateAll()` pushes the
 * compact RemoteViews onto a 4x2 instance (observed on a Pixel 10, appwidget id 2).
 * Looking ids up by ComponentName binds each id to the widget class its receiver
 * actually declares, so a corrupted map cannot cross-render, and the next refresh
 * repairs an already-affected instance.
 */
suspend fun refreshAllWidgets(context: Context) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val glanceManager = GlanceAppWidgetManager(context)
    refreshWidgets(
        idsFor = { receiver -> appWidgetManager.getAppWidgetIds(ComponentName(context, receiver)) },
        glanceIdFor = { id -> glanceManager.getGlanceIdBy(id) },
        update = { widget, glanceId -> widget.update(context, glanceId) },
    )
}

/**
 * The bug-class logic behind [refreshAllWidgets], pulled out so it can be tested without any
 * Android framework dependency: given each receiver's own widget ids (however they're looked
 * up), never let one receiver's ids reach another receiver's widget instance. That crossing is
 * exactly what the historical `updateAll()` bug did — see [refreshAllWidgets]'s doc comment.
 */
internal suspend fun refreshWidgets(
    receivers: List<Pair<Class<out GlanceAppWidgetReceiver>, () -> GlanceAppWidget>> = WIDGET_RECEIVERS,
    idsFor: (Class<out GlanceAppWidgetReceiver>) -> IntArray,
    glanceIdFor: (Int) -> GlanceId,
    update: suspend (GlanceAppWidget, GlanceId) -> Unit,
) {
    receivers.forEach { (receiver, widgetFactory) ->
        val ids = idsFor(receiver)
        if (ids.isEmpty()) return@forEach
        val widget = widgetFactory()
        ids.forEach { id -> update(widget, glanceIdFor(id)) }
    }
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
