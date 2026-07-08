package com.ventouxlabs.netlens.feature.celltower

import app.cash.turbine.test
import com.ventouxlabs.netlens.feature.celltower.engine.CellTowerState
import com.ventouxlabs.netlens.feature.celltower.engine.FakeCellTowerReader
import com.ventouxlabs.netlens.feature.celltower.model.CellTowerInfo
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

@OptIn(ExperimentalCoroutinesApi::class)
class CellTowerViewModelTest {

    private lateinit var fakeReader: FakeCellTowerReader
    private lateinit var viewModel: CellTowerViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeReader = FakeCellTowerReader()
        viewModel = CellTowerViewModel(fakeReader)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has no permission and no towers`() = runTest {
        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.hasPermission)
            assertNull(state.connectedTower)
            assertTrue(state.neighborCells.isEmpty())
            assertFalse(state.isRefreshing)
            assertFalse(state.noCellular)
        }
    }

    @Test
    fun `onPermissionResult false does not start observing`() = runTest {
        viewModel.state.test {
            awaitItem() // initial
            viewModel.onPermissionResult(false)
            // hasPermission was already false, so the state is unchanged (StateFlow conflates
            // equal values) and observing never starts.
            expectNoEvents()
        }
        assertFalse(viewModel.state.value.hasPermission)
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun `onPermissionResult true starts observing`() = runTest {
        viewModel.state.test {
            awaitItem() // initial
            viewModel.onPermissionResult(true)
            val granted = awaitItem()
            assertTrue(granted.hasPermission)
            val observing = awaitItem()
            assertTrue(observing.isRefreshing)
        }
    }

    @Test
    fun `observed state with connected tower updates the ui state`() = runTest {
        val tower = testTower(cellId = "1001")
        viewModel.onPermissionResult(true)

        viewModel.state.test {
            var s = awaitItem()
            while (!s.isRefreshing) s = awaitItem()

            fakeReader.emit(CellTowerState(connected = tower, neighbors = emptyList()))
            var updated = awaitItem()
            while (updated.connectedTower == null) updated = awaitItem()

            assertEquals(tower, updated.connectedTower)
            assertFalse(updated.isRefreshing)
            assertFalse(updated.noCellular)
        }
    }

    @Test
    fun `observed empty state marks noCellular`() = runTest {
        viewModel.onPermissionResult(true)

        viewModel.state.test {
            var s = awaitItem()
            while (!s.isRefreshing) s = awaitItem()

            fakeReader.emit(CellTowerState(connected = null, neighbors = emptyList()))
            var updated = awaitItem()
            while (!updated.noCellular) updated = awaitItem()

            assertNull(updated.connectedTower)
            assertTrue(updated.noCellular)
        }
    }

    @Test
    fun `refresh without permission does nothing`() = runTest {
        viewModel.state.test {
            awaitItem() // initial
            viewModel.refresh()
            expectNoEvents()
        }
    }

    @Test
    fun `refresh with permission and no reading marks noCellular`() = runTest {
        viewModel.onPermissionResult(true)
        fakeReader.onceResult = null

        viewModel.state.test {
            var s = awaitItem()
            while (!s.isRefreshing) s = awaitItem()

            viewModel.refresh()
            var updated = awaitItem()
            while (!updated.noCellular) updated = awaitItem()
            assertFalse(updated.isRefreshing)
        }
    }

    @Test
    fun `refresh with permission and a reading populates towers`() = runTest {
        val connected = testTower(cellId = "2002")
        val neighbor = testTower(cellId = "2003")
        viewModel.onPermissionResult(true)
        fakeReader.onceResult = CellTowerState(connected = connected, neighbors = listOf(neighbor))

        viewModel.state.test {
            var s = awaitItem()
            while (!s.isRefreshing) s = awaitItem()

            viewModel.refresh()
            var updated = awaitItem()
            while (updated.connectedTower == null) updated = awaitItem()

            assertEquals(connected, updated.connectedTower)
            assertEquals(listOf(neighbor), updated.neighborCells)
        }
    }

    @Test
    fun `buildExportText includes connected and neighbor towers`() = runTest {
        val connected = testTower(cellId = "3001", operatorName = "CarrierX")
        val neighbor = testTower(cellId = "3002", operatorName = "CarrierX")
        viewModel.onPermissionResult(true)
        fakeReader.emit(CellTowerState(connected = connected, neighbors = listOf(neighbor)))

        viewModel.state.test {
            var s = awaitItem()
            while (s.connectedTower == null) s = awaitItem()
        }

        val text = viewModel.buildExportText()
        assertTrue(text.contains("Connected Tower:"))
        assertTrue(text.contains("CID: 3001"))
        assertTrue(text.contains("Neighbor Cells (1):"))
        assertTrue(text.contains("CID: 3002"))
    }

    @Test
    fun `buildExportText with no data still has a header`() {
        val text = viewModel.buildExportText()
        assertEquals("Cell Tower Info", text)
    }

    @Test
    fun `buildExportJson serializes towers as a list`() = runTest {
        val connected = testTower(cellId = "4001")
        viewModel.onPermissionResult(true)
        fakeReader.emit(CellTowerState(connected = connected, neighbors = emptyList()))

        viewModel.state.test {
            var s = awaitItem()
            while (s.connectedTower == null) s = awaitItem()
        }

        val json = viewModel.buildExportJson()
        assertTrue(json.contains("\"cellId\":\"4001\""))
    }

    private fun testTower(
        cellId: String,
        operatorName: String = "TestCarrier",
        isRegistered: Boolean = true,
    ) = CellTowerInfo(
        networkType = "LTE",
        operatorName = operatorName,
        cellId = cellId,
        tac = "10",
        band = "EARFCN 100",
        rsrp = -85,
        rsrq = -10,
        sinr = 15,
        rssi = null,
        isRegistered = isRegistered,
    )
}
