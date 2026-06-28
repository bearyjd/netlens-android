package com.ventouxlabs.netlens.feature.whois.engine

import com.ventouxlabs.netlens.feature.whois.model.WhoisResult

interface WhoisClient {
    suspend fun query(domain: String): WhoisResult
}
