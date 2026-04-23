package us.beary.netlens.feature.lanscan.engine

import us.beary.netlens.feature.lanscan.model.LanDevice
import javax.inject.Inject

class DeviceFingerprinter @Inject constructor() {

    fun fingerprint(device: LanDevice): LanDevice {
        val vendor = device.vendor?.lowercase() ?: ""
        val hostname = device.hostname?.lowercase() ?: ""

        val deviceType = guessDeviceType(vendor, hostname)
        val osGuess = guessOs(vendor, hostname)

        return device.copy(deviceType = deviceType, osGuess = osGuess)
    }

    private fun guessDeviceType(vendor: String, hostname: String): String? {
        // Router/Gateway detection
        if (vendor.contains("cisco") || vendor.contains("netgear") || vendor.contains("tp-link") ||
            vendor.contains("linksys") || vendor.contains("ubiquiti") ||
            vendor.contains("mikrotik") || vendor.contains("juniper") || vendor.contains("aruba") ||
            hostname.contains("router") || hostname.contains("gateway")
        ) {
            return "Router"
        }

        // Printer detection
        if (vendor.contains("hewlett") || vendor.contains("hp inc") || vendor.contains("canon") ||
            vendor.contains("epson") || vendor.contains("brother") || vendor.contains("xerox") ||
            vendor.contains("lexmark") || hostname.contains("printer") || hostname.contains("prn")
        ) {
            return "Printer"
        }

        // Smart TV / Streaming
        if (vendor.contains("samsung") && (hostname.contains("tv") || hostname.contains("tizen")) ||
            vendor.contains("lg electronics") && hostname.contains("tv") ||
            vendor.contains("roku") || vendor.contains("amazon") && hostname.contains("fire") ||
            vendor.contains("vizio")
        ) {
            return "Smart TV"
        }

        // Apple devices
        if (vendor.contains("apple")) {
            return when {
                hostname.contains("iphone") -> "Phone"
                hostname.contains("ipad") -> "Tablet"
                hostname.contains("macbook") || hostname.contains("mac-") -> "Computer"
                hostname.contains("appletv") || hostname.contains("apple-tv") -> "Smart TV"
                else -> "Apple Device"
            }
        }

        // Phone detection
        if (vendor.contains("samsung") || vendor.contains("xiaomi") || vendor.contains("huawei") ||
            vendor.contains("oneplus") || vendor.contains("google") || vendor.contains("motorola") ||
            vendor.contains("sony mobile") || vendor.contains("oppo") || vendor.contains("vivo") ||
            hostname.contains("android") || hostname.contains("phone")
        ) {
            return "Phone"
        }

        // IoT / Smart Home
        if (vendor.contains("espressif") || vendor.contains("tuya") || vendor.contains("shelly") ||
            vendor.contains("sonoff") || vendor.contains("nest") || vendor.contains("ring") ||
            vendor.contains("ecobee") || vendor.contains("philips hue") ||
            hostname.contains("iot") || hostname.contains("smart")
        ) {
            return "IoT"
        }

        // Game console
        if (vendor.contains("nintendo") || vendor.contains("sony interactive") ||
            vendor.contains("microsoft") && hostname.contains("xbox")
        ) {
            return "Game Console"
        }

        // Computer (generic - checked last as fallback)
        if (vendor.contains("intel") || vendor.contains("dell") || vendor.contains("lenovo") ||
            vendor.contains("acer") || vendor.contains("asus") || vendor.contains("msi") ||
            hostname.contains("desktop") || hostname.contains("laptop") || hostname.contains("pc")
        ) {
            return "Computer"
        }

        return null
    }

    private fun guessOs(vendor: String, hostname: String): String? {
        if (vendor.contains("apple") || hostname.contains("iphone") || hostname.contains("ipad")) {
            return if (hostname.contains("iphone") || hostname.contains("ipad")) "iOS" else "macOS"
        }
        if (hostname.contains("android") || vendor.contains("xiaomi") ||
            vendor.contains("samsung") && !hostname.contains("tv") ||
            vendor.contains("oneplus") || vendor.contains("oppo") || vendor.contains("vivo") ||
            vendor.contains("huawei") && !hostname.contains("router")
        ) {
            return "Android"
        }
        if (hostname.contains("windows") || hostname.contains("win-") || hostname.contains("desktop-")) {
            return "Windows"
        }
        if (hostname.contains("linux") || hostname.contains("ubuntu") || hostname.contains("debian") ||
            hostname.contains("raspberry") || hostname.contains("pi")
        ) {
            return "Linux"
        }
        return null
    }
}
