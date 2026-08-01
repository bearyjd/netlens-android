package com.ventouxlabs.netlens.feature.lanscan

import app.cash.paparazzi.Paparazzi
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.feature.lanscan.model.HostDetailState
import com.ventouxlabs.netlens.feature.lanscan.model.HostPortResult
import com.ventouxlabs.netlens.feature.portscan.model.PortRiskLevel
import org.junit.Rule
import org.junit.Test

/**
 * Composition smoke tests — does the sheet *render*, not does it look right. No golden images;
 * see `AndroidScreenshotConventionPlugin` for why that is the point rather than a shortcut.
 *
 * `ModalBottomSheet` does render under Paparazzi, which was worth checking before writing these —
 * a sheet is hosted differently from ordinary content.
 */
class HostDetailSheetRenderTest {

    @get:Rule
    val paparazzi = Paparazzi()

    private fun result(
        port: Int,
        risk: PortRiskLevel,
        service: String = "svc",
        protocol: String = "TCP",
    ) = HostPortResult(
        port = port,
        protocol = protocol,
        serviceName = service,
        isOpen = true,
        latencyMs = 4,
        riskLevel = risk,
        description = "$service on $port",
    )

    private fun render(state: HostDetailState) = paparazzi.snapshot {
        HostDetailSheet(
            state = state,
            onDismiss = {},
            onScanPorts = {},
            onCancelScan = {},
            onNavigateToTool = { _, _ -> },
            onShareJson = null,
            onOpenService = {},
        )
    }

    @Test
    fun `renders results grouped across risk levels`() {
        render(
            HostDetailState(
                device = LanDevice(ip = "192.168.1.10", hostname = "nas.local"),
                enrichedResults = listOf(
                    result(22, PortRiskLevel.CRITICAL, "ssh"),
                    result(23, PortRiskLevel.CRITICAL, "telnet"),
                    result(80, PortRiskLevel.WARNING, "http"),
                    result(443, PortRiskLevel.INFO, "https"),
                ),
                openCount = 4,
            ),
        )
    }

    @Test
    fun `the same port on two protocols renders as two rows`() {
        // The row key used to be "${riskLevel}_${port}", ignoring the protocol that
        // HostPortResult carries — so 80/TCP and 80/UDP both keyed to "WARNING_80" and would
        // crash the list. Nothing emits anything but TCP today, which is precisely the condition
        // under which #116's duplicate-key crash sat unnoticed: the key was fine for the data
        // that existed and wrong for the data the model allows. Reverting the protocol out of
        // the key fails exactly this test.
        render(
            HostDetailState(
                device = LanDevice(ip = "192.168.1.10", hostname = "nas.local"),
                enrichedResults = listOf(
                    result(80, PortRiskLevel.WARNING, "http", protocol = "TCP"),
                    result(80, PortRiskLevel.WARNING, "http", protocol = "UDP"),
                ),
                openCount = 2,
            ),
        )
    }

    @Test
    fun `a host with no scan yet renders`() {
        render(HostDetailState(device = LanDevice(ip = "192.168.1.10")))
    }

    @Test
    fun `a scan in progress renders`() {
        render(
            HostDetailState(
                device = LanDevice(ip = "192.168.1.10", hostname = "nas.local"),
                isScanning = true,
                progress = 0.4f,
                enrichedResults = listOf(result(22, PortRiskLevel.CRITICAL, "ssh")),
                openCount = 1,
            ),
        )
    }

    @Test
    fun `an error renders`() {
        render(
            HostDetailState(
                device = LanDevice(ip = "192.168.1.10"),
                error = "Scan failed: host unreachable",
            ),
        )
    }

    @Test
    fun `a fingerprinted host with evidence renders`() {
        render(
            HostDetailState(
                device = LanDevice(
                    ip = "192.168.1.10",
                    hostname = "nas.local",
                    macAddress = "aa:bb:cc:dd:ee:ff",
                ),
                enrichedResults = listOf(result(445, PortRiskLevel.CRITICAL, "smb")),
                enrichedType = "NAS",
                enrichedOs = "Linux",
                fingerprintEvidence = listOf("SMB banner", "mDNS _smb._tcp"),
                openCount = 1,
            ),
        )
    }
}
