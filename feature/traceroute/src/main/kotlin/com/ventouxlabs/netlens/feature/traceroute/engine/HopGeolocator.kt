package com.ventouxlabs.netlens.feature.traceroute.engine

import com.ventouxlabs.netlens.feature.traceroute.model.HopLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import javax.inject.Inject

interface HopGeolocator {
    suspend fun lookupAll(ips: List<String?>): Map<String, HopLocation>
}

class HopGeolocatorImpl @Inject constructor() : HopGeolocator {

    private val json = Json { ignoreUnknownKeys = true }
    private val semaphore = Semaphore(MAX_CONCURRENT)

    override suspend fun lookupAll(ips: List<String?>): Map<String, HopLocation> {
        val uniqueIps = ips.filterNotNull()
            .filter { it.isNotBlank() && isValidPublicIp(it) }
            .distinct()

        if (uniqueIps.isEmpty()) return emptyMap()

        return coroutineScope {
            uniqueIps.map { ip ->
                async {
                    semaphore.withPermit { ip to lookupSingle(ip) }
                }
            }.awaitAll()
                .filter { it.second != null }
                .associate { it.first to it.second!! }
        }
    }

    private suspend fun lookupSingle(ip: String): HopLocation? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/$ip")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/json")

            try {
                if (conn.responseCode != 200) return@withContext null

                val body = conn.inputStream.bufferedReader().readText()
                val obj = json.parseToJsonElement(body).jsonObject

                val success = obj["success"]?.jsonPrimitive?.boolean ?: false
                if (!success) return@withContext null

                HopLocation(
                    city = obj["city"]?.jsonPrimitive?.content ?: "",
                    country = obj["country"]?.jsonPrimitive?.content ?: "",
                    countryCode = obj["country_code"]?.jsonPrimitive?.content ?: "",
                    latitude = obj["latitude"]?.jsonPrimitive?.double ?: 0.0,
                    longitude = obj["longitude"]?.jsonPrimitive?.double ?: 0.0,
                    org = obj["connection"]?.jsonObject?.get("org")?.jsonPrimitive?.content ?: "",
                )
            } finally {
                conn.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun isValidPublicIp(ip: String): Boolean {
        val addr = try {
            InetAddress.getByName(ip)
        } catch (_: Exception) {
            return false
        }
        return !addr.isSiteLocalAddress &&
            !addr.isLoopbackAddress &&
            !addr.isLinkLocalAddress &&
            !addr.isAnyLocalAddress
    }

    private companion object {
        const val BASE_URL = "https://ipwho.is"
        const val TIMEOUT_MS = 5_000
        const val MAX_CONCURRENT = 5
    }
}
