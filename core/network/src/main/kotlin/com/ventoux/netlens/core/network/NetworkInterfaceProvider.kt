package com.ventoux.netlens.core.network

interface NetworkInterfaceProvider {
    fun getNetworkInterfaces(): List<NetworkInterfaceInfo>
    fun getActiveNetworkInterface(): NetworkInterfaceInfo?
}
