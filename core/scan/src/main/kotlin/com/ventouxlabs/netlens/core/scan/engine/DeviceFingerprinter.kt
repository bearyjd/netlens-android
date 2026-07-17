package com.ventouxlabs.netlens.core.scan.engine

import com.ventouxlabs.netlens.core.oui.OuiLookup
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.core.scan.model.NetBiosInfo
import com.ventouxlabs.netlens.core.scan.model.SsdpDevice
import javax.inject.Inject
import javax.inject.Singleton

data class PortFingerprint(
    val deviceType: String?,
    val osGuess: String?,
    val evidence: List<String>,
)

interface DeviceFingerprinter {
    suspend fun fingerprint(device: LanDevice): LanDevice
    fun classifyFromServices(services: List<String>): Pair<String?, String?>
    fun classifyFromSsdp(ssdpDevice: SsdpDevice): Pair<String?, String?>
    fun classifyFromNetBios(info: NetBiosInfo): String?
    fun fingerprintWithPorts(device: LanDevice, openPorts: List<Int>): PortFingerprint
}

@Singleton
class DeviceFingerprinterImpl @Inject constructor(
    private val ouiLookup: OuiLookup,
) : DeviceFingerprinter {

    override suspend fun fingerprint(device: LanDevice): LanDevice {
        val hostname = device.hostname?.lowercase() ?: ""
        val serviceType = classifyFromServices(device.services)
        val deviceType = serviceType.first ?: guessDeviceType(hostname)
        val osGuess = serviceType.second ?: guessOs(hostname)
        val vendor = if (device.macAddress != null) {
            ouiLookup.lookup(device.macAddress)
        } else {
            null
        }
        return device.copy(deviceType = deviceType, osGuess = osGuess, vendor = vendor)
    }

    override fun classifyFromServices(services: List<String>): Pair<String?, String?> {
        var type: String? = null
        var os: String? = null
        for (svc in services) {
            val normalized = svc.trim('.').lowercase()
            val mapping = SERVICE_TYPE_MAP[normalized]
            if (mapping != null) {
                if (type == null) type = mapping.first
                if (os == null) os = mapping.second
            }
        }
        return type to os
    }

    override fun classifyFromSsdp(ssdpDevice: SsdpDevice): Pair<String?, String?> {
        val upnpType = ssdpDevice.deviceType?.lowercase() ?: ""
        val manufacturer = ssdpDevice.manufacturer?.lowercase() ?: ""
        val friendlyName = ssdpDevice.friendlyName?.lowercase() ?: ""
        val modelName = ssdpDevice.modelName?.lowercase() ?: ""

        val deviceType = when {
            upnpType.contains("mediarenderer") || upnpType.contains("tv") -> "Smart TV"
            upnpType.contains("mediaserver") -> "Media Server"
            upnpType.contains("printer") -> "Printer"
            upnpType.contains("internetgateway") || upnpType.contains("wandevice") -> "Router"
            friendlyName.contains("chromecast") || friendlyName.contains("google home") -> "Smart Speaker"
            friendlyName.contains("tv") || modelName.contains("tv") -> "Smart TV"
            friendlyName.contains("sonos") || friendlyName.contains("speaker") -> "Smart Speaker"
            else -> null
        }

        val osGuess = when {
            manufacturer.contains("apple") -> "macOS"
            manufacturer.contains("microsoft") -> "Windows"
            manufacturer.contains("google") -> "Android"
            manufacturer.contains("samsung") && deviceType == "Smart TV" -> "Tizen"
            manufacturer.contains("lg") && deviceType == "Smart TV" -> "webOS"
            else -> null
        }

        return deviceType to osGuess
    }

    override fun classifyFromNetBios(info: NetBiosInfo): String? {
        val name = info.name.uppercase()
        return when {
            name.startsWith("DESKTOP-") || name.startsWith("WIN-") -> "Windows"
            info.workgroup?.uppercase() in listOf("WORKGROUP", "MSHOME") -> "Windows"
            else -> null
        }
    }

    override fun fingerprintWithPorts(device: LanDevice, openPorts: List<Int>): PortFingerprint {
        val evidence = mutableListOf<String>()
        var type = device.deviceType
        var os = device.osGuess

        if (631 in openPorts || 9100 in openPorts) {
            if (type == null) type = "Printer"
            evidence.add("port ${if (631 in openPorts) "631 (IPP)" else "9100 (raw print)"}")
        }
        if (3389 in openPorts) {
            if (os == null) os = "Windows"
            evidence.add("port 3389 (RDP)")
        }
        if (548 in openPorts) {
            if (os == null) os = "macOS"
            evidence.add("port 548 (AFP)")
        }
        if (22 in openPorts && 53 in openPorts) {
            if (type == null) type = "Router"
            evidence.add("ports 22+53 (SSH+DNS)")
        }
        if (openPorts.any { it in listOf(80, 443, 8080) } && type == null) {
            type = "Web Server"
            evidence.add("web ports open")
        }

        device.services.forEach { svc ->
            val clean = svc.trim('.').removePrefix("_").removeSuffix("._tcp").removeSuffix("._udp")
            evidence.add("mDNS: $clean")
        }
        device.hostname?.let { evidence.add("hostname: $it") }
        device.vendor?.let { evidence.add("vendor: $it") }

        return PortFingerprint(type, os, evidence)
    }

    private fun guessDeviceType(hostname: String): String? {
        if (hostname.contains("router") || hostname.contains("gateway")) return "Router"
        if (hostname.contains("printer") || hostname.contains("prn")) return "Printer"
        if (hostname.contains("appletv") || hostname.contains("apple-tv")) return "Smart TV"
        if (hostname.contains("tv") || hostname.contains("tizen")) return "Smart TV"
        if (hostname.contains("iphone")) return "Phone"
        if (hostname.contains("ipad")) return "Tablet"
        if (hostname.contains("macbook") || hostname.contains("mac-")) return "Computer"
        if (hostname.contains("android") || hostname.contains("phone")) return "Phone"
        if (hostname.contains("iot") || hostname.contains("smart")) return "IoT"
        if (hostname.contains("xbox")) return "Game Console"
        if (hostname.contains("desktop") || hostname.contains("laptop") || hostname.contains("pc-")) return "Computer"
        return null
    }

    private fun guessOs(hostname: String): String? {
        if (hostname.contains("iphone") || hostname.contains("ipad")) return "iOS"
        if (hostname.contains("macbook") || hostname.contains("mac-")) return "macOS"
        if (hostname.contains("android")) return "Android"
        if (hostname.contains("windows") || hostname.contains("win-") || hostname.contains("desktop-")) return "Windows"
        if (hostname.contains("linux") || hostname.contains("ubuntu") || hostname.contains("debian") ||
            hostname.contains("raspberry") || hostname.contains("pi-")
        ) return "Linux"
        return null
    }

    companion object {
        private val SERVICE_TYPE_MAP: Map<String, Pair<String?, String?>> = mapOf(
            "_airplay._tcp" to ("Smart TV" to "iOS"),
            "_raop._tcp" to ("AirPlay Speaker" to null),
            "_googlecast._tcp" to ("Chromecast" to "Android"),
            "_spotify-connect._tcp" to ("Smart Speaker" to null),
            "_homekit._tcp" to ("IoT" to null),
            "_hap._tcp" to ("IoT" to null),
            "_companion-link._tcp" to ("Phone" to "iOS"),
            "_printer._tcp" to ("Printer" to null),
            "_pdl-datastream._tcp" to ("Printer" to null),
            "_ipp._tcp" to ("Printer" to null),
            "_smb._tcp" to (null to null),
            "_ssh._tcp" to (null to null),
            "_http._tcp" to (null to null),
            "_https._tcp" to (null to null),
        )
    }
}
