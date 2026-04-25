package us.beary.netlens.feature.whois.engine

class FakeDomainResolver : DomainResolver {
    var ip: String? = null

    override suspend fun resolve(domain: String): String? = ip
}
