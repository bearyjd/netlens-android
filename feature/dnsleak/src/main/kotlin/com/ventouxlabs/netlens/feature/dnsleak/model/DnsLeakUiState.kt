package com.ventouxlabs.netlens.feature.dnsleak.model

data class DnsLeakUiState(
    val isLoading: Boolean = false,
    val result: DnsLeakResult? = null,
    val vpnActive: Boolean = false,
    val systemDnsServers: List<String> = emptyList(),
)
