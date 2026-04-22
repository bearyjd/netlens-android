package us.beary.netlens.feature.wol.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import javax.inject.Inject

class WolSenderImpl @Inject constructor() : WolSender {

    override suspend fun sendMagicPacket(
        macAddress: String,
        broadcastIp: String,
        port: Int,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(port in 1..MAX_PORT) { "Port must be in range 1-65535" }
            require(IPV4_PATTERN.matches(broadcastIp)) { "Invalid broadcast IP address" }
            val macBytes = parseMac(macAddress)
            val packet = buildMagicPacket(macBytes)
            sendPacket(packet, broadcastIp, port)
        }
    }

    private fun parseMac(mac: String): ByteArray {
        val hex = mac.split(':', '-')
        require(hex.size == MAC_BYTE_COUNT) {
            "Invalid MAC address: expected 6 octets, got ${hex.size}"
        }
        return hex.map { it.toInt(HEX_RADIX).toByte() }.toByteArray()
    }

    private fun buildMagicPacket(macBytes: ByteArray): ByteArray {
        val packet = ByteArray(MAGIC_PACKET_SIZE)

        // First 6 bytes: 0xFF
        for (i in 0 until HEADER_SIZE) {
            packet[i] = 0xFF.toByte()
        }

        // Repeat MAC address 16 times
        for (i in 0 until MAC_REPEAT_COUNT) {
            val offset = HEADER_SIZE + i * MAC_BYTE_COUNT
            macBytes.copyInto(packet, offset)
        }

        return packet
    }

    private fun sendPacket(packet: ByteArray, broadcastIp: String, port: Int) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.broadcast = true
            val address = InetAddress.getByName(broadcastIp)
            val datagram = DatagramPacket(packet, packet.size, address, port)
            socket.send(datagram)
        } finally {
            socket?.close()
        }
    }

    private companion object {
        const val MAGIC_PACKET_SIZE = 102
        const val HEADER_SIZE = 6
        const val MAC_BYTE_COUNT = 6
        const val MAC_REPEAT_COUNT = 16
        const val HEX_RADIX = 16
        const val MAX_PORT = 65535
        val IPV4_PATTERN = Regex("^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)$")
    }
}
