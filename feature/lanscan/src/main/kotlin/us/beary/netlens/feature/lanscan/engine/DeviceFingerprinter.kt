package us.beary.netlens.feature.lanscan.engine

import us.beary.netlens.feature.lanscan.model.LanDevice
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
