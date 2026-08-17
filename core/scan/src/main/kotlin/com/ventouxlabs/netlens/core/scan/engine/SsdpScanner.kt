package com.ventouxlabs.netlens.core.scan.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import com.ventouxlabs.netlens.core.network.DisplayText
import com.ventouxlabs.netlens.core.scan.model.SsdpDevice

interface SsdpScanner {
    fun discover(timeoutMs: Long = 3000): Flow<SsdpDevice>
}

@Singleton
class SsdpScannerImpl @Inject constructor() : SsdpScanner {

    override fun discover(timeoutMs: Long): Flow<SsdpDevice> = flow {
        val responses = sendMSearch(timeoutMs)
        val seen = mutableSetOf<String>()
        for ((ip, locationUrl) in responses) {
            if (!seen.add(ip)) continue
            val device = withTimeoutOrNull(DESCRIPTION_TIMEOUT_MS) {
                fetchDeviceDescription(ip, locationUrl)
            } ?: SsdpDevice(ip = ip)
            emit(device)
        }
    }.flowOn(Dispatchers.IO)

    private fun sendMSearch(timeoutMs: Long): List<Pair<String, String?>> {
        val results = mutableListOf<Pair<String, String?>>()
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs.toInt()

                val message = M_SEARCH_MESSAGE.toByteArray()
                val address = InetAddress.getByName(MULTICAST_ADDRESS)
                val packet = DatagramPacket(message, message.size, address, SSDP_PORT)
                socket.send(packet)

                val buffer = ByteArray(2048)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val response = DatagramPacket(buffer, buffer.size)
                        socket.receive(response)
                        val body = String(response.data, 0, response.length)
                        val ip = response.address.hostAddress ?: continue
                        val location = parseLocation(body)
                        results.add(ip to location)
                    } catch (_: java.net.SocketTimeoutException) {
                        break
                    }
                }
            }
        } catch (_: Exception) {
            // SSDP discovery is best-effort
        }
        return results
    }

    private fun fetchDeviceDescription(ip: String, locationUrl: String?): SsdpDevice? {
        if (locationUrl == null) return SsdpDevice(ip = ip)
        if (!isSafeLocationUrl(locationUrl, ip)) return SsdpDevice(ip = ip)
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(locationUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = DESCRIPTION_TIMEOUT_MS.toInt()
                readTimeout = DESCRIPTION_TIMEOUT_MS.toInt()
                // Without this the host check above is bypassable in one hop:
                // HttpURLConnection follows redirects by DEFAULT, so a hostile responder passes
                // isSafeLocationUrl with its own IP and then answers 302 -> anywhere, and the
                // redirect target is fetched with no re-validation. Matches the convention the
                // other two HTTP paths already follow (HttpRequesterImpl, EndpointCheckerImpl).
                instanceFollowRedirects = false
            }
            val xml = connection.inputStream.bufferedReader().use { reader ->
                readCapped(reader, MAX_DESCRIPTION_BYTES)
            }
            parseDeviceXml(ip, xml)
        } catch (_: Exception) {
            SsdpDevice(ip = ip)
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val MULTICAST_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val DESCRIPTION_TIMEOUT_MS = 2000L

        // A hostile LAN device could stream an unbounded description body; cap the read
        // so it cannot OOM the app. Legit UPnP descriptions are a few KB.
        private const val MAX_DESCRIPTION_BYTES = 256 * 1024

        /** Reads up to [cap] characters from [reader], discarding anything beyond the cap. */
        internal fun readCapped(reader: BufferedReader, cap: Int): String {
            val buffer = CharArray(8192)
            val result = StringBuilder()
            while (result.length < cap) {
                val maxToRead = minOf(buffer.size, cap - result.length)
                val read = reader.read(buffer, 0, maxToRead)
                if (read == -1) break
                result.append(buffer, 0, read)
            }
            return result.toString()
        }

        /**
         * A LOCATION URL is fetchable only if it points at **the device that answered**.
         *
         * The previous version resolved the host and rejected loopback/link-local. That left two
         * holes, both exploitable by any device on the LAN:
         *
         *  1. **DNS rebinding.** It resolved the host to check it, then [URL.openConnection]
         *     resolved *again* to connect. A responder controlling DNS answers benign on the
         *     check and loopback on the fetch. Multiple A records did it without any trickery —
         *     `getByName` returns the first, the connection may use another.
         *  2. **Cross-host SSRF inside the LAN.** Nothing tied the URL to the responder, so a
         *     hostile device could set `LOCATION: http://192.168.1.1/admin` and have the app
         *     fetch the router on its behalf. Neither loopback nor link-local, so it passed.
         *
         * Requiring an IP literal equal to the responder's address closes (1) outright — there
         * is no second resolution because there is no resolution at all — and makes this a
         * **pure function**, which is why it finally has tests; the old one did DNS I/O.
         *
         * **(2) is reduced, not eliminated.** The "responder" is the source address of an
         * unauthenticated UDP datagram, and source addresses are trivially spoofable on the same
         * L2 segment. An on-segment attacker can emit a datagram claiming to be 192.168.1.1 with
         * a matching LOCATION and still steer the fetch. What that costs them: they must be on
         * the segment, and they never see the response body — it only reaches this app's UI.
         * Fixing that is not possible at this layer; UDP has no sender authentication.
         *
         * Cost: a device advertising LOCATION by hostname loses its description and is reported
         * with its IP only. UPnP devices advertise their own address, so this is rare; a
         * degraded row is the right trade against fetching an attacker-chosen host.
         */
        internal fun isSafeLocationUrl(url: String, responderIp: String): Boolean {
            val lower = url.lowercase()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
            val host = try {
                URL(url).host
            } catch (_: Exception) {
                return false
            }
            if (host.isNullOrEmpty()) return false

            // An IP literal, never a hostname. Rejecting hostnames outright is what removes the
            // second resolution: there is nothing left to rebind.
            val target = parseIpLiteral(host) ?: return false

            // Unconditional, and NOT redundant with the responder match below. Source addresses
            // on unauthenticated UDP are forgeable, so "it matches the sender" does not mean the
            // destination is safe: a forged reply claiming ::1 or fe80:: with a matching LOCATION
            // would otherwise be fetched. An earlier revision of this function dropped these
            // checks in favour of the match alone, which reintroduced local SSRF — including from
            // another app on the same device answering from 127.0.0.1.
            if (target.isLoopbackAddress) return false
            if (target.isLinkLocalAddress) return false
            if (target.isAnyLocalAddress) return false
            if (target.isMulticastAddress) return false

            return normalizeIp(host) == normalizeIp(responderIp)
        }

        /** Strips IPv6 brackets and any zone id, so `[fe80::1%wlan0]` and `fe80::1` compare equal. */
        private fun normalizeIp(value: String): String =
            value.removeSurrounding("[", "]").substringBefore('%').lowercase()

        private val IPV4_LITERAL = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
        private val IPV6_LITERAL = Regex("""^[0-9a-f:]*:[0-9a-f:.]*$""")

        /**
         * Parses [value] only if it is an IP literal, returning null for anything else.
         *
         * The literal check runs BEFORE `getByName` on purpose: `getByName` performs a DNS lookup
         * for a hostname, and a blocking network call inside a security predicate is exactly what
         * this function was rewritten to remove. For a literal it only parses.
         */
        private fun parseIpLiteral(value: String): InetAddress? {
            val normalized = normalizeIp(value)
            if (!IPV4_LITERAL.matches(normalized) && !IPV6_LITERAL.matches(normalized)) return null
            return try {
                InetAddress.getByName(normalized)
            } catch (_: Exception) {
                null
            }
        }

        private val M_SEARCH_MESSAGE = buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: 239.255.255.250:1900\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 2\r\n")
            append("ST: ssdp:all\r\n")
            append("\r\n")
        }

        internal fun parseLocation(response: String): String? {
            for (line in response.lines()) {
                if (line.startsWith("LOCATION:", ignoreCase = true)) {
                    return line.substringAfter(":").trim()
                }
            }
            return null
        }

        internal fun parseDeviceXml(ip: String, xml: String): SsdpDevice {
            fun extractTag(tag: String): String? {
                val open = "<$tag>"
                val close = "</$tag>"
                val start = xml.indexOf(open)
                if (start < 0) return null
                // Search for the closing tag AFTER the opening one. Searching the whole string
                // found a `</tag>` that precedes `<tag>`, then called substring(start, end) with
                // start > end — a StringIndexOutOfBoundsException on input a hostile LAN device
                // fully controls. It was masked only by the catch-all in fetchDeviceDescription.
                val end = xml.indexOf(close, start + open.length)
                if (end < 0) return null
                // Flattened at ingestion: every tag here is device-controlled XML, and a newline
                // in a friendlyName otherwise reaches Compose, the notification and Room as-is.
                // DisplayText.flatten also trims and nulls-on-empty, replacing the old
                // `.trim().ifEmpty { null }`.
                return DisplayText.flatten(xml.substring(start + open.length, end))
            }
            return SsdpDevice(
                ip = ip,
                friendlyName = extractTag("friendlyName"),
                manufacturer = extractTag("manufacturer"),
                modelName = extractTag("modelName"),
                deviceType = extractTag("deviceType"),
            )
        }
    }
}
