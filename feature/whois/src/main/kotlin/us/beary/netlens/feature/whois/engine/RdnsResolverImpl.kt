package us.beary.netlens.feature.whois.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import us.beary.netlens.feature.whois.model.RdnsResult
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import javax.inject.Inject

class RdnsResolverImpl @Inject constructor() : RdnsResolver {

    override suspend fun resolve(ip: String): RdnsResult = withContext(Dispatchers.IO) {
        require(isValidIpLiteral(ip)) { "Input must be a valid IP address, not a hostname: $ip" }
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
        private val IPV4_PATTERN = Regex(
            "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)$",
        )

        fun isValidIpLiteral(ip: String): Boolean {
            if (IPV4_PATTERN.matches(ip)) return true
            if (ip.count { it == ':' } < 2) return false
            return try {
                val addr = InetAddress.getByName(ip)
                addr is Inet4Address || addr is Inet6Address
            } catch (_: Exception) {
                false
            }
        }
    }
}
