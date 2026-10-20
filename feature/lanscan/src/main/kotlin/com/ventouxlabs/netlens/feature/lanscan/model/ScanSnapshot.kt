package com.ventouxlabs.netlens.feature.lanscan.model

import com.ventouxlabs.netlens.core.scan.model.LanDevice
import kotlinx.serialization.Serializable

data class ScanCoordinates(val latitude: Double, val longitude: Double)

@Serializable
data class ScanSnapshotDevice(
    val ip: String,
    val hostname: String? = null,
    val latencyMs: Long = 0,
    val macAddress: String? = null,
    val vendor: String? = null,
    val deviceType: String? = null,
    val osGuess: String? = null,
    val services: List<String> = emptyList(),
    // Added after the first snapshots shipped: defaults to null so older stored JSON still decodes.
    val model: String? = null,
)

fun LanDevice.toSnapshotDevice() = ScanSnapshotDevice(
    ip = ip,
    hostname = hostname,
    latencyMs = latencyMs,
    macAddress = macAddress,
    vendor = vendor,
    deviceType = deviceType,
    osGuess = osGuess,
    services = services,
    model = model,
)
