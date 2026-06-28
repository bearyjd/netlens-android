package com.ventouxlabs.netlens.feature.ipcalc.model

data class SubnetInfo(
    val networkAddress: String,
    val broadcastAddress: String,
    val firstHost: String,
    val lastHost: String,
    val totalHosts: Long,
    val subnetMask: String,
    val wildcardMask: String,
    val cidrNotation: String,
    val ipClass: String,
    val isBogon: Boolean,
)
