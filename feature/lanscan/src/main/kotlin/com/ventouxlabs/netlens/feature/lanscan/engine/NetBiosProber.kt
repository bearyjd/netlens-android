package com.ventouxlabs.netlens.feature.lanscan.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton
import com.ventouxlabs.netlens.feature.lanscan.model.NetBiosInfo

interface NetBiosProber {
    suspend fun probe(ip: String): NetBiosInfo?
}

@Singleton
class NetBiosProberImpl @Inject constructor() : NetBiosProber {

    override suspend fun probe(ip: String): NetBiosInfo? = withContext(Dispatchers.IO) {
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = TIMEOUT_MS
                val query = buildNameQuery()
                val address = InetAddress.getByName(ip)
                val packet = DatagramPacket(query, query.size, address, NETBIOS_PORT)
                socket.send(packet)

                val buffer = ByteArray(1024)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                parseResponse(buffer, response.length)
            }
        } catch (_: java.net.SocketTimeoutException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val NETBIOS_PORT = 137
        private const val TIMEOUT_MS = 1000

        internal fun buildNameQuery(): ByteArray {
            val packet = ByteArray(50)
            // Transaction ID
            packet[0] = 0x00
            packet[1] = 0x01
            // Flags: standard query
            packet[2] = 0x00
            packet[3] = 0x00
            // Questions: 1
            packet[4] = 0x00
            packet[5] = 0x01
            // Answers, Authority, Additional: 0
            // Name: * (wildcard) encoded as NetBIOS name
            packet[12] = 0x20 // Name length
            // Encode '*' padded with spaces to 16 bytes, then first-level encoded
            val name = "*".padEnd(16, ' ')
            var offset = 13
            for (ch in name) {
                val b = ch.code
                packet[offset++] = ('A' + (b shr 4)).code.toByte()
                packet[offset++] = ('A' + (b and 0x0F)).code.toByte()
            }
            packet[offset++] = 0x00 // Name terminator
            // Type: NBSTAT (0x0021)
            packet[offset++] = 0x00
            packet[offset++] = 0x21
            // Class: IN (0x0001)
            packet[offset++] = 0x00
            packet[offset] = 0x01
            return packet
        }

        internal fun parseResponse(data: ByteArray, length: Int): NetBiosInfo? {
            if (length < 57) return null

            // Skip header (12 bytes) + question section
            var offset = 12
            // Skip name in response
            while (offset < length && data[offset].toInt() != 0) offset++
            offset++ // null terminator
            offset += 4 // type + class
            // Skip TTL (4 bytes) + data length (2 bytes)
            offset += 6
            if (offset >= length) return null

            val nameCount = data[offset].toInt() and 0xFF
            offset++

            var computerName: String? = null
            var workgroup: String? = null

            for (i in 0 until nameCount) {
                if (offset + 18 > length) break
                val nameBytes = data.copyOfRange(offset, offset + 15)
                val nameType = data[offset + 15].toInt() and 0xFF
                val flags = ((data[offset + 16].toInt() and 0xFF) shl 8) or
                    (data[offset + 17].toInt() and 0xFF)
                val isGroup = (flags and 0x8000) != 0
                val name = String(nameBytes).trim()

                if (nameType == 0x00 && !isGroup && computerName == null) {
                    computerName = name
                } else if (nameType == 0x00 && isGroup && workgroup == null) {
                    workgroup = name
                }
                offset += 18
            }

            return computerName?.let { NetBiosInfo(name = it, workgroup = workgroup) }
        }
    }
}
