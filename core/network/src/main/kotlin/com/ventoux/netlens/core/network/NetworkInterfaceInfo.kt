package com.ventoux.netlens.core.network

data class NetworkInterfaceInfo(
    val ip: String,
    val prefixLength: Int,
    val interfaceName: String,
    val label: String,
    val isVpn: Boolean,
    val gateway: String? = null,
)
