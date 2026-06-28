package com.ventouxlabs.netlens.feature.tls.model

data class TlsCertInfo(
    val subjectCN: String,
    val issuerCN: String,
    val serialNumber: String,
    val notBefore: String,
    val notAfter: String,
    val signatureAlgorithm: String,
    val isExpired: Boolean,
    val daysUntilExpiry: Long,
)
