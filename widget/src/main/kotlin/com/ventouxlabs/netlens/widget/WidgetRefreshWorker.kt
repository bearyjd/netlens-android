package com.ventouxlabs.netlens.widget

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.ventouxlabs.netlens.core.network.VpnState
import com.ventouxlabs.netlens.core.network.detectVpnState
import com.ventouxlabs.netlens.core.network.getPhysicalNetwork
import androidx.datastore.preferences.core.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ventouxlabs.netlens.core.data.dao.LanScanHistoryDao
import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.widget.model.WidgetIpResponse
import com.ventouxlabs.netlens.widget.util.DnsLeakDetector
import com.ventouxlabs.netlens.widget.util.NetworkCollector
import com.ventouxlabs.netlens.widget.util.PingMeasurement
import com.ventouxlabs.netlens.widget.util.gateSsidForTransport
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class WidgetRefreshWorker(
    private val appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun lanScanHistoryDao(): LanScanHistoryDao
        fun userPreferencesRepository(): UserPreferencesRepository
    }

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                appContext,
                WorkerEntryPoint::class.java,
            )
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val caps = network?.let { cm.getNetworkCapabilities(it) }
            val isConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            // Gate the SSID by physical-transport. WifiManager.connectionInfo keeps
            // returning the last associated SSID even after the device switches to
            // cellular, so the value must be dropped when the physical link is not WiFi.
            val physicalCaps = getPhysicalNetwork(cm)?.let { cm.getNetworkCapabilities(it) }
            val onWifi = physicalCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val rawSsid = try {
                val wm = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION") // SSID requires location on API 31+; degrades to null
                wm.connectionInfo?.ssid?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" }
            } catch (_: Exception) {
                null
            }
            val ssid = gateSsidForTransport(isWifiTransport = onWifi, rawSsid = rawSsid)

            val encryptionType = try {
                detectEncryptionType(appContext, caps)
            } catch (_: Exception) {
                null
            }

            val vpnState = detectVpnState(cm)
            val isVpnActive = vpnState !is VpnState.None

            val deviceCount = try {
                entryPoint.lanScanHistoryDao()
                    .getRecent(1).first()
                    .firstOrNull()?.deviceCount ?: 0
            } catch (_: Exception) {
                0
            }

            // Kept outside resolveWidgetScore — as before the extraction — so the offline path
            // still skips the DataStore round-trip it would never use.
            val persistedScore = if (isConnected) {
                try {
                    entryPoint.userPreferencesRepository().postureScore.first()
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
            val score = resolveWidgetScore(
                isConnected = isConnected,
                persisted = persistedScore,
                nowMs = System.currentTimeMillis(),
                encryptionType = encryptionType,
                deviceCount = deviceCount,
                vpnState = vpnState,
            )

            val consentGranted = try {
                entryPoint.userPreferencesRepository()
                    .ipInfoConsentGranted.first()
            } catch (_: Exception) {
                false
            }

            val ipData: WidgetIpResponse? = if (isConnected && consentGranted) {
                try {
                    fetchIpInfo()
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }

            val latencyMs = if (isConnected) {
                try {
                    measureLatency()
                } catch (_: Exception) {
                    -1L
                }
            } else {
                -1L
            }

            val collected = NetworkCollector.collect(appContext)

            val dnsServers = collected.dnsServers
            val routingMode = if (isVpnActive) {
                val routes = cm.getLinkProperties(network)?.routes ?: emptyList()
                val vpnDefault = routes.any { route ->
                    route.isDefaultRoute &&
                        route.`interface` == collected.vpnInterfaceName
                }
                DnsLeakDetector.detectRoutingMode(true, vpnDefault)
            } else {
                DnsLeakDetector.detectRoutingMode(false, false)
            }
            val isDnsLeaking = DnsLeakDetector.isLeaking(
                dnsServers, isVpnActive, collected.vpnInterfaceName, null,
            )

            val pingResult = if (isConnected) {
                PingMeasurement.measure()?.also { PingMeasurement.record(it) }
            } else {
                null
            }

            val snapshot = WidgetSnapshot(
                isConnected = isConnected,
                ssid = ssid,
                encryptionType = encryptionType,
                score = score,
                ipDisplay = resolveIpDisplay(ipData),
                latencyMs = latencyMs,
                pingMs = pingResult ?: -1,
                deviceCount = deviceCount,
                vpnState = vpnState,
                collected = collected,
                dnsServers = dnsServers,
                routingMode = routingMode,
                isDnsLeaking = isDnsLeaking,
                nowMs = System.currentTimeMillis(),
            )

            val dataStore = WidgetStateDefinition.getDataStore(appContext, "")
            dataStore.edit { prefs -> prefs.applyWidgetSnapshot(snapshot) }

            refreshAllWidgets(appContext)

            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.failure()
        }
    }
}

internal data class WidgetScore(
    val grade: String,
    val colorArgb: Int,
    val issueCount: Int,
    val topIssue: String?,
    val topIssueId: String?,
)

internal fun computeWidgetScore(
    encryptionType: String?,
    deviceCount: Int,
    vpnState: VpnState,
): WidgetScore {
    val encScore = encryptionScore(encryptionType)
    val devScore = deviceCountScore(deviceCount)
    val vpnScore = when (vpnState) {
        VpnState.FullTunnel -> 100
        VpnState.SplitTunnel -> 60
        VpnState.None -> 40
    }

    val numeric = ((encScore * 50 + devScore * 30 + vpnScore * 20) / 100).coerceIn(0, 100)
    val grade = gradeFor(numeric)

    val vpnIssue: Pair<String, String>? = when (vpnState) {
        VpnState.FullTunnel -> null
        VpnState.SplitTunnel -> "VPN is split-tunnel" to "vpn"
        VpnState.None -> "VPN not active" to "vpn"
    }

    val issues = listOfNotNull(
        ("Weak or no encryption" to "encryption").takeIf { encScore <= 20 },
        ("Too many devices on network" to "device_count").takeIf { devScore <= 40 },
        vpnIssue,
    )

    val colorArgb = when (grade) {
        "A", "B" -> 0xFF4CAF50.toInt()
        "C" -> 0xFFFFC107.toInt()
        else -> 0xFFF44336.toInt()
    }

    return WidgetScore(
        grade = grade,
        colorArgb = colorArgb,
        issueCount = issues.size,
        topIssue = issues.firstOrNull()?.first,
        topIssueId = issues.firstOrNull()?.second,
    )
}

internal fun gradeColorArgb(grade: String): Int = when (grade) {
    "A", "B" -> 0xFF4CAF50.toInt()
    "C" -> 0xFFFFC107.toInt()
    else -> 0xFFF44336.toInt()
}

internal fun gradeFor(score: Int): String = when {
    score >= 90 -> "A"
    score >= 75 -> "B"
    score >= 60 -> "C"
    score >= 40 -> "D"
    else -> "F"
}

internal fun encryptionScore(type: String?): Int {
    if (type == null) return 0
    val upper = type.uppercase()
    return when {
        upper.contains("WPA3") -> 100
        upper.contains("OWE") || upper.contains("ENHANCED_OPEN") -> 80
        upper.contains("WPA2") -> 70
        upper.contains("WPA") -> 50
        upper.contains("WEP") -> 20
        upper.isEmpty() || upper == "OPEN" -> 0
        else -> 40
    }
}

internal fun deviceCountScore(count: Int): Int = when {
    count <= 5 -> 100
    count <= 15 -> 80
    count <= 30 -> 60
    else -> 40
}

internal fun isEncryptionSecure(type: String?): Boolean {
    if (type == null) return true
    val upper = type.uppercase()
    return upper.contains("WPA3") || upper.contains("WPA2") || upper.contains("OWE")
}

internal fun detectEncryptionType(
    context: Context,
    caps: NetworkCapabilities? = null,
): String? {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return null
    val activeCaps = caps ?: run {
        val network = cm.activeNetwork ?: return null
        cm.getNetworkCapabilities(network) ?: return null
    }
    if (!activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

    // API 31+: TransportInfo carries security type without location permission
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val wifiInfo = activeCaps.transportInfo as? WifiInfo ?: return null
        return when (wifiInfo.currentSecurityType) {
            WifiInfo.SECURITY_TYPE_OPEN -> "Open"
            WifiInfo.SECURITY_TYPE_WEP -> "WEP"
            WifiInfo.SECURITY_TYPE_PSK -> "WPA2"
            WifiInfo.SECURITY_TYPE_SAE -> "WPA3"
            WifiInfo.SECURITY_TYPE_EAP -> "WPA2-Enterprise"
            WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE -> "WPA3-Enterprise"
            WifiInfo.SECURITY_TYPE_OWE -> "OWE"
            WifiInfo.SECURITY_TYPE_WAPI_PSK -> "WAPI"
            WifiInfo.SECURITY_TYPE_WAPI_CERT -> "WAPI-Cert"
            else -> "Unknown"
        }
    }

    // Pre-API 31 fallback using deprecated WifiManager APIs
    val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
    @Suppress("DEPRECATION") // Pre-S fallback; API 31+ uses TransportInfo above
    val info: WifiInfo = wm.connectionInfo ?: return null
    @Suppress("DEPRECATION") // Pre-S fallback; requires ACCESS_FINE_LOCATION
    val scanResults = wm.scanResults ?: return null
    @Suppress("DEPRECATION") // Pre-S fallback; BSSID comparison
    val connected = scanResults.find { it.BSSID == info.bssid } ?: return null
    return parseCapabilities(connected.capabilities)
}

internal fun parseCapabilities(capabilities: String): String = when {
    capabilities.contains("WPA3") || capabilities.contains("SAE") -> "WPA3"
    capabilities.contains("OWE") -> "OWE"
    capabilities.contains("WPA2") || capabilities.contains("RSN") -> "WPA2"
    capabilities.contains("WPA") -> "WPA"
    capabilities.contains("WEP") -> "WEP"
    else -> "Open"
}

/**
 * Appends a new latency sample (ms) to a comma-separated history, capping it at the
 * most recent [cap] samples. A `null` [sample] (missing/failed measurement) is a
 * no-op — the existing history is returned unchanged (malformed entries dropped).
 */
internal fun appendLatencySample(existingCsv: String?, sample: Int?, cap: Int = 12): String {
    val existing = existingCsv
        ?.split(",")
        ?.mapNotNull { it.trim().toIntOrNull() }
        .orEmpty()
    if (sample == null) return existing.joinToString(",")
    return (existing + sample).takeLast(cap).joinToString(",")
}

private val httpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(HttpTimeout) {
        connectTimeoutMillis = 5_000
        requestTimeoutMillis = 8_000
    }
}

private suspend fun fetchIpInfo(): WidgetIpResponse {
    return httpClient.get("https://ipinfo.io/json").body()
}

private suspend fun measureLatency(): Long = withContext(Dispatchers.IO) {
    val start = System.currentTimeMillis()
    Socket().use { socket ->
        socket.connect(InetSocketAddress("8.8.8.8", 53), 3000)
    }
    System.currentTimeMillis() - start
}
