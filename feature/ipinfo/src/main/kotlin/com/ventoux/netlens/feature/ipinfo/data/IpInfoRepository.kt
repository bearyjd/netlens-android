package com.ventoux.netlens.feature.ipinfo.data

import com.ventoux.netlens.feature.ipinfo.model.IpApiResponse

interface IpInfoRepository {
    suspend fun fetchIpInfo(): Result<IpApiResponse>
}
