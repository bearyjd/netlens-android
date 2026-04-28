package us.beary.netlens.core.network

interface NetworkInterfaceProvider {
    fun getNetworkInterfaces(): List<NetworkInterfaceInfo>
    fun getActiveNetworkInterface(): NetworkInterfaceInfo?
}
