package com.ventoux.netlens.feature.whois.engine

import com.ventoux.netlens.feature.whois.model.WhoisResult

interface WhoisClient {
    suspend fun query(domain: String): WhoisResult
}
