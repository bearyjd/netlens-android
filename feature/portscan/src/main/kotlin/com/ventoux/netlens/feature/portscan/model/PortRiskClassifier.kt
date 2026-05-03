package com.ventoux.netlens.feature.portscan.model

object PortRiskClassifier {

    private val CRITICAL_PORTS = setOf(21, 23, 135, 139, 445, 3389, 5900)

    fun classifyRisk(port: Int, isOpen: Boolean): PortRiskLevel {
        if (!isOpen) return PortRiskLevel.CLOSED
        return if (port in CRITICAL_PORTS) PortRiskLevel.CRITICAL else PortRiskLevel.WARNING
    }
}
