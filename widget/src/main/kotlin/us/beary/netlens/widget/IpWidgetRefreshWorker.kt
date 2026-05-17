package us.beary.netlens.widget

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
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
import us.beary.netlens.feature.ipinfo.model.IpApiResponse
import java.net.Inet4Address
import java.net.NetworkInterface

class IpWidgetRefreshWorker(
    private val appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json { ignoreUnknownKeys = true },
                )
            }
            install(HttpTimeout) {
                connectTimeoutMillis = TIMEOUT_MS
                requestTimeoutMillis = TIMEOUT_MS
            }
        }

        return try {
            val response = client
                .get(IP_API_URL)
                .body<IpApiResponse>()

            val wifi = readWifiSignal()
            val dataStore = IpWidgetStateDefinition.getDataStore(appContext, "ip_widget")
            dataStore.edit { prefs ->
                prefs[IpWidgetStateDefinition.IP_KEY] = response.query
                prefs[IpWidgetStateDefinition.ISP_KEY] = response.isp
                prefs[IpWidgetStateDefinition.IS_VPN_KEY] = response.proxy
                prefs[IpWidgetStateDefinition.LAN_IP_KEY] = readLanIp()
                prefs[IpWidgetStateDefinition.LAST_UPDATED_KEY] = System.currentTimeMillis()
                prefs[IpWidgetStateDefinition.SIGNAL_DBM_KEY] = wifi?.rssi ?: 0
                prefs[IpWidgetStateDefinition.LINK_SPEED_KEY] = wifi?.linkSpeed ?: 0
            }

            NetLensWidget().updateAll(appContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            client.close()
        }
    }

    private data class WifiSignal(val rssi: Int, val linkSpeed: Int)

    @Suppress("DEPRECATION")
    private fun readWifiSignal(): WifiSignal? = try {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager
        val active = cm?.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) {
            null
        } else {
            val info: WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                caps.transportInfo as? WifiInfo
            } else {
                (appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
                    ?.connectionInfo
            }
            info?.let { WifiSignal(rssi = it.rssi, linkSpeed = it.linkSpeed) }
        }
    } catch (_: Exception) {
        null
    }

    private fun readLanIp(): String = try {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            .mapNotNull { it.hostAddress }
            .firstOrNull()
            .orEmpty()
    } catch (_: Exception) {
        ""
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val IP_API_URL =
            "http://ip-api.com/json/?fields=query,isp,org,as,country,regionName,city,lat,lon,proxy,hosting"
    }
}
