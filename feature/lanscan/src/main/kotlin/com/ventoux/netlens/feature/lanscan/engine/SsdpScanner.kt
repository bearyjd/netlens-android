package com.ventoux.netlens.feature.lanscan.engine

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

data class SsdpDevice(
    val ip: String,
    val friendlyName: String? = null,
    val manufacturer: String? = null,
    val modelName: String? = null,
    val deviceType: String? = null,
)

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
                socket.broadcast = true

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
        return try {
            val connection = URL(locationUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = DESCRIPTION_TIMEOUT_MS.toInt()
            connection.readTimeout = DESCRIPTION_TIMEOUT_MS.toInt()
            val xml = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            connection.disconnect()
            parseDeviceXml(ip, xml)
        } catch (_: Exception) {
            SsdpDevice(ip = ip)
        }
    }

    companion object {
        private const val MULTICAST_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val DESCRIPTION_TIMEOUT_MS = 2000L

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
                val start = xml.indexOf("<$tag>")
                val end = xml.indexOf("</$tag>")
                if (start < 0 || end < 0) return null
                return xml.substring(start + tag.length + 2, end).trim().ifEmpty { null }
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
