package com.ventoux.netlens.feature.ipinfo.data

import kotlinx.coroutines.CompletableDeferred
import com.ventoux.netlens.feature.ipinfo.model.IpInfoResponse

class FakeIpInfoRepository : IpInfoRepository {
    var result: Result<IpInfoResponse> = Result.failure(IllegalStateException("No result configured"))
    private var gate: CompletableDeferred<Unit>? = null

    fun enableSuspend() { gate = CompletableDeferred() }
    fun resume() { gate?.complete(Unit) }

    override suspend fun fetchIpInfo(): Result<IpInfoResponse> {
        gate?.await()
        return result
    }
}
