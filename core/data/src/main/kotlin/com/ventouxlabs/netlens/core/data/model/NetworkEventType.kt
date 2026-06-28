package com.ventouxlabs.netlens.core.data.model

object NetworkEventType {
    const val CONNECTED = "CONNECTED"
    const val DISCONNECTED = "DISCONNECTED"
    const val CHANGED = "CHANGED"
    const val DNS_CHANGE = "DNS_CHANGE"
    const val SPEED_TEST = "SPEED_TEST"
    const val SECURITY_AUDIT = "SECURITY_AUDIT"
    const val SCORE_CHANGE = "SCORE_CHANGE"
    const val NEW_DEVICE = "NEW_DEVICE"

    val ALL = setOf(
        CONNECTED, DISCONNECTED, CHANGED,
        DNS_CHANGE, SPEED_TEST, SECURITY_AUDIT,
        SCORE_CHANGE, NEW_DEVICE,
    )
}
