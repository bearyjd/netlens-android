package com.ventouxlabs.netlens.feature.ipinfo.data

import com.ventouxlabs.netlens.feature.ipinfo.model.IpInfoResponse

interface IpInfoRepository {
    suspend fun fetchIpInfo(): Result<IpInfoResponse>
}
