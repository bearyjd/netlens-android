package com.ventoux.netlens.feature.whois.engine

import com.ventoux.netlens.feature.whois.model.RdnsResult

class FakeRdnsResolver : RdnsResolver {
    var result: RdnsResult? = null
    var error: Throwable? = null

    override suspend fun resolve(ip: String): RdnsResult {
        error?.let { throw it }
        return result ?: throw IllegalStateException("No result configured")
    }
}
