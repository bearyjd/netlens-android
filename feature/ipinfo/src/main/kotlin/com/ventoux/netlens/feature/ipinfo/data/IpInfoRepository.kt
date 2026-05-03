package com.ventoux.netlens.feature.ipinfo.data

import com.ventoux.netlens.feature.ipinfo.model.IpInfoResponse

interface IpInfoRepository {
    suspend fun fetchIpInfo(): Result<IpInfoResponse>
}
