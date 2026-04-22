package us.beary.netlens.feature.ipinfo.data

import us.beary.netlens.feature.ipinfo.model.IpApiResponse

interface IpInfoRepository {
    suspend fun fetchIpInfo(): Result<IpApiResponse>
}
