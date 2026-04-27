package com.ventoux.netlens.feature.wol

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
import com.ventoux.netlens.core.data.dao.WolHistoryDao
import com.ventoux.netlens.core.data.model.WolHistoryEntry
import com.ventoux.netlens.core.data.model.WolTarget
import com.ventoux.netlens.feature.wol.dao.FakeWolTargetDao
import com.ventoux.netlens.feature.wol.engine.FakeWolSender
import com.ventoux.netlens.feature.wol.model.WolUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalCoroutinesApi::class)
class WolViewModelTest {

    private lateinit var dao: FakeWolTargetDao
    private lateinit var sender: FakeWolSender
    private lateinit var viewModel: WolViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dao = FakeWolTargetDao()
        sender = FakeWolSender()
        viewModel = WolViewModel(sender, dao, FakeWolHistoryDao())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has defaults`() = runTest {
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(emptyList<WolTarget>(), state.savedTargets)
            assertEquals("", state.macInput)
            assertEquals("255.255.255.255", state.broadcastIp)
            assertEquals(9, state.port)
            assertNull(state.lastSentStatus)
            assertFalse(state.showAddDialog)
            assertEquals("", state.addLabel)
            assertEquals("", state.addMac)
            assertNull(state.editingTarget)
            assertNull(state.error)
        }
    }

    @Test
    fun `onMacInputChanged updates macInput with formatted MAC`() = runTest {
        viewModel.state.test {
            awaitItem() // initial

            viewModel.onMacInputChanged("aabbccddeeff")

            val updated = awaitItem()
            assertEquals("AA:BB:CC:DD:EE:FF", updated.macInput)
        }
    }

    @Test
    fun `onMacInputChanged formats partial input`() = runTest {
        viewModel.state.test {
            awaitItem() // initial

            viewModel.onMacInputChanged("aabb")

            val updated = awaitItem()
            assertEquals("AA:BB", updated.macInput)
        }
    }

    @Test
    fun `onBroadcastIpChanged updates broadcastIp`() = runTest {
        viewModel.state.test {
            awaitItem() // initial

            viewModel.onBroadcastIpChanged("192.168.1.255")

            val updated = awaitItem()
            assertEquals("192.168.1.255", updated.broadcastIp)
        }
    }

    @Test
    fun `onPortChanged updates port`() = runTest {
        viewModel.state.test {
            awaitItem() // initial

            viewModel.onPortChanged(7)

            val updated = awaitItem()
            assertEquals(7, updated.port)
        }
    }

    @Test
    fun `sendWol success shows status message`() = runTest {
        sender.result = Result.success(Unit)

        viewModel.state.test {
            awaitItem() // initial

            viewModel.sendWol("AA:BB:CC:DD:EE:FF", "255.255.255.255", 9)

            val withStatus = awaitItem()
            assertEquals("Magic packet sent to AA:BB:CC:DD:EE:FF", withStatus.lastSentStatus)
            assertNull(withStatus.error)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `sendWol failure shows error`() = runTest {
        sender.result = Result.failure(RuntimeException("Network unreachable"))

        viewModel.state.test {
            awaitItem() // initial

            viewModel.sendWol("AA:BB:CC:DD:EE:FF", "255.255.255.255", 9)

            val errorState = awaitItem()
            assertEquals("Network unreachable", errorState.error)
        }
    }

    @Test
    fun `saveTarget adds to DAO and hides dialog`() = runTest {
        viewModel.state.test {
            awaitItem() // initial

            viewModel.saveTarget("Router", "AA:BB:CC:DD:EE:FF", "192.168.1.255", 9)

            // We expect the dialog to be hidden and a target to appear
            // Consume remaining events and check final state
            val events = cancelAndConsumeRemainingEvents()
            val finalState = viewModel.state.value
            assertFalse(finalState.showAddDialog)
            assertEquals(1, finalState.savedTargets.size)
            assertEquals("Router", finalState.savedTargets[0].label)
            assertEquals("AA:BB:CC:DD:EE:FF", finalState.savedTargets[0].macAddress)
        }
    }

    @Test
    fun `deleteTarget removes from DAO`() = runTest {
        viewModel.state.test {
            awaitItem() // initial

            viewModel.saveTarget("Router", "AA:BB:CC:DD:EE:FF", "192.168.1.255", 9)

            // Consume until we have the target
            val events1 = cancelAndConsumeRemainingEvents()
        }

        val target = viewModel.state.value.savedTargets[0]

        viewModel.state.test {
            val current = awaitItem()
            assertEquals(1, current.savedTargets.size)

            viewModel.deleteTarget(target)

            val afterDelete = awaitItem()
            assertTrue(afterDelete.savedTargets.isEmpty())
        }
    }

    @Test
    fun `showAddDialog sets showAddDialog to true and clears fields`() = runTest {
        viewModel.state.test {
            awaitItem() // initial

            viewModel.showAddDialog()

            val updated = awaitItem()
            assertTrue(updated.showAddDialog)
            assertEquals("", updated.addLabel)
            assertEquals("", updated.addMac)
            assertNull(updated.editingTarget)
        }
    }

    @Test
    fun `hideAddDialog sets showAddDialog to false and clears fields`() = runTest {
        viewModel.showAddDialog()

        viewModel.state.test {
            val current = awaitItem()
            assertTrue(current.showAddDialog)

            viewModel.hideAddDialog()

            val hidden = awaitItem()
            assertFalse(hidden.showAddDialog)
            assertEquals("", hidden.addLabel)
            assertEquals("", hidden.addMac)
            assertNull(hidden.editingTarget)
        }
    }

    @Test
    fun `editTarget opens dialog with target data`() = runTest {
        val target = WolTarget(
            id = 1,
            label = "Router",
            macAddress = "AA:BB:CC:DD:EE:FF",
            broadcastIp = "192.168.1.255",
            port = 9,
        )

        viewModel.state.test {
            awaitItem() // initial

            viewModel.editTarget(target)

            val editing = awaitItem()
            assertTrue(editing.showAddDialog)
            assertEquals("Router", editing.addLabel)
            assertEquals("AA:BB:CC:DD:EE:FF", editing.addMac)
            assertEquals(target, editing.editingTarget)
        }
    }

    @Test
    fun `clearError clears error`() = runTest {
        sender.result = Result.failure(RuntimeException("fail"))
        viewModel.sendWol("AA:BB:CC:DD:EE:FF", "255.255.255.255", 9)

        viewModel.state.test {
            val current = awaitItem()
            assertEquals("fail", current.error)

            viewModel.clearError()

            val cleared = awaitItem()
            assertNull(cleared.error)
        }
    }

    @Test
    fun `clearStatus clears lastSentStatus`() = runTest {
        sender.result = Result.success(Unit)
        viewModel.sendWol("AA:BB:CC:DD:EE:FF", "255.255.255.255", 9)

        viewModel.state.test {
            val current = awaitItem()
            assertEquals("Magic packet sent to AA:BB:CC:DD:EE:FF", current.lastSentStatus)

            viewModel.clearStatus()

            val cleared = awaitItem()
            assertNull(cleared.lastSentStatus)
        }
    }

    @Test
    fun `sendWolToTarget delegates to sendWol with target fields`() = runTest {
        sender.result = Result.success(Unit)

        val target = WolTarget(
            id = 1,
            label = "Router",
            macAddress = "11:22:33:44:55:66",
            broadcastIp = "10.0.0.255",
            port = 7,
        )

        viewModel.state.test {
            awaitItem() // initial

            viewModel.sendWolToTarget(target)

            val withStatus = awaitItem()
            assertEquals("Magic packet sent to 11:22:33:44:55:66", withStatus.lastSentStatus)
            cancelAndConsumeRemainingEvents()
        }
    }
}

private class FakeWolHistoryDao : WolHistoryDao {
    override fun getRecent(limit: Int): Flow<List<WolHistoryEntry>> = flowOf(emptyList())
    override fun search(query: String, limit: Int): Flow<List<WolHistoryEntry>> = flowOf(emptyList())
    override suspend fun getById(id: Long): WolHistoryEntry? = null
    override suspend fun insert(entry: WolHistoryEntry) {}
    override suspend fun deleteById(id: Long) {}
    override suspend fun deleteOlderThan(before: Long) {}
    override suspend fun deleteAll() {}
}
