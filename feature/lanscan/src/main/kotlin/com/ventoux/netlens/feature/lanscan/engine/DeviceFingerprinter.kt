package com.ventoux.netlens.feature.lanscan.engine

import com.ventoux.netlens.feature.lanscan.model.LanDevice
import javax.inject.Inject

class DeviceFingerprinter @Inject constructor() {

    fun fingerprint(device: LanDevice): LanDevice {
        val hostname = device.hostname?.lowercase() ?: ""
        val deviceType = guessDeviceType(hostname)
        val osGuess = guessOs(hostname)
        return device.copy(deviceType = deviceType, osGuess = osGuess)
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

    data class PortFingerprint(
        val deviceType: String?,
        val osGuess: String?,
        val evidence: List<String>,
    )

    fun fingerprintWithPorts(device: LanDevice, openPorts: List<Int>): PortFingerprint {
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

        return PortFingerprint(type, os, evidence)
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
}
