package us.beary.netlens.feature.whois.engine

import us.beary.netlens.feature.whois.model.WhoisResult

interface WhoisClient {
    suspend fun query(domain: String): WhoisResult
}
