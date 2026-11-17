package com.ventouxlabs.netlens.feature.devices

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import com.ventouxlabs.netlens.core.data.dao.KnownDeviceDao
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.model.SaveOutcome
import com.ventouxlabs.netlens.core.data.model.MergeKind
import com.ventouxlabs.netlens.core.data.model.UserDetails
import com.ventouxlabs.netlens.core.data.model.userDetails
import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.core.data.testing.FakeDeviceIdentifierDao
import com.ventouxlabs.netlens.core.data.testing.FakeDeviceIdentityDao
import com.ventouxlabs.netlens.core.data.testing.FakeDeviceMergeDecisionDao
import com.ventouxlabs.netlens.core.data.testing.FakeDeviceObservationDao
import com.ventouxlabs.netlens.core.data.testing.FakeDeviceSightingDao
import com.ventouxlabs.netlens.core.data.testing.FakeKeyValueStore
import com.ventouxlabs.netlens.core.data.testing.FakeKnownDeviceDao
import com.ventouxlabs.netlens.core.scan.network.FakeNetworkIdentity
import com.ventouxlabs.netlens.feature.devices.model.DeviceDetailsEdit
import com.ventouxlabs.netlens.feature.devices.model.DiscardedEdit
import com.ventouxlabs.netlens.feature.devices.model.UserField
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The shared fake with a gate in front of the guarded save, so a test can hold a save in flight,
 * and a switch that makes the trust compare-and-set miss. Everything else is the fake itself.
 */
private class GatedKnownDeviceDao(private val fake: FakeKnownDeviceDao) : KnownDeviceDao by fake {
    var gate: CompletableDeferred<Unit>? = null
    var saveCalls = 0
    var casMiss = false
    var failLookups = false

    override suspend fun getById(id: Long): KnownDeviceEntity? {
        if (failLookups) throw IllegalStateException("database closed")
        return fake.getById(id)
    }

    override suspend fun saveUserDetails(id: Long, base: UserDetails, edit: UserDetails): SaveOutcome {
        saveCalls += 1
        gate?.await()
        return fake.saveUserDetails(id, base, edit)
    }

    override suspend fun setKnownIf(id: Long, isKnown: Boolean, expected: Boolean): Int =
        if (casMiss) 0 else fake.setKnownIf(id, isKnown, expected)
}

/** The edit-vs-merge guard as the detail sheet drives it: every [com.ventouxlabs.netlens.core.data.model.SaveOutcome]. */
@OptIn(ExperimentalCoroutinesApi::class)
class DevicesViewModelGuardedSaveTest {

    @TempDir
    lateinit var tempDir: File

    private val known = FakeKnownDeviceDao()
    private val identity = FakeDeviceIdentityDao(
        known, FakeDeviceIdentifierDao(), FakeDeviceObservationDao(), FakeDeviceSightingDao(), FakeDeviceMergeDecisionDao(),
    )
    private val gated = GatedKnownDeviceDao(known)
    private lateinit var viewModel: DevicesViewModel
    private val viewModelStore = ViewModelStore()
    private lateinit var dataStoreScope: CoroutineScope

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dataStoreScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tempDir, "guard_prefs.preferences_pb") },
        )
        viewModel = DevicesViewModel(
            knownDeviceDao = gated,
            watchedNetworkDao = FakeWatchedNetworkDao(),
            networkIdentity = FakeNetworkIdentity(),
            userPreferences = UserPreferencesRepository(dataStore, FakeKeyValueStore()),
            inventory = RecordingDeviceInventoryRepository(),
            watchScheduler = RecordingWatchScheduler(),
            defaultDispatcher = UnconfinedTestDispatcher(),
        )
        viewModelStore.put("devices", viewModel)
    }

    @AfterEach
    fun tearDown() {
        viewModelStore.clear()
        dataStoreScope.cancel()
        Dispatchers.resetMain()
    }

    private fun row(id: Long, hostname: String, details: UserDetails = UserDetails()) = KnownDeviceEntity(
        id = id, macAddress = null, hostname = hostname, ip = "192.168.1.$id", vendor = null,
        customName = details.customName, tags = details.tags, notes = details.notes, location = details.location,
    )

    /** Opens the editor the way the sheet does: base = the stored row at that moment. */
    private fun open(id: Long): UserDetails {
        viewModel.selectDevice(id)
        return known.byId(id)!!.userDetails()
    }

    private val state get() = viewModel.uiState.value

    @Test
    fun `a clean save writes only the changed field and closes the editor`() = runTest {
        known.seed(row(1, "nas", UserDetails(notes = "  ")))
        val base = open(1)

        viewModel.saveDetails(1, base, DeviceDetailsEdit.from(base).copy(customName = "NAS"))

        assertEquals(UserDetails(customName = "NAS", notes = "  "), known.byId(1)?.userDetails())
        assertNull(state.selectedDeviceId)
        assertFalse(state.detailSaving)
        assertNull(state.detailNotice)
    }

    @Test
    fun `saving to a merged row writes nothing, opens the survivor with the change applied, and a second save lands`() = runTest {
        known.seed(row(1, "survivor", UserDetails(customName = "Kept", location = "Office")), row(2, "loser"))
        val base = open(2)
        identity.mergeDevices(1, 2, isKnown = false, kind = MergeKind.USER)
        val survivorBefore = known.byId(1)

        viewModel.saveDetails(2, base, DeviceDetailsEdit.from(base).copy(notes = "typed while merging"))

        assertEquals(survivorBefore, known.byId(1), "never written to the survivor automatically")
        assertEquals(1L, state.selectedDeviceId)
        assertEquals(1L, state.selectedDevice?.id)
        val draft = state.detailDraft!!
        assertEquals(1L, draft.deviceId)
        assertEquals(survivorBefore!!.userDetails(), draft.base)
        assertEquals(DeviceDetailsEdit(customName = "Kept", location = "Office", notes = "typed while merging"), draft.edit)
        assertEquals(R.string.devices_detail_merged, state.detailNotice?.message)
        assertTrue(state.detailNotice?.arg.orEmpty().contains("Kept"))

        viewModel.saveDetails(1, draft.base, draft.edit)

        assertEquals(UserDetails(customName = "Kept", location = "Office", notes = "typed while merging"), known.byId(1)?.userDetails())
        assertNull(state.selectedDeviceId)
    }

    @Test
    fun `a stale save after an automatic fill conflicts, keeps the fill, and offers the change for review`() = runTest {
        known.seed(row(1, "survivor"), row(2, "loser", UserDetails(notes = "their notes")))
        val base = open(1)
        identity.mergeDevices(1, 2, isKnown = null, kind = MergeKind.AUTO)

        viewModel.saveDetails(1, base, DeviceDetailsEdit.from(base).copy(notes = "my notes"))

        assertEquals("their notes", known.byId(1)?.notes)
        assertEquals(1L, state.selectedDeviceId)
        assertEquals(R.string.devices_detail_changed, state.detailNotice?.message)
        assertEquals(UserDetails(notes = "their notes"), state.detailDraft?.base)
        // The conflicting field keeps the row's value; what was typed is listed, not lost silently.
        assertEquals("their notes", state.detailDraft?.edit?.notes)
        assertEquals(listOf(DiscardedEdit(UserField.NOTES, "my notes")), state.detailNotice?.discarded)
    }

    @Test
    fun `a merged survivor keeps a value it already holds and lists the typed one, while a still-empty field takes the edit`() = runTest {
        known.seed(row(1, "survivor", UserDetails(customName = "Kept")), row(2, "loser"))
        val base = open(2)
        identity.mergeDevices(1, 2, isKnown = false, kind = MergeKind.USER)

        viewModel.saveDetails(2, base, DeviceDetailsEdit.from(base).copy(customName = "Mine\nline", location = "Den"))

        assertEquals(DeviceDetailsEdit(customName = "Kept", location = "Den"), state.detailDraft?.edit)
        assertEquals(listOf(DiscardedEdit(UserField.NAME, "Mine line")), state.detailNotice?.discarded)
    }

    private fun assertDroppedAfterReopen(setUpOutcome: suspend () -> Unit) = runTest {
        known.seed(row(1, "survivor"), row(2, "loser", UserDetails(notes = "their notes")), row(3, "other"))
        val base = open(1)
        setUpOutcome()
        gated.gate = CompletableDeferred()
        viewModel.saveDetails(1, base, DeviceDetailsEdit.from(base).copy(notes = "typed"))

        viewModel.selectDevice(null)
        viewModel.selectDevice(3)
        gated.gate?.complete(Unit)

        assertEquals(3L, state.selectedDeviceId, "the new editor session is not closed or hijacked")
        assertEquals(3L, state.selectedDevice?.id)
        assertNull(state.detailDraft)
        assertNull(state.detailNotice)
        assertFalse(state.detailSaving)
    }

    @Test
    fun `a Saved outcome after the editor was reopened is dropped`() = assertDroppedAfterReopen {}

    @Test
    fun `a Merged outcome after the editor was reopened is dropped`() =
        assertDroppedAfterReopen { identity.mergeDevices(2, 1, isKnown = false, kind = MergeKind.USER) }

    @Test
    fun `a Conflict outcome after the editor was reopened is dropped`() =
        assertDroppedAfterReopen { identity.mergeDevices(1, 2, isKnown = null, kind = MergeKind.AUTO) }

    @Test
    fun `a Gone outcome after the editor was reopened is dropped`() = assertDroppedAfterReopen { known.delete(1) }

    @Test
    fun `a failed lookup while redirecting a save shows the save-failed notice and frees Save`() = runTest {
        known.seed(row(1, "survivor"), row(2, "loser", UserDetails(notes = "their notes")))
        val base = open(1)
        identity.mergeDevices(1, 2, isKnown = null, kind = MergeKind.AUTO)
        gated.failLookups = true

        viewModel.saveDetails(1, base, DeviceDetailsEdit.from(base).copy(notes = "my notes"))

        assertEquals(R.string.devices_detail_save_failed, state.detailNotice?.message)
        assertFalse(state.detailSaving)
        assertEquals(1L, state.selectedDeviceId)

        // The session is intact: the retry's outcome is applied, not dropped as stale.
        gated.failLookups = false
        viewModel.saveDetails(1, base, DeviceDetailsEdit.from(base).copy(notes = "my notes"))
        assertEquals(R.string.devices_detail_changed, state.detailNotice?.message)
        assertEquals(2, gated.saveCalls)
    }

    @Test
    fun `a second save while one is in flight is refused, even from a reopened editor`() = runTest {
        known.seed(row(1, "nas"))
        val base = open(1)
        gated.gate = CompletableDeferred()

        viewModel.saveDetails(1, base, DeviceDetailsEdit.from(base).copy(customName = "a"))
        viewModel.saveDetails(1, base, DeviceDetailsEdit.from(base).copy(customName = "b"))
        viewModel.selectDevice(null)
        val again = open(1)
        viewModel.saveDetails(1, again, DeviceDetailsEdit.from(again).copy(customName = "c"))
        assertTrue(state.detailSaving)
        gated.gate?.complete(Unit)

        assertEquals(1, gated.saveCalls)
        assertEquals("a", known.byId(1)?.customName)
        assertFalse(state.detailSaving)
    }

    @Test
    fun `a missed trust toggle is reported only in that row's open editor`() = runTest {
        known.seed(row(1, "nas"), row(2, "printer"))
        gated.casMiss = true

        viewModel.toggleKnown(1)
        assertNull(state.detailNotice, "no editor open: no notice")

        open(2)
        viewModel.toggleKnown(1)
        assertNull(state.detailNotice, "another row's editor: no notice")

        viewModel.toggleKnown(2)
        assertEquals(R.string.devices_detail_trust_changed, state.detailNotice?.message)
    }

    @Test
    fun `a save to a deleted row keeps the editor open and says it was not saved`() = runTest {
        known.seed(row(1, "nas"))
        val base = open(1)
        known.delete(1)

        viewModel.saveDetails(1, base, DeviceDetailsEdit.from(base).copy(customName = "x"))

        assertEquals(1L, state.selectedDeviceId)
        assertEquals(1L, state.selectedDevice?.id, "the last copy keeps the sheet up")
        assertEquals(R.string.devices_detail_gone, state.detailNotice?.message)
        assertNull(state.detailDraft)
        assertFalse(state.detailSaving)
    }

    @Test
    fun `when the open row is merged away the editor stays and says where it went`() = runTest {
        known.seed(row(1, "survivor"), row(2, "loser"))
        open(2)

        identity.mergeDevices(1, 2, isKnown = false, kind = MergeKind.USER)

        assertEquals(2L, state.selectedDevice?.id)
        assertEquals(R.string.devices_detail_vanished_merged, state.detailNotice?.message)
        assertTrue(state.detailNotice?.arg.orEmpty().contains("survivor"))
    }

    @Test
    fun `a search that hides the open row does not close or flag it`() = runTest {
        known.seed(row(1, "nas"), row(2, "printer"))
        open(1)

        viewModel.setSearchQuery("printer")

        assertEquals(1L, state.selectedDevice?.id)
        assertNull(state.detailNotice)
    }

    @Test
    fun `deleting from the editor closes it without a lost-save notice`() = runTest {
        known.seed(row(1, "nas"))
        open(1)

        viewModel.delete(1)

        assertNull(known.byId(1))
        assertNull(state.selectedDeviceId)
        assertNull(state.detailNotice)
    }

    @Test
    fun `a merged device name in the notice is flattened and bidi-isolated`() = runTest {
        known.seed(row(1, "evil\u202Ename\nrow"), row(2, "loser"))
        val base = open(2)
        identity.mergeDevices(1, 2, isKnown = false, kind = MergeKind.USER)

        viewModel.saveDetails(2, base, DeviceDetailsEdit.from(base).copy(customName = "x"))

        assertEquals("\u2068evilname row\u2069", state.detailNotice?.arg)
    }
}
