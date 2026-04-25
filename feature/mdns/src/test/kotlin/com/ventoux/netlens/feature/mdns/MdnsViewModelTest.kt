package com.ventoux.netlens.feature.mdns

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.ventoux.netlens.feature.mdns.engine.FakeMdnsScanner
import com.ventoux.netlens.feature.mdns.model.MdnsService
import com.ventoux.netlens.feature.mdns.model.MdnsUiState

@OptIn(ExperimentalCoroutinesApi::class)
class MdnsViewModelTest {

    private lateinit var fakeScanner: FakeMdnsScanner
    private lateinit var viewModel: MdnsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeScanner = FakeMdnsScanner()
        viewModel = MdnsViewModel(fakeScanner)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has defaults`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(MdnsUiState(), state)
            assertEquals(emptyList<MdnsService>(), state.services)
            assertFalse(state.isScanning)
            assertNull(state.error)
        }
    }

    @Test
    fun `startScan sets isScanning to true`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial state

            viewModel.startScan()

            val scanningState = awaitItem()
            assertTrue(scanningState.isScanning)
            assertEquals(emptyList<MdnsService>(), scanningState.services)
            assertNull(scanningState.error)
        }
    }

    @Test
    fun `services discovered are added to state`() = runTest {
        val service1 = MdnsService(
            serviceName = "My Printer",
            serviceType = "_http._tcp.",
            host = "192.168.1.100",
            port = 80,
        )
        val service2 = MdnsService(
            serviceName = "NAS Server",
            serviceType = "_smb._tcp.",
            host = "192.168.1.101",
            port = 445,
        )

        viewModel.uiState.test {
            awaitItem() // initial state

            viewModel.startScan()
            awaitItem() // isScanning = true

            fakeScanner.emit(service1)
            val stateWith1 = awaitItem()
            assertEquals(1, stateWith1.services.size)
            assertEquals(service1, stateWith1.services[0])

            fakeScanner.emit(service2)
            val stateWith2 = awaitItem()
            assertEquals(2, stateWith2.services.size)
            assertEquals(service2, stateWith2.services[1])
        }
    }

    @Test
    fun `duplicate services are not added`() = runTest {
        val service = MdnsService(
            serviceName = "My Printer",
            serviceType = "_http._tcp.",
            host = "192.168.1.100",
            port = 80,
        )

        viewModel.uiState.test {
            awaitItem() // initial state

            viewModel.startScan()
            awaitItem() // isScanning = true

            fakeScanner.emit(service)
            val stateWith1 = awaitItem()
            assertEquals(1, stateWith1.services.size)

            // Send the same service again -- should be deduped by name+type
            fakeScanner.emit(service)
            // No new emission expected since state didn't change (same object returned)
            expectNoEvents()
        }
    }

    @Test
    fun `stopScan stops scanning and sets isScanning to false`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial state

            viewModel.startScan()
            awaitItem() // isScanning = true

            viewModel.stopScan()

            val finalState = awaitItem()
            assertFalse(finalState.isScanning)
        }
    }

    @Test
    fun `scan with flow error shows error in state`() = runTest {
        // Create a scanner that emits an error inside the Flow (via catch operator)
        // rather than throwing synchronously from discoverServices().
        val errorScanner = object : com.ventoux.netlens.feature.mdns.engine.MdnsScanner {
            override fun discoverServices(serviceType: String): kotlinx.coroutines.flow.Flow<MdnsService> {
                return kotlinx.coroutines.flow.flow {
                    throw RuntimeException("Network error")
                }
            }

            override fun stopDiscovery() {}
        }
        val errorViewModel = MdnsViewModel(errorScanner)

        errorViewModel.uiState.test {
            awaitItem() // initial state

            errorViewModel.startScan()

            val finalState = expectMostRecentItem()
            assertEquals("Network error", finalState.error)
        }
    }

    @Test
    fun `startScan while already scanning is ignored`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial state

            viewModel.startScan()
            awaitItem() // isScanning = true

            // Calling startScan again should be a no-op due to guard clause
            viewModel.startScan()
            expectNoEvents()
        }
    }
}
