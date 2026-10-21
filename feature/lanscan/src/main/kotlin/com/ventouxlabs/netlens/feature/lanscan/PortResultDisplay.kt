package com.ventouxlabs.netlens.feature.lanscan

import com.ventouxlabs.netlens.core.scan.model.PortRiskLevel
import com.ventouxlabs.netlens.feature.lanscan.model.HostPortResult

internal data class PortScanSummary(
    val open: Int,
    val closedOrFiltered: Int,
    val scanned: Int,
)

/** Open ports only, grouped by risk level in display order. */
internal fun openPortGroups(results: List<HostPortResult>): List<Pair<PortRiskLevel, List<HostPortResult>>> =
    results
        .filter { it.isOpen }
        .groupBy { it.riskLevel }
        .entries
        .sortedBy { it.key.sortOrder }
        .map { it.key to it.value }

internal fun portScanSummary(results: List<HostPortResult>): PortScanSummary {
    val open = results.count { it.isOpen }
    return PortScanSummary(open = open, closedOrFiltered = results.size - open, scanned = results.size)
}

/** Service name for the row title, or null when it is blank or the generic "Port <n>" fallback. */
internal fun portServiceNameToShow(port: Int, serviceName: String): String? =
    serviceName.takeUnless { it.isBlank() || it.trim() == "Port $port" }

/** Description line, or null when it is blank or merely repeats the service name. */
internal fun portDescriptionToShow(serviceName: String, description: String): String? =
    description.takeUnless { it.isBlank() || it.trim().equals(serviceName.trim(), ignoreCase = true) }
