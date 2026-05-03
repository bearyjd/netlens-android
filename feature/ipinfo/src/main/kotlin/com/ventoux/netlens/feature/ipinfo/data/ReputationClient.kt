package com.ventoux.netlens.feature.ipinfo.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ventoux.netlens.feature.ipinfo.di.IpInfoHttpClient
import com.ventoux.netlens.feature.ipinfo.model.AbuseIpDbResponse
import com.ventoux.netlens.feature.ipinfo.model.ReputationResult
import javax.inject.Inject

class ReputationClient @Inject constructor(
    @IpInfoHttpClient private val client: HttpClient,
) {

    suspend fun checkReputation(ip: String, apiKey: String): Result<ReputationResult> {
        return try {
            val response = withContext(Dispatchers.IO) {
                client.get(ABUSEIPDB_URL) {
                    header("Key", apiKey)
                    header("Accept", "application/json")
                    parameter("ipAddress", ip)
                    parameter("maxAgeInDays", MAX_AGE_DAYS)
                }.body<AbuseIpDbResponse>()
            }
            val data = response.data
            Result.success(
                ReputationResult(
                    abuseConfidenceScore = data.abuseConfidenceScore,
                    totalReports = data.totalReports,
                    isWhitelisted = data.isWhitelisted ?: false,
                    usageType = data.usageType ?: "",
                    isp = data.isp ?: "",
                    domain = data.domain ?: "",
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val ABUSEIPDB_URL = "https://api.abuseipdb.com/api/v2/check"
        const val MAX_AGE_DAYS = 90
    }
}
