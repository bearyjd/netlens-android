package com.ventoux.netlens.feature.wifi.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import com.ventoux.netlens.feature.wifi.model.ConnectedWifiInfo
import com.ventoux.netlens.feature.wifi.model.WifiNetwork
import javax.inject.Inject

class WifiScannerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : WifiScanner {

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    override fun scan(): Flow<List<WifiNetwork>> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val results = wifiManager.scanResults.map { result ->
                    val freq = result.frequency
                    WifiNetwork(
                        ssid = result.SSID ?: "",
                        bssid = result.BSSID ?: "",
                        frequency = freq,
                        channelNumber = ChannelCalculator.frequencyToChannel(freq),
                        channelWidth = extractChannelWidth(result),
                        level = result.level,
                        security = extractSecurity(result),
                        capabilities = result.capabilities ?: "",
                    )
                }
                trySend(results)
            }
        }

        context.registerReceiver(
            receiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
        )

        @Suppress("DEPRECATION")
        wifiManager.startScan()

        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }

    override fun observeConnected(): Flow<ConnectedWifiInfo?> = flow {
        val info = getConnectedInfo()
        emit(info)
    }

    private fun getConnectedInfo(): ConnectedWifiInfo? {
        @Suppress("DEPRECATION")
        val wifiInfo: WifiInfo = wifiManager.connectionInfo ?: return null
        val ssid = wifiInfo.ssid?.removePrefix("\"")?.removeSuffix("\"") ?: return null
        if (ssid == "<unknown ssid>") return null

        @Suppress("DEPRECATION")
        val ipInt = wifiInfo.ipAddress
        val ipAddress = if (ipInt != 0) {
            "${ipInt and 0xFF}.${ipInt shr 8 and 0xFF}.${ipInt shr 16 and 0xFF}.${ipInt shr 24 and 0xFF}"
        } else {
            null
        }

        return ConnectedWifiInfo(
            ssid = ssid,
            bssid = wifiInfo.bssid ?: "",
            linkSpeedMbps = wifiInfo.linkSpeed,
            frequency = wifiInfo.frequency,
            rssi = wifiInfo.rssi,
            ipAddress = ipAddress,
        )
    }

    private fun extractChannelWidth(result: android.net.wifi.ScanResult): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when (result.channelWidth) {
                android.net.wifi.ScanResult.CHANNEL_WIDTH_20MHZ -> 20
                android.net.wifi.ScanResult.CHANNEL_WIDTH_40MHZ -> 40
                android.net.wifi.ScanResult.CHANNEL_WIDTH_80MHZ -> 80
                android.net.wifi.ScanResult.CHANNEL_WIDTH_160MHZ -> 160
                android.net.wifi.ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
                else -> 20
            }
        } else {
            20
        }
    }

    private fun extractSecurity(result: android.net.wifi.ScanResult): String {
        val caps = result.capabilities ?: return "Open"
        return when {
            caps.contains("WPA3") -> "WPA3"
            caps.contains("WPA2") -> "WPA2"
            caps.contains("WPA") -> "WPA"
            caps.contains("WEP") -> "WEP"
            else -> "Open"
        }
    }
}
