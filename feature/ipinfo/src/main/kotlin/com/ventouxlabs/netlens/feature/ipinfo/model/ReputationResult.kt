package com.ventouxlabs.netlens.feature.ipinfo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AbuseIpDbResponse(
    val data: AbuseIpDbData,
)

@Serializable
data class AbuseIpDbData(
    @SerialName("ipAddress") val ipAddress: String = "",
    @SerialName("abuseConfidenceScore") val abuseConfidenceScore: Int = 0,
    @SerialName("totalReports") val totalReports: Int = 0,
    @SerialName("isWhitelisted") val isWhitelisted: Boolean? = null,
    @SerialName("usageType") val usageType: String? = null,
    @SerialName("isp") val isp: String? = null,
    @SerialName("domain") val domain: String? = null,
    @SerialName("countryCode") val countryCode: String? = null,
)

data class ReputationResult(
    val abuseConfidenceScore: Int,
    val totalReports: Int,
    val isWhitelisted: Boolean,
    val usageType: String,
    val isp: String,
    val domain: String,
) {
    val riskLevel: ReputationRisk
        get() = when {
            abuseConfidenceScore == 0 && totalReports == 0 -> ReputationRisk.CLEAN
            abuseConfidenceScore < 25 -> ReputationRisk.LOW
            abuseConfidenceScore < 75 -> ReputationRisk.MEDIUM
            else -> ReputationRisk.HIGH
        }
}

enum class ReputationRisk(val label: String) {
    CLEAN("Clean"),
    LOW("Low Risk"),
    MEDIUM("Medium Risk"),
    HIGH("High Risk"),
}
