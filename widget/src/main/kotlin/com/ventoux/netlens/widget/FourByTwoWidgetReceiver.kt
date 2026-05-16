package com.ventoux.netlens.widget

import android.content.Context
import android.net.ConnectivityManager
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class FourByTwoWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = FourByTwoWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueWidgetRefresh(context)
        enqueuePeriodicWidgetRefresh(context)
        networkCallback = registerWidgetNetworkCallback(context, networkCallback)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        unregisterWidgetNetworkCallback(context, networkCallback)
        networkCallback = null
    }

    private companion object {
        var networkCallback: ConnectivityManager.NetworkCallback? = null
    }
}
