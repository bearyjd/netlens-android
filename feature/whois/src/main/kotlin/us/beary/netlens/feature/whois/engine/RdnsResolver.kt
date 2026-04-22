package us.beary.netlens.feature.whois.engine

import us.beary.netlens.feature.whois.model.RdnsResult

interface RdnsResolver {
    suspend fun resolve(ip: String): RdnsResult
}
