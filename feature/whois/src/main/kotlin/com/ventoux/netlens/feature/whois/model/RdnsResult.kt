package com.ventoux.netlens.feature.whois.model

data class RdnsResult(
    val ip: String,
    val hostnames: List<String>,
)
