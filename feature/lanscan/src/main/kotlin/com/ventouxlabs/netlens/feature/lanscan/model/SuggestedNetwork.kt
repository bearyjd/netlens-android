package com.ventouxlabs.netlens.feature.lanscan.model

data class SuggestedNetwork(
    val cidr: String,
    val ip: String,
    val prefixLength: Int,
    val label: String,
    val isVpn: Boolean,
)
