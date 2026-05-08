package com.ventoux.netlens.feature.posture.engine

import com.ventoux.netlens.core.network.VpnState
import com.ventoux.netlens.feature.posture.model.FactorResult
import com.ventoux.netlens.feature.posture.model.PostureFactor
import com.ventoux.netlens.feature.posture.model.PostureScore
import com.ventoux.netlens.feature.posture.model.Severity

object PostureScoreEngine {

    fun compute(
        encryptionType: String?,
        isConnected: Boolean,
        deviceCount: Int?,
        vpnState: VpnState,
        untrustedNetwork: Boolean,
    ): PostureScore? {
        if (!isConnected) return null

        val factors = buildList {
            add(evaluateEncryption(encryptionType))
            deviceCount?.let { add(evaluateDeviceCount(it)) }
            add(evaluateVpn(vpnState, untrustedNetwork))
        }

        val activeFactors = factors.filter { it.severity != Severity.Unavailable }
        if (activeFactors.isEmpty()) return null

        val totalWeight = activeFactors.sumOf { it.factor.weight.toDouble() }
        val weightedSum = activeFactors.sumOf { it.score * it.factor.weight.toDouble() }
        val numericScore = (weightedSum / totalWeight).toInt().coerceIn(0, 100)

        val grade = gradeFor(numericScore)

        return PostureScore(
            grade = grade,
            numericScore = numericScore,
            factors = factors,
            vpnState = vpnState,
        )
    }

    internal fun evaluateEncryption(type: String?): FactorResult {
        if (type == null) {
            return FactorResult(
                factor = PostureFactor.Encryption,
                score = 0,
                label = "N/A",
                detail = "Not connected to Wi-Fi — encryption does not apply",
                severity = Severity.Unavailable,
            )
        }
        val normalized = type.uppercase().trim()
        val (score, severity, detail) = when {
            normalized.contains("WPA3") -> Triple(100, Severity.Good, "WPA3 is the strongest available encryption")
            normalized.contains("OWE") || normalized.contains("ENHANCED_OPEN") ->
                Triple(80, Severity.Good, "Enhanced Open (OWE) encrypts traffic without requiring a password")
            normalized.contains("WPA2") -> Triple(70, Severity.Moderate, "WPA2 is adequate but WPA3 is recommended")
            normalized.contains("WPA") -> Triple(50, Severity.Moderate, "WPA is outdated — upgrade to WPA2 or WPA3")
            normalized.contains("WEP") -> Triple(20, Severity.Critical, "WEP is broken and trivially crackable — switch immediately")
            normalized.isEmpty() || normalized == "OPEN" ->
                Triple(0, Severity.Critical, "No encryption detected — network traffic is visible to anyone nearby")
            else -> Triple(40, Severity.Moderate, "Unknown encryption type: $type")
        }
        val label = if (normalized.isEmpty() || normalized == "OPEN") "Open / None" else type
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

    internal fun evaluateVpn(state: VpnState, untrustedNetwork: Boolean): FactorResult {
        return when (state) {
            VpnState.FullTunnel -> FactorResult(
                factor = PostureFactor.VpnStatus,
                score = 100,
                label = "Full tunnel",
                detail = "VPN is active and protecting all traffic — your IP is hidden",
                severity = Severity.Good,
            )
            VpnState.SplitTunnel -> FactorResult(
                factor = PostureFactor.VpnStatus,
                score = if (untrustedNetwork) 60 else 80,
                label = "Split tunnel",
                detail = "Only some traffic is protected — review your VPN settings to enable full-tunnel mode",
                severity = if (untrustedNetwork) Severity.Moderate else Severity.Good,
            )
            VpnState.None -> FactorResult(
                factor = PostureFactor.VpnStatus,
                score = if (untrustedNetwork) 30 else 60,
                label = "Inactive",
                detail = if (untrustedNetwork)
                    "No VPN on an untrusted network — your traffic may be visible to others"
                else
                    "No VPN — fine on networks you trust, otherwise enable a VPN",
                severity = if (untrustedNetwork) Severity.Poor else Severity.Moderate,
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

}
