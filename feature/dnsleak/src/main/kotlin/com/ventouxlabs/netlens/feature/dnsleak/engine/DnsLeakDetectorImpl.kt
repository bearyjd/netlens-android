package com.ventouxlabs.netlens.feature.dnsleak.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import com.ventouxlabs.netlens.feature.dnsleak.model.DnsLeakResult
import com.ventouxlabs.netlens.feature.dnsleak.model.ResolverInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xbill.DNS.DClass
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Record
import org.xbill.DNS.SimpleResolver
import java.net.InetAddress
import java.util.UUID
import javax.inject.Inject

class DnsLeakDetectorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : DnsLeakDetector {

    override suspend fun detect(vpnActive: Boolean): DnsLeakResult = withContext(Dispatchers.IO) {
        try {
            val systemServers = getSystemDnsServers()
            val classifiedServers = systemServers.map { ip -> classifyResolver(ip) }

            val probeResults = probeSystemResolver()

            val allResolvers = (classifiedServers + probeResults)
                .distinctBy { it.ip }

            if (vpnActive) {
                val leaked = allResolvers.filter { !it.isKnownPublic }
                if (leaked.isNotEmpty()) {
                    val expected = allResolvers.filter { it.isKnownPublic }
                    DnsLeakResult.LeakDetected(
                        leakedResolvers = leaked,
                        expectedResolvers = expected,
                    )
                } else {
                    DnsLeakResult.NoLeak(resolvers = allResolvers)
                }
            } else {
                DnsLeakResult.NoLeak(resolvers = allResolvers)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DnsLeakResult.Error(e.message ?: "DNS leak detection failed")
        }
    }

    private fun getSystemDnsServers(): List<String> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork ?: return emptyList()
        val linkProps: LinkProperties = cm.getLinkProperties(activeNetwork) ?: return emptyList()
        return linkProps.dnsServers.mapNotNull { it.hostAddress }
    }

    private fun probeSystemResolver(): List<ResolverInfo> {
        return try {
            val probeDomain = "${UUID.randomUUID()}.example.com"
            val name = Name.fromString("$probeDomain.")
            val query = Message.newQuery(Record.newRecord(name, org.xbill.DNS.Type.A, DClass.IN))

            val resolver = SimpleResolver()
            resolver.setTimeout(java.time.Duration.ofSeconds(TIMEOUT_SECONDS))

            val response = resolver.send(query)
            val resolverIp = resolver.address.address.hostAddress ?: return emptyList()

            listOf(classifyResolver(resolverIp))
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val TIMEOUT_SECONDS = 5L

        private val KNOWN_PUBLIC_DNS = mapOf(
            "8.8.8.8" to "Google DNS",
            "8.8.4.4" to "Google DNS",
            "1.1.1.1" to "Cloudflare DNS",
            "1.0.0.1" to "Cloudflare DNS",
            "9.9.9.9" to "Quad9 DNS",
            "149.112.112.112" to "Quad9 DNS",
            "208.67.222.222" to "OpenDNS",
            "208.67.220.220" to "OpenDNS",
            "76.76.2.0" to "Control D",
            "76.76.10.0" to "Control D",
            "94.140.14.14" to "AdGuard DNS",
            "94.140.15.15" to "AdGuard DNS",
            "185.228.168.9" to "CleanBrowsing",
            "185.228.169.9" to "CleanBrowsing",
            "76.76.19.19" to "Alternate DNS",
            "76.223.122.150" to "Alternate DNS",
            "2001:4860:4860::8888" to "Google DNS",
            "2001:4860:4860::8844" to "Google DNS",
            "2606:4700:4700::1111" to "Cloudflare DNS",
            "2606:4700:4700::1001" to "Cloudflare DNS",
            "2620:fe::fe" to "Quad9 DNS",
            "2620:fe::9" to "Quad9 DNS",
        )

        private val KNOWN_PRIVATE_RANGES = listOf(
            "10.",
            "172.16.", "172.17.", "172.18.", "172.19.",
            "172.20.", "172.21.", "172.22.", "172.23.",
            "172.24.", "172.25.", "172.26.", "172.27.",
            "172.28.", "172.29.", "172.30.", "172.31.",
            "192.168.",
        )

        internal fun classifyResolver(ip: String): ResolverInfo {
            val knownName = KNOWN_PUBLIC_DNS[ip]
            if (knownName != null) {
                return ResolverInfo(ip = ip, name = knownName, isKnownPublic = true)
            }

            val isPrivate = KNOWN_PRIVATE_RANGES.any { ip.startsWith(it) } ||
                ip == "127.0.0.1" || ip == "::1"

            val name = when {
                isPrivate -> "Private/Local DNS"
                else -> resolveHostname(ip)
            }

            return ResolverInfo(ip = ip, name = name, isKnownPublic = false)
        }

        private fun resolveHostname(ip: String): String {
            return try {
                val addr = InetAddress.getByName(ip)
                val hostname = addr.canonicalHostName
                if (hostname != ip) hostname else "ISP DNS"
            } catch (_: Exception) {
                "ISP DNS"
            }
        }
    }
}
