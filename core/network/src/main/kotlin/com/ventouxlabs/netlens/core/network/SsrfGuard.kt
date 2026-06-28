package com.ventouxlabs.netlens.core.network

import java.net.InetAddress

object SsrfGuard {

    fun isPrivateOrLoopback(host: String): Boolean {
        if (host.equals("localhost", ignoreCase = true)) return true

        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (_: Exception) {
            return true
        }

        return addresses.any { addr ->
            addr.isLoopbackAddress ||
                addr.isLinkLocalAddress ||
                addr.isSiteLocalAddress ||
                isUniqueLocal(addr)
        }
    }

    private fun isUniqueLocal(addr: InetAddress): Boolean {
        val bytes = addr.address
        if (bytes.size == 16) {
            val first = bytes[0].toInt() and 0xFF
            if (first == 0xFC || first == 0xFD) return true
        }
        return false
    }
}
