package us.beary.netlens.feature.ipinfo.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import us.beary.netlens.feature.ipinfo.di.IpInfoHttpClient
import us.beary.netlens.feature.ipinfo.model.IpApiResponse
import javax.inject.Inject

class IpInfoRepositoryImpl @Inject constructor(
    @IpInfoHttpClient private val client: HttpClient,
) : IpInfoRepository {

    override suspend fun fetchIpInfo(): Result<IpApiResponse> = try {
        val response = withContext(Dispatchers.IO) {
            client.get(IP_API_URL).body<IpApiResponse>()
        }
        Result.success(response)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private companion object {
        const val IP_API_URL =
            "http://ip-api.com/json/?fields=query,isp,org,as,country,countryCode,regionName,city,lat,lon,proxy,hosting"
    }
}
