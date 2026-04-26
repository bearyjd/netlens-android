package com.ventoux.netlens.widget

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.datastore.preferences.core.edit
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WidgetRefreshWorker(
    private val appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val caps = network?.let { cm.getNetworkCapabilities(it) }
            val isConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            val ssid = try {
                val wm = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                wm.connectionInfo?.ssid?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" }
            } catch (_: Exception) {
                null
            }

            val dataStore = WidgetStateDefinition.getDataStore(appContext, "")
            dataStore.edit { prefs ->
                prefs[WidgetStateDefinition.IS_CONNECTED] = isConnected
                ssid?.let { prefs[WidgetStateDefinition.SSID] = it }
                prefs[WidgetStateDefinition.LAST_SCAN_TIMESTAMP] = System.currentTimeMillis()
                prefs[WidgetStateDefinition.IS_SCAN_RUNNING] = false
            }

            CompactWidget().updateAll(appContext)
            StandardWidget().updateAll(appContext)
            DashboardWidget().updateAll(appContext)

            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
