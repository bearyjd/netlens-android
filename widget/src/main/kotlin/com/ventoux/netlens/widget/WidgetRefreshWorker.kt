package com.ventoux.netlens.widget

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
import com.ventoux.netlens.core.data.dao.LanScanHistoryDao
import com.ventoux.netlens.widget.model.WidgetIpResponse
import com.ventoux.netlens.widget.util.toFlagEmoji
import dagger.hilt.EntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
    }

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

            val encryptionType = try {
                detectEncryptionType(appContext)
            } catch (_: Exception) {
                null
            }

            val isVpnActive = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

            val deviceCount = try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    appContext,
                    WorkerEntryPoint::class.java,
                )
                entryPoint.lanScanHistoryDao()
                    .getRecent(1).first()
                    .firstOrNull()?.deviceCount ?: 0
            } catch (_: Exception) {
                0
            }

            val score = if (isConnected) {
                computeWidgetScore(encryptionType, deviceCount, isVpnActive)
            } else {
                null
            }

            var publicIp = ""
            var countryFlag = ""
            var countryName = ""
            var countryCode = ""
            var ispName = ""
            var asnName = ""

            if (isConnected) {
                try {
                    val ipData = fetchIpInfo()
                    publicIp = ipData.query
                    countryName = ipData.country
                    countryCode = ipData.countryCode
                    countryFlag = ipData.countryCode.toFlagEmoji()
                    ispName = ipData.isp
                    asnName = ipData.asName
                } catch (_: Exception) { }
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

            val dataStore = WidgetStateDefinition.getDataStore(appContext, "")
            dataStore.edit { prefs ->
                prefs[WidgetStateDefinition.IS_CONNECTED] = isConnected
                ssid?.let { prefs[WidgetStateDefinition.SSID] = it }
                prefs[WidgetStateDefinition.LAST_SCAN_TIMESTAMP] = System.currentTimeMillis()
                prefs[WidgetStateDefinition.IS_SCAN_RUNNING] = false

                encryptionType?.let { prefs[WidgetStateDefinition.ENCRYPTION_TYPE] = it }
                prefs[WidgetStateDefinition.IS_ENCRYPTION_SECURE] = isEncryptionSecure(encryptionType)

                if (score != null) {
                    prefs[WidgetStateDefinition.SCORE_GRADE] = score.grade
                    prefs[WidgetStateDefinition.SCORE_COLOR_ARGB] = score.colorArgb
                    prefs[WidgetStateDefinition.ISSUE_COUNT] = score.issueCount
                    score.topIssue?.let { prefs[WidgetStateDefinition.TOP_ISSUE] = it }
                    score.topIssueId?.let { prefs[WidgetStateDefinition.TOP_ISSUE_ID] = it }
                }

                if (publicIp.isNotEmpty()) {
                    prefs[WidgetStateDefinition.PUBLIC_IP] = publicIp
                    prefs[WidgetStateDefinition.COUNTRY_FLAG] = countryFlag
                    prefs[WidgetStateDefinition.COUNTRY_NAME] = countryName
                    prefs[WidgetStateDefinition.COUNTRY_CODE] = countryCode
                    prefs[WidgetStateDefinition.ISP_NAME] = ispName
                    prefs[WidgetStateDefinition.ASN_NAME] = asnName
                }

                prefs[WidgetStateDefinition.LATENCY_MS] = latencyMs
                prefs[WidgetStateDefinition.DEVICE_COUNT] = deviceCount
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
    isVpnActive: Boolean,
): WidgetScore {
    val encScore = encryptionScore(encryptionType)
    val devScore = deviceCountScore(deviceCount)
    val vpnScore = if (isVpnActive) 100 else 40

    val numeric = ((encScore * 50 + devScore * 30 + vpnScore * 20) / 100).coerceIn(0, 100)
    val grade = gradeFor(numeric)

    val issues = mutableListOf<Pair<String, String>>()
    if (encScore <= 20) issues.add("Weak or no encryption" to "encryption")
    if (devScore <= 40) issues.add("Too many devices on network" to "device_count")
    if (!isVpnActive) issues.add("VPN not active" to "vpn")

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

internal fun detectEncryptionType(context: Context): String? {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return null
    val network = cm.activeNetwork ?: return null
    val caps = cm.getNetworkCapabilities(network) ?: return null
    if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

    val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
    @Suppress("DEPRECATION")
    val info: WifiInfo = wm.connectionInfo ?: return null

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return when (info.currentSecurityType) {
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

    @Suppress("DEPRECATION")
    val scanResults = wm.scanResults ?: return null
    @Suppress("DEPRECATION")
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

private suspend fun fetchIpInfo(): WidgetIpResponse {
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    return client.use {
        it.get("http://ip-api.com/json/?fields=query,country,countryCode,isp,as").body()
    }
}

private suspend fun measureLatency(): Long = withContext(Dispatchers.IO) {
    val start = System.currentTimeMillis()
    Socket().use { socket ->
        socket.connect(InetSocketAddress("8.8.8.8", 53), 3000)
    }
    System.currentTimeMillis() - start
}
