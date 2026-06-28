package com.ventouxlabs.netlens.feature.portscan.model

object PortRiskClassifier {

    private val CRITICAL_PORTS = setOf(21, 23, 135, 139, 445, 3389, 5900)
    private val SAFE_PORTS = setOf(22, 80, 443, 465, 587, 636, 993, 995, 8443)

    fun classifyRisk(port: Int, isOpen: Boolean): PortRiskLevel {
        if (!isOpen) return PortRiskLevel.CLOSED
        return when (port) {
            in CRITICAL_PORTS -> PortRiskLevel.CRITICAL
            in SAFE_PORTS -> PortRiskLevel.INFO
            else -> PortRiskLevel.WARNING
        }
    }
}
