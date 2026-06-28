package com.ventouxlabs.netlens.feature.whois.engine

import com.ventouxlabs.netlens.feature.whois.model.RdnsResult

interface RdnsResolver {
    suspend fun resolve(ip: String): RdnsResult
}
