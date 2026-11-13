package com.ventouxlabs.netlens.feature.lanscan

import com.ventouxlabs.netlens.core.data.testing.FakeKnownDeviceDao
import com.ventouxlabs.netlens.core.scan.DeviceInventoryRepositoryImpl
import com.ventouxlabs.netlens.core.scan.engine.FakeArpTableReader
import com.ventouxlabs.netlens.core.scan.engine.FakeLanMdnsScanner
import com.ventouxlabs.netlens.core.scan.engine.FakeLanNetworkBinder
import com.ventouxlabs.netlens.core.scan.engine.FakeNetBiosProber
import com.ventouxlabs.netlens.core.scan.engine.FakePortScanner
import com.ventouxlabs.netlens.core.scan.engine.FakeSsdpScanner
import com.ventouxlabs.netlens.core.scan.engine.FakeSubnetScanner
import com.ventouxlabs.netlens.core.scan.engine.PortFingerprint
import com.ventouxlabs.netlens.core.scan.model.LanDevice
import com.ventouxlabs.netlens.feature.lanscan.engine.FakeScanLocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The port-scan fingerprint must not erase a confidence the device already earned, and the host
 * detail state must carry the type and OS confidences separately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HostDetailFingerprintConfidenceTest {

    private lateinit var fingerprinter: FakeDeviceFingerprinter
    private lateinit var viewModel: LanScanViewModel

    private val mdnsDevice = LanDevice(
        ip = "192.168.1.20",
        deviceType = "Phone",
        osGuess = "iOS",
        fingerprintConfidence = 90,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fingerprinter = FakeDeviceFingerprinter()
        viewModel = LanScanViewModel(
            subnetScanner = FakeSubnetScanner(),
            mdnsScanner = FakeLanMdnsScanner(),
            fingerprinter = fingerprinter,
            portScanner = FakePortScanner(),
            ssdpScanner = FakeSsdpScanner(),
            netBiosProber = FakeNetBiosProber(),
            arpTableReader = FakeArpTableReader(),
            lanNetworkBinder = FakeLanNetworkBinder(),
            networkInterfaceProvider = FakeNetworkInterfaceProvider(),
            lanScanHistoryDao = FakeLanScanHistoryDao(),
            knownDeviceDao = FakeKnownDeviceDao(),
            deviceInventoryRepository = DeviceInventoryRepositoryImpl(
                FakeKnownDeviceDao(),
                FakeNewDeviceNotifier(),
            ),
            scanLocationProvider = FakeScanLocationProvider(),
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `port scan that derives nothing does not overwrite the per-field confidence with zero`() = runTest {
        // A port scan with nothing new inherits the device's existing fields and confidence.
        fingerprinter.portFingerprint = PortFingerprint(
            deviceType = "Phone",
            osGuess = "iOS",
            evidence = emptyList(),
            typeConfidence = 90,
            osConfidence = 90,
        )

        viewModel.selectDevice(mdnsDevice)

        val detail = viewModel.hostDetail.value
        assertEquals(90, detail?.enrichedTypeConfidence)
        assertEquals(90, detail?.enrichedOsConfidence)
    }

    @Test
    fun `host detail carries type and os confidence separately`() = runTest {
        fingerprinter.portFingerprint = PortFingerprint(
            deviceType = "Computer",
            osGuess = "Windows",
            evidence = listOf("port 3389 (RDP)"),
            typeConfidence = 40,
            osConfidence = 80,
        )

        viewModel.selectDevice(
            mdnsDevice.copy(deviceType = "Computer", osGuess = null, fingerprintConfidence = 40),
        )

        val detail = viewModel.hostDetail.value
        assertEquals(40, detail?.enrichedTypeConfidence)
        assertEquals(80, detail?.enrichedOsConfidence)
    }
}
