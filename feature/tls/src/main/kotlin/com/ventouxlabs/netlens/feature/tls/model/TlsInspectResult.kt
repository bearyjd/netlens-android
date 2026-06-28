package com.ventouxlabs.netlens.feature.tls.model

data class TlsInspectResult(
    val host: String,
    val port: Int,
    val protocol: String,
    val cipherSuite: String,
    val certificates: List<TlsCertInfo>,
)
