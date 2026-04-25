package us.beary.netlens.feature.whois.engine

import us.beary.netlens.feature.whois.model.WhoisResult

class FakeWhoisClient : WhoisClient {
    var result: WhoisResult? = null
    var error: Throwable? = null

    override suspend fun query(domain: String): WhoisResult {
        error?.let { throw it }
        return result ?: throw IllegalStateException("No result configured")
    }
}
