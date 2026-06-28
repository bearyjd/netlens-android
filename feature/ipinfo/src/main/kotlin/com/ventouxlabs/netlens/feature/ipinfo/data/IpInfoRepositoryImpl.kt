package com.ventouxlabs.netlens.feature.ipinfo.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ventouxlabs.netlens.feature.ipinfo.di.IpInfoHttpClient
import com.ventouxlabs.netlens.feature.ipinfo.model.IpInfoResponse
import javax.inject.Inject

class IpInfoRepositoryImpl @Inject constructor(
    @IpInfoHttpClient private val client: HttpClient,
) : IpInfoRepository {

    @Volatile
    private var cachedResponse: IpInfoResponse? = null
    @Volatile
    private var cacheTimestamp: Long = 0L

    override suspend fun fetchIpInfo(): Result<IpInfoResponse> {
        val now = System.currentTimeMillis()
        val cached = cachedResponse
        if (cached != null && (now - cacheTimestamp) < CACHE_TTL_MS) {
            return Result.success(cached)
        }
        return try {
            val response = withContext(Dispatchers.IO) {
                client.get(IPINFO_URL).body<IpInfoResponse>()
            }
            cachedResponse = response
            cacheTimestamp = System.currentTimeMillis()
            Result.success(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val IPINFO_URL = "https://ipinfo.io/json"
        const val CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour
    }
}
