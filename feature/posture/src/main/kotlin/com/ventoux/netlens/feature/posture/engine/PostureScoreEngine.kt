package com.ventoux.netlens.feature.posture.engine

import androidx.compose.ui.graphics.Color
import com.ventoux.netlens.feature.posture.model.FactorResult
import com.ventoux.netlens.feature.posture.model.PostureFactor
import com.ventoux.netlens.feature.posture.model.PostureScore
import com.ventoux.netlens.feature.posture.model.Severity

object PostureScoreEngine {

    fun compute(
        encryptionType: String?,
        isConnected: Boolean,
        deviceCount: Int?,
        isVpnActive: Boolean,
    ): PostureScore {
        if (!isConnected) return PostureScore.UNSCANNED

        val factors = buildList {
            add(evaluateEncryption(encryptionType))
            deviceCount?.let { add(evaluateDeviceCount(it)) }
            add(evaluateVpn(isVpnActive))
        }

        val activeFactors = factors.filter { it.severity != Severity.Unavailable }
        if (activeFactors.isEmpty()) return PostureScore.UNSCANNED

        val totalWeight = activeFactors.sumOf { it.factor.weight.toDouble() }
        val weightedSum = activeFactors.sumOf { it.score * it.factor.weight.toDouble() }
        val numericScore = (weightedSum / totalWeight).toInt().coerceIn(0, 100)

        val grade = gradeFor(numericScore)
        val color = colorFor(grade)

        return PostureScore(
            grade = grade,
            numericScore = numericScore,
            color = color,
            factors = factors,
        )
    }

    internal fun evaluateEncryption(type: String?): FactorResult {
        val normalized = type?.uppercase()?.trim() ?: ""
        val (score, severity, detail) = when {
            normalized.contains("WPA3") -> Triple(100, Severity.Good, "WPA3 is the strongest available encryption")
            normalized.contains("WPA2") -> Triple(70, Severity.Moderate, "WPA2 is adequate but WPA3 is recommended")
            normalized.contains("WPA") -> Triple(50, Severity.Moderate, "WPA is outdated — upgrade to WPA2 or WPA3")
            normalized.contains("WEP") -> Triple(20, Severity.Critical, "WEP is broken and trivially crackable — switch immediately")
            normalized.isEmpty() -> Triple(0, Severity.Critical, "No encryption detected — network traffic is visible to anyone nearby")
            else -> Triple(40, Severity.Moderate, "Unknown encryption type: $type")
        }
        val label = if (normalized.isEmpty()) "Open / None" else type ?: "Unknown"
        return FactorResult(
            factor = PostureFactor.Encryption,
            score = score,
            label = label,
            detail = detail,
            severity = severity,
        )
    }

    internal fun evaluateDeviceCount(count: Int): FactorResult {
        val (score, severity, detail) = when {
            count <= 5 -> Triple(100, Severity.Good, "$count devices — small, manageable network")
            count <= 15 -> Triple(80, Severity.Good, "$count devices — typical home network")
            count <= 30 -> Triple(60, Severity.Moderate, "$count devices — larger attack surface, review unknown devices")
            else -> Triple(40, Severity.Poor, "$count devices — very large network, high risk of unmanaged devices")
        }
        return FactorResult(
            factor = PostureFactor.DeviceCount,
            score = score,
            label = "$count device${if (count != 1) "s" else ""}",
            detail = detail,
            severity = severity,
        )
    }

    internal fun evaluateVpn(isActive: Boolean): FactorResult {
        return if (isActive) {
            FactorResult(
                factor = PostureFactor.VpnStatus,
                score = 100,
                label = "Active",
                detail = "VPN is active — your traffic is encrypted and your IP is hidden",
                severity = Severity.Good,
            )
        } else {
            FactorResult(
                factor = PostureFactor.VpnStatus,
                score = 40,
                label = "Inactive",
                detail = "No VPN detected — your ISP and network operator can see your traffic",
                severity = Severity.Moderate,
            )
        }
    }

    internal fun gradeFor(score: Int): String = when {
        score >= 90 -> "A"
        score >= 75 -> "B"
        score >= 60 -> "C"
        score >= 40 -> "D"
        else -> "F"
    }

    internal fun colorFor(grade: String): Color = when (grade) {
        "A" -> Color(0xFF4CAF50)
        "B" -> Color(0xFF8BC34A)
        "C" -> Color(0xFFFFC107)
        "D" -> Color(0xFFFF9800)
        "F" -> Color(0xFFF44336)
        else -> Color(0xFF9E9E9E)
    }
}
