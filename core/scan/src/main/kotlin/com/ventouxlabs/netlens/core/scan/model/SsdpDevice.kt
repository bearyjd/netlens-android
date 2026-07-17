package com.ventouxlabs.netlens.core.scan.model

data class SsdpDevice(
    val ip: String,
    val friendlyName: String? = null,
    val manufacturer: String? = null,
    val modelName: String? = null,
    val deviceType: String? = null,
)
