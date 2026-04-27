package com.ventoux.netlens.feature.history

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ventoux.netlens.core.data.model.HistoryDetailData
import com.ventoux.netlens.feature.history.model.HistoryItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryDetailSheet(
    item: HistoryItem,
    data: HistoryDetailData,
    onDismiss: () -> Unit,
    onRerun: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // Header
            item {
                Text(
                    text = item.toolName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = item.primaryLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = DateUtils.getRelativeTimeSpanString(
                        item.timestamp,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { HorizontalDivider() }

            // Detail content dispatched by type
            item {
                when (data) {
                    is HistoryDetailData.Ping -> PingDetail(data.entry)
                    is HistoryDetailData.LanScan -> LanScanDetail(data.entry)
                    is HistoryDetailData.PortScan -> PortScanDetail(data.entry)
                    is HistoryDetailData.Dns -> DnsDetail(data.entry)
                    is HistoryDetailData.Whois -> WhoisDetail(data.entry)
                    is HistoryDetailData.IpInfo -> IpInfoDetail(data.entry)
                    is HistoryDetailData.Traceroute -> TracerouteDetail(data.entry)
                    is HistoryDetailData.Tls -> TlsDetail(data.entry)
                    is HistoryDetailData.HttpTest -> HttpTestDetail(data.entry)
                    is HistoryDetailData.Mdns -> MdnsDetail(data.entry)
                    is HistoryDetailData.Wol -> WolDetail(data.entry)
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = onRerun,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.history_detail_rerun))
                }
            }
        }
    }
}

// --- Per-tool detail renderers ---

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PingDetail(entry: com.ventoux.netlens.core.data.model.PingHistoryEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DetailRow("Host", entry.host)
        DetailRow("Sent", entry.sentCount.toString())
        DetailRow("Received", entry.receivedCount.toString())
        DetailRow("Min", "%.1f ms".format(entry.minMs))
        DetailRow("Avg", "%.1f ms".format(entry.avgMs))
        DetailRow("Max", "%.1f ms".format(entry.maxMs))
        DetailRow("Mode", entry.mode)
    }
}

@Composable
private fun LanScanDetail(entry: com.ventoux.netlens.core.data.model.LanScanHistoryEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entry.ssid?.let { DetailRow("SSID", it) }
        entry.subnet?.let { DetailRow("Subnet", it) }
        DetailRow("Devices Found", entry.deviceCount.toString())
        if (entry.devicesJson.isNotBlank() && entry.devicesJson != "[]") {
            Spacer(Modifier.height(4.dp))
            Text(
                "Devices",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            val devices = try {
                kotlinx.serialization.json.Json.decodeFromString<List<String>>(entry.devicesJson)
            } catch (_: Exception) {
                entry.devicesJson.removeSurrounding("[", "]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"") }
            }
            devices.forEach { ip ->
                Text(ip, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun PortScanDetail(entry: com.ventoux.netlens.core.data.model.PortScanHistoryEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DetailRow("Host", entry.host)
        DetailRow("Total Scanned", entry.totalScanned.toString())
        DetailRow("Duration", "${entry.durationMs} ms")
        val ports = entry.openPorts
            .takeIf { it.isNotBlank() && it != "[]" }
            ?.removeSurrounding("[", "]")
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?: emptyList()
        DetailRow("Open Ports", ports.size.toString())
        if (ports.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Open Ports",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            ports.forEach { port ->
                Text(
                    port.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun DnsDetail(entry: com.ventoux.netlens.core.data.model.DnsHistoryEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DetailRow("Query", entry.query)
        DetailRow("Record Type", entry.recordType)
        if (entry.resultsJson.isNotBlank() && entry.resultsJson != "[]") {
            Spacer(Modifier.height(4.dp))
            Text(
                "Results",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            val records = try {
                kotlinx.serialization.json.Json.decodeFromString<List<String>>(entry.resultsJson)
            } catch (_: Exception) {
                entry.resultsJson.removeSurrounding("[", "]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"") }
            }
            records.forEach { record ->
                Text(
                    record,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun WhoisDetail(entry: com.ventoux.netlens.core.data.model.WhoisHistoryEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DetailRow("Query", entry.query)
        Spacer(Modifier.height(4.dp))
        Text(
            "Response",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = entry.rawResponse,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun IpInfoDetail(entry: com.ventoux.netlens.core.data.model.IpInfoHistoryEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DetailRow("IP", entry.ip)
        entry.isp?.let { DetailRow("ISP", it) }
        entry.org?.let { DetailRow("Organization", it) }
        entry.city?.let { DetailRow("City", it) }
        entry.countryCode?.let { DetailRow("Country", it) }
        DetailRow("VPN", if (entry.isVpn) "Yes" else "No")
    }
}

@Composable
private fun TracerouteDetail(entry: com.ventoux.netlens.core.data.model.TracerouteHistoryEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DetailRow("Host", entry.host)
        DetailRow("Hops", entry.hopCount.toString())
        if (entry.hopsJson.isNotBlank() && entry.hopsJson != "[]") {
            Spacer(Modifier.height(4.dp))
            Text(
                "Hop Details",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            val hops = try {
                kotlinx.serialization.json.Json.decodeFromString<List<String>>(entry.hopsJson)
            } catch (_: Exception) {
                entry.hopsJson.removeSurrounding("[", "]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"") }
            }
            hops.forEachIndexed { index, hop ->
                Text(
                    "${index + 1}. $hop",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun TlsDetail(entry: com.ventoux.netlens.core.data.model.TlsHistoryEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DetailRow("Host", "${entry.host}:${entry.port}")
        DetailRow("Valid", if (entry.isValid) "Yes" else "No")
        DetailRow("Issuer", entry.issuer)
        DetailRow("Subject", entry.subject)
        DetailRow("Expires", entry.expiresAt)
        DetailRow("Protocol", entry.protocol)
    }
}

@Composable
private fun HttpTestDetail(entry: com.ventoux.netlens.core.data.model.HttpTesterHistoryEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DetailRow("URL", entry.url)
        DetailRow("Method", entry.method)
        DetailRow("Status", entry.statusCode.toString())
        DetailRow("Duration", "${entry.durationMs} ms")
        DetailRow("Response Size", "${entry.responseSize} bytes")
    }
}

@Composable
private fun MdnsDetail(entry: com.ventoux.netlens.core.data.model.MdnsHistoryEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DetailRow("Services Found", entry.serviceCount.toString())
        if (entry.servicesJson.isNotBlank() && entry.servicesJson != "[]") {
            Spacer(Modifier.height(4.dp))
            Text(
                "Services",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            val services = try {
                kotlinx.serialization.json.Json.decodeFromString<List<String>>(entry.servicesJson)
            } catch (_: Exception) {
                entry.servicesJson.removeSurrounding("[", "]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"") }
            }
            services.forEach { service ->
                Text(
                    service,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun WolDetail(entry: com.ventoux.netlens.core.data.model.WolHistoryEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entry.label?.let { DetailRow("Label", it) }
        DetailRow("MAC Address", entry.mac)
        DetailRow("Broadcast IP", entry.broadcastIp)
    }
}
