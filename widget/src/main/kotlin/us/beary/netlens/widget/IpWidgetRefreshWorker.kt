package us.beary.netlens.widget

import android.content.Context
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

            val dataStore = IpWidgetStateDefinition.getDataStore(appContext, "ip_widget")
            dataStore.edit { prefs ->
                prefs[IpWidgetStateDefinition.IP_KEY] = response.query
                prefs[IpWidgetStateDefinition.ISP_KEY] = response.isp
                prefs[IpWidgetStateDefinition.IS_VPN_KEY] = response.proxy
            }

            NetLensWidget().updateAll(appContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            client.close()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val IP_API_URL =
            "http://ip-api.com/json/?fields=query,isp,org,as,country,regionName,city,lat,lon,proxy,hosting"
    }
}
