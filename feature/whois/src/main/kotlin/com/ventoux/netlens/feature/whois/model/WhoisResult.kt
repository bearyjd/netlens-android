package com.ventoux.netlens.feature.whois.model

data class WhoisResult(
    val domain: String,
    val registrar: String?,
    val createdDate: String?,
    val expiryDate: String?,
    val nameServers: List<String>,
    val rawResponse: String,
)
