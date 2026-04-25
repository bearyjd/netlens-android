package us.beary.netlens.widget

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.datastore.preferences.core.edit
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import us.beary.netlens.widget.model.WidgetIpResponse
import java.net.Inet4Address
import java.net.NetworkInterface

class IpWidgetRefreshWorker(
    private val appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                connectTimeoutMillis = TIMEOUT_MS
                requestTimeoutMillis = TIMEOUT_MS
            }
        }.use { client -> try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val caps = network?.let { cm.getNetworkCapabilities(it) }
            val linkProps = network?.let { cm.getLinkProperties(it) }

            val isConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val isVpn = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)?.not() ?: false

            val ssid = try {
                val wm = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                wm.connectionInfo?.ssid?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" }
            } catch (_: Exception) {
                null
            }

            val localIp = getLocalIpAddress()
            val gateway = linkProps?.routes
                ?.firstOrNull { it.isDefaultRoute }
                ?.gateway?.hostAddress
            val dnsServers = linkProps?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()

            var publicIp = ""
            var isp = ""
            var countryCode = ""
            if (isConnected) {
                try {
                    val response = client.get(IP_API_URL).body<WidgetIpResponse>()
                    publicIp = response.ip
                    isp = response.org
                    countryCode = response.countryCode
                } catch (_: Exception) {
                    // Keep empty — offline or API unreachable
                }
            }

            val dataStore = IpWidgetStateDefinition.getDataStore(appContext, "ip_widget")
            dataStore.edit { prefs ->
                prefs[IpWidgetStateDefinition.IP_KEY] = publicIp
                prefs[IpWidgetStateDefinition.ISP_KEY] = isp
                prefs[IpWidgetStateDefinition.IS_VPN_KEY] = isVpn
                prefs[IpWidgetStateDefinition.COUNTRY_CODE_KEY] = countryCode
                prefs[IpWidgetStateDefinition.IS_CONNECTED_KEY] = isConnected
                ssid?.let { prefs[IpWidgetStateDefinition.SSID_KEY] = it }
                localIp?.let { prefs[IpWidgetStateDefinition.LOCAL_IP_KEY] = it }
                gateway?.let { prefs[IpWidgetStateDefinition.GATEWAY_KEY] = it }
                prefs[IpWidgetStateDefinition.DNS_SERVERS_KEY] = dnsServers.joinToString(",")
            }

            NetLensWidget().updateAll(appContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        } }
    }

    private fun getLocalIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val IP_API_URL = "https://ipapi.co/json/"
    }
}
