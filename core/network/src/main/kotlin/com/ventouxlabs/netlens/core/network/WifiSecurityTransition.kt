package com.ventouxlabs.netlens.core.network

/**
 * True for WPA2/WPA3 transition mode ("WPA2/WPA3", "WPA3/WPA2", ...): a WPA2-PSK client can still
 * join, so the network is not proven SAE-only and must not be scored or reported as pure WPA3.
 * Enterprise labels ("WPA3-Enterprise") never contain "WPA2" and are not transition mode.
 */
fun isWpa3TransitionMode(encryptionType: String): Boolean {
    val upper = encryptionType.uppercase()
    return upper.contains("WPA3") && upper.contains("WPA2")
}

/** Conservative score cap for transition mode: below pure WPA3 (100), above WPA2 (70). */
const val WPA3_TRANSITION_SCORE: Int = 85
