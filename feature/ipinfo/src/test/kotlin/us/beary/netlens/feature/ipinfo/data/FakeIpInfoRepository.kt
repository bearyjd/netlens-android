package us.beary.netlens.feature.ipinfo.data

import kotlinx.coroutines.CompletableDeferred
import us.beary.netlens.feature.ipinfo.model.IpApiResponse

class FakeIpInfoRepository : IpInfoRepository {
    var result: Result<IpApiResponse> = Result.failure(IllegalStateException("No result configured"))
    private var gate: CompletableDeferred<Unit>? = null

    fun enableSuspend() { gate = CompletableDeferred() }
    fun resume() { gate?.complete(Unit) }

    override suspend fun fetchIpInfo(): Result<IpApiResponse> {
        gate?.await()
        return result
    }
}
