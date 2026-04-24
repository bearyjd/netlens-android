package us.beary.netlens.feature.whois.engine

interface DomainResolver {
    suspend fun resolve(domain: String): String?
}
