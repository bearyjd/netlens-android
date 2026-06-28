package com.ventouxlabs.netlens.widget.util

/**
 * Returns a SSID for display only when the underlying physical link is WiFi.
 *
 * `WifiManager.connectionInfo` keeps returning the last associated SSID even
 * after the device has switched to cellular, so the caller must clear the
 * value when the physical transport is no longer WiFi.
 */
fun gateSsidForTransport(isWifiTransport: Boolean, rawSsid: String?): String? =
    rawSsid?.takeIf { isWifiTransport }
