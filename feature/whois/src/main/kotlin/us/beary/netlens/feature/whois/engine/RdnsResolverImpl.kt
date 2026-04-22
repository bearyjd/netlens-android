package us.beary.netlens.feature.whois.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import us.beary.netlens.feature.whois.model.RdnsResult
import java.net.InetAddress
import javax.inject.Inject

class RdnsResolverImpl @Inject constructor() : RdnsResolver {

    override suspend fun resolve(ip: String): RdnsResult = withContext(Dispatchers.IO) {
        val address = InetAddress.getByName(ip)
        val hostName = address.canonicalHostName
        val hostnames = if (hostName == address.hostAddress) {
            emptyList()
        } else {
            listOf(hostName)
        }
        RdnsResult(ip = ip, hostnames = hostnames)
    }
}
