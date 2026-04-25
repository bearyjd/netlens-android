package us.beary.netlens.feature.ipinfo.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import us.beary.netlens.feature.ipinfo.di.IpInfoHttpClient
import us.beary.netlens.feature.ipinfo.model.IpApiResponse
import javax.inject.Inject

class IpInfoRepositoryImpl @Inject constructor(
    @IpInfoHttpClient private val client: HttpClient,
) : IpInfoRepository {

    override suspend fun fetchIpInfo(): Result<IpApiResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                client
                    .get(IP_API_URL)
                    .body<IpApiResponse>()
            }
        }

    private companion object {
        const val IP_API_URL =
            "https://ip-api.com/json/?fields=query,isp,org,as,country,regionName,city,lat,lon,proxy,hosting"
    }
}
