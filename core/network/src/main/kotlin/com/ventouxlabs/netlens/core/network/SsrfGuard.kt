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

        return addresses.any(::isBlockedAddress)
    }

    /**
     * Resolves [host] exactly once and returns the resolved addresses only when
     * *every* record (all A + AAAA) is a public address. Returns `null` when the
     * host cannot be resolved or any record points at a private/loopback/link-local/
     * unique-local address.
     *
     * Callers that need DNS-rebinding (TOCTOU) safety must connect to one of the
     * returned [InetAddress] instances directly rather than re-resolving the
     * hostname, so a low-TTL DNS flip between validation and connect cannot swap in
     * a private address after the check has passed.
     */
    fun resolveIfPublic(host: String): List<InetAddress>? {
        if (host.equals("localhost", ignoreCase = true)) return null

        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (_: Exception) {
            return null
        }

        if (addresses.isEmpty() || addresses.any(::isBlockedAddress)) return null
        return addresses.toList()
    }

    private fun isBlockedAddress(addr: InetAddress): Boolean =
        addr.isLoopbackAddress ||
            addr.isLinkLocalAddress ||
            addr.isSiteLocalAddress ||
            isUniqueLocal(addr)

    private fun isUniqueLocal(addr: InetAddress): Boolean {
        val bytes = addr.address
        if (bytes.size == 16) {
            val first = bytes[0].toInt() and 0xFF
            if (first == 0xFC || first == 0xFD) return true
        }
        return false
    }
}
