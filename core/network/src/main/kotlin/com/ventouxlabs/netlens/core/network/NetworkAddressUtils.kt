package com.ventouxlabs.netlens.core.network

fun calculateNetworkAddress(ip: String, prefixLength: Int): String {
    val parts = ip.split(".")
    if (parts.size != 4) return ip
    val ipInt = parts.fold(0L) { acc, part ->
        (acc shl 8) or (part.toIntOrNull()?.toLong() ?: return ip)
    }
    val mask = if (prefixLength == 0) 0L else (0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL
    val network = ipInt and mask
    return "${(network shr 24) and 0xFF}.${(network shr 16) and 0xFF}.${(network shr 8) and 0xFF}.${network and 0xFF}"
}
