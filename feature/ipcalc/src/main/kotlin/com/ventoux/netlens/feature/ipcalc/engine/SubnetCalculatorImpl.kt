package com.ventoux.netlens.feature.ipcalc.engine

import com.ventoux.netlens.feature.ipcalc.model.SubnetInfo
import javax.inject.Inject

class SubnetCalculatorImpl @Inject constructor() : SubnetCalculator {

    override fun calculate(input: String): SubnetInfo {
        val trimmed = input.trim()
        require(trimmed.isNotEmpty()) { "Input cannot be empty" }
        require(!trimmed.contains(':')) { "IPv6 is not supported — enter an IPv4 address or CIDR" }

        val (ip, prefixLen) = parseInput(trimmed)
        val ipInt = ipToInt(ip)

        val mask = if (prefixLen == 0) 0L else (0xFFFFFFFFL shl (32 - prefixLen)) and 0xFFFFFFFFL
        val wildcard = mask xor 0xFFFFFFFFL
        val network = ipInt and mask
        val broadcast = network or wildcard

        val totalHosts = when {
            prefixLen == 32 -> 1L
            prefixLen == 31 -> 2L
            else -> (1L shl (32 - prefixLen)) - 2
        }

        val firstHost = when {
            prefixLen >= 31 -> intToIp(network)
            else -> intToIp(network + 1)
        }

        val lastHost = when {
            prefixLen == 32 -> intToIp(network)
            prefixLen == 31 -> intToIp(broadcast)
            else -> intToIp(broadcast - 1)
        }

        return SubnetInfo(
            networkAddress = intToIp(network),
            broadcastAddress = intToIp(broadcast),
            firstHost = firstHost,
            lastHost = lastHost,
            totalHosts = totalHosts,
            subnetMask = intToIp(mask),
            wildcardMask = intToIp(wildcard),
            cidrNotation = "${intToIp(network)}/$prefixLen",
            ipClass = classifyIp(network),
            isBogon = isBogon(network, prefixLen),
        )
    }

    private fun parseInput(input: String): Pair<String, Int> {
        if ('/' in input) {
            val parts = input.split('/')
            require(parts.size == 2) { "Invalid CIDR notation" }
            val prefix = parts[1].toIntOrNull()
            require(prefix != null && prefix in 0..32) { "CIDR prefix must be 0–32" }
            validateIp(parts[0])
            return parts[0] to prefix
        }

        if (' ' in input) {
            val parts = input.split(' ').filter { it.isNotEmpty() }
            require(parts.size == 2) { "Expected 'IP MASK' format (e.g. 192.168.1.0 255.255.255.0)" }
            validateIp(parts[0])
            validateIp(parts[1])
            val prefix = maskToPrefix(parts[1])
            return parts[0] to prefix
        }

        validateIp(input)
        return input to 32
    }

    private fun validateIp(ip: String) {
        val octets = ip.split('.')
        require(octets.size == 4) { "Invalid IP address: expected 4 octets" }
        octets.forEach { octet ->
            val value = octet.toIntOrNull()
            require(value != null && value in 0..255) { "Invalid octet: $octet" }
        }
    }

    private fun ipToInt(ip: String): Long {
        val octets = ip.split('.')
        return octets.fold(0L) { acc, octet -> (acc shl 8) or octet.toLong() }
    }

    private fun intToIp(value: Long): String {
        val masked = value and 0xFFFFFFFFL
        return "${(masked shr 24) and 0xFF}.${(masked shr 16) and 0xFF}.${(masked shr 8) and 0xFF}.${masked and 0xFF}"
    }

    private fun maskToPrefix(mask: String): Int {
        val maskInt = ipToInt(mask)
        var bits = maskInt
        var count = 0
        var seenZero = false
        for (i in 31 downTo 0) {
            val bit = (bits shr i) and 1L
            if (bit == 1L) {
                require(!seenZero) { "Invalid subnet mask: $mask (non-contiguous bits)" }
                count++
            } else {
                seenZero = true
            }
        }
        return count
    }

    private fun classifyIp(networkInt: Long): String {
        val firstOctet = (networkInt shr 24) and 0xFF
        return when {
            firstOctet in 0..127 -> "A"
            firstOctet in 128..191 -> "B"
            firstOctet in 192..223 -> "C"
            firstOctet in 224..239 -> "D (Multicast)"
            else -> "E (Reserved)"
        }
    }

    private fun isBogon(networkInt: Long, prefixLen: Int): Boolean {
        fun matches(net: String, prefix: Int): Boolean {
            val netInt = ipToInt(net)
            val effectivePrefix = minOf(prefix, prefixLen)
            if (effectivePrefix == 0) return true
            val mask = (0xFFFFFFFFL shl (32 - effectivePrefix)) and 0xFFFFFFFFL
            return (networkInt and mask) == (netInt and mask) && prefixLen >= prefix
        }

        return matches("0.0.0.0", 8) ||
            matches("10.0.0.0", 8) ||
            matches("100.64.0.0", 10) ||
            matches("127.0.0.0", 8) ||
            matches("169.254.0.0", 16) ||
            matches("172.16.0.0", 12) ||
            matches("192.0.0.0", 24) ||
            matches("192.0.2.0", 24) ||
            matches("192.168.0.0", 16) ||
            matches("198.18.0.0", 15) ||
            matches("198.51.100.0", 24) ||
            matches("203.0.113.0", 24) ||
            matches("224.0.0.0", 4) ||
            matches("240.0.0.0", 4)
    }
}
