package us.beary.netlens.feature.ipinfo.data

import us.beary.netlens.feature.ipinfo.model.IpApiResponse

class FakeIpInfoRepository : IpInfoRepository {
    var result: Result<IpApiResponse> = Result.failure(IllegalStateException("No result configured"))

    override suspend fun fetchIpInfo(): Result<IpApiResponse> = result
}
