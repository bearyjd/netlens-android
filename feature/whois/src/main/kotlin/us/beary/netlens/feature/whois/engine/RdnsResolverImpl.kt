package us.beary.netlens.feature.whois.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import us.beary.netlens.feature.whois.model.RdnsResult
import java.net.InetAddress
import javax.inject.Inject

class RdnsResolverImpl @Inject constructor() : RdnsResolver {

    override suspend fun resolve(ip: String): RdnsResult = withContext(Dispatchers.IO) {
        require(IP_PATTERN.matches(ip)) { "Input must be a valid IP address, not a hostname: $ip" }
        val address = InetAddress.getByName(ip)
        val hostName = address.canonicalHostName
        val hostnames = if (hostName == address.hostAddress) {
            emptyList()
        } else {
            listOf(hostName)
        }
        RdnsResult(ip = ip, hostnames = hostnames)
    }

    private companion object {
        val IP_PATTERN = Regex(
            "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)$" +
                "|^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}$",
        )
    }
}
