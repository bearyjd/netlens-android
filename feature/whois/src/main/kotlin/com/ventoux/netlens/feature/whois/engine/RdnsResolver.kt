package com.ventoux.netlens.feature.whois.engine

import com.ventoux.netlens.feature.whois.model.RdnsResult

interface RdnsResolver {
    suspend fun resolve(ip: String): RdnsResult
}
