package com.ventouxlabs.netlens.feature.devices

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity
import com.ventouxlabs.netlens.core.data.model.DeviceMergeDecisionEntity
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.core.data.secure.KeyValueStore
import com.ventouxlabs.netlens.core.data.testing.FakeKnownDeviceDao
import com.ventouxlabs.netlens.core.network.DisplayText
import com.ventouxlabs.netlens.core.scan.DeviceInventoryRepository
import com.ventouxlabs.netlens.core.scan.NewDeviceNotifier
import com.ventouxlabs.netlens.core.scan.identity.MergeSuggestion
import com.ventouxlabs.netlens.core.scan.network.FakeNetworkIdentity
import com.ventouxlabs.netlens.core.scan.testing.InventoryFakes
import com.ventouxlabs.netlens.feature.devices.model.UserField
import com.ventouxlabs.netlens.feature.devices.model.buildMergeSuggestionUi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Drives [DevicesViewModel] against the real [DeviceInventoryRepository] built on the shared
 * core:scan-testing fakes, so the repository's own refusal rules apply. Rows are seeded directly
 * and `SUGGESTED` decisions written straight into the fake decision table, skipping the resolver.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DevicesViewModelSuggestionsTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var fakes: InventoryFakes
    private lateinit var spy: SpyInventory
    private lateinit var viewModel: DevicesViewModel
    private val viewModelStore = ViewModelStore()
    private lateinit var dataStoreScope: CoroutineScope

    /** Records what the ViewModel sends; [beforeAccept]/[forceFailure] let a test change the world mid-call. */
    private class SpyInventory(private val real: DeviceInventoryRepository) : DeviceInventoryRepository by real {
        val acceptCalls = mutableListOf<Pair<MergeSuggestion.BetweenRows, Boolean?>>()
        val rejectCalls = mutableListOf<MergeSuggestion.BetweenRows>()
        var beforeAccept: suspend () -> Unit = {}
        var refuseAccept = false
        var throwOnAccept = false

        override suspend fun acceptSuggestion(pair: MergeSuggestion.BetweenRows, isKnown: Boolean?, expectedSurvivorId: Long, previewHash: Int): Boolean {
            acceptCalls += pair to isKnown
            beforeAccept()
            if (throwOnAccept) error("boom")
            return if (refuseAccept) false else real.acceptSuggestion(pair, isKnown, expectedSurvivorId, previewHash)
        }

        override suspend fun rejectSuggestion(pair: MergeSuggestion.BetweenRows): Boolean {
            rejectCalls += pair
            return real.rejectSuggestion(pair)
        }
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val known = FakeKnownDeviceDao()
        fakes = InventoryFakes(known)
        spy = SpyInventory(
            fakes.repository(
                notifier = object : NewDeviceNotifier {
                    override fun createChannel() = Unit
                    override fun notify(device: KnownDeviceEntity) = Unit
                },
                autoMerge = false,
            ),
        )
        dataStoreScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tempDir, "suggestion_prefs.preferences_pb") },
        )
        val keyValueStore = object : KeyValueStore {
            private val map = mutableMapOf<String, String>()
            override fun getString(key: String): String? = map[key]?.takeIf { it.isNotBlank() }
            override fun putString(key: String, value: String?) {
                if (value.isNullOrBlank()) map.remove(key) else map[key] = value
            }
        }
        viewModel = DevicesViewModel(
            knownDeviceDao = known,
            watchedNetworkDao = FakeWatchedNetworkDao(),
            networkIdentity = FakeNetworkIdentity(),
            userPreferences = UserPreferencesRepository(dataStore, keyValueStore),
            inventory = spy,
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

    private fun row(
        id: Long,
        firstSeen: Long,
        isKnown: Boolean = false,
        hostname: String? = "host$id",
        customName: String? = null,
        notes: String? = null,
        tags: String? = null,
        location: String? = null,
        vendor: String? = null,
        mac: String? = "aa:bb:cc:dd:ee:0$id",
    ) = KnownDeviceEntity(
        id = id, macAddress = mac, hostname = hostname, ip = "192.168.1.$id", vendor = vendor, firstSeen = firstSeen, lastSeen = firstSeen,
        isKnown = isKnown, customName = customName, notes = notes, tags = tags, location = location,
    )

    private suspend fun seedPair(a: KnownDeviceEntity, b: KnownDeviceEntity, reasons: String = "model,hostname") {
        fakes.known.seed(a, b)
        fakes.decisions.suggest(DeviceMergeDecisionEntity(minOf(a.id, b.id), maxOf(a.id, b.id), "SUGGESTED", 70, reasons, 0))
    }

    private fun only() = viewModel.uiState.value.suggestions.single()

    @Test
    fun `a pending pair becomes one suggestion whose count drives the banner`() = runTest {
        seedPair(row(1, firstSeen = 10), row(2, firstSeen = 20))

        val s = only()
        assertEquals("suggestion:1:2", s.key)
        assertEquals(1, viewModel.uiState.value.suggestions.size)
    }

    @Test
    fun `hostile names are flattened to literal safe text`() = runTest {
        val rlo = Char(0x202E)
        seedPair(
            row(1, firstSeen = 10, customName = "nas\n192.168.1.1 (Router)${rlo}evil"),
            row(2, firstSeen = 20, customName = null, hostname = "a\r\nb\tc"),
        )

        assertEquals("nas 192.168.1.1 (Router) evil", only().survivor.name)
        assertEquals("a b c", only().loser.name)
    }

    @Test
    fun `a name cannot forge a second line of trust text`() = runTest {
        seedPair(row(1, firstSeen = 10, customName = "nas\nTrusted (known)"), row(2, firstSeen = 20))

        assertEquals("nas Trusted (known)", only().survivor.name)
    }

    @Test
    fun `every bidi and invisible format character becomes a space`() = runTest {
        val specials = (0x202A..0x202E) + (0x2066..0x2069) + listOf(0x200B, 0x200C, 0x200D, 0xFEFF, 0x2028, 0x2029, 0x200E, 0x200F, 0x061C)
        specials.forEach { code ->
            val x = FakeKnownDeviceDao()
            x.seed(row(1, firstSeen = 10, customName = null, hostname = "nas${Char(code)}evil"), row(2, firstSeen = 20))
            val ui = buildMergeSuggestionUi(
                MergeSuggestion.BetweenRows(1, 2, 70, emptyList()), x.byId(1)!!, x.byId(2)!!, emptyList(),
            )
            assertEquals("nas evil", ui.survivor.name, "U+${code.toString(16)}")
        }
    }

    @Test
    fun `the display name falls back customName then hostname then vendor then ip`() = runTest {
        seedPair(row(1, firstSeen = 10, customName = "\n", hostname = "\t", vendor = "Acme"), row(2, firstSeen = 20, hostname = null, vendor = null))

        assertEquals("Acme", only().survivor.name)
        assertEquals("192.168.1.2", only().loser.name)
    }

    @Test
    fun `suggestions survive a search that hides both rows`() = runTest {
        seedPair(row(1, firstSeen = 10), row(2, firstSeen = 20))

        viewModel.setSearchQuery("no-device-matches-this")

        assertTrue(viewModel.uiState.value.devices.isEmpty())
        assertEquals(1, viewModel.uiState.value.suggestions.size)
    }

    @Test
    fun `open and close keep the suggestions and clear the message`() = runTest {
        seedPair(row(1, firstSeen = 10), row(2, firstSeen = 20))
        viewModel.openSuggestions()
        assertTrue(viewModel.uiState.value.suggestionsOpen)
        viewModel.setSearchQuery("x")
        assertTrue(viewModel.uiState.value.suggestionsOpen)

        viewModel.closeSuggestions()

        assertFalse(viewModel.uiState.value.suggestionsOpen)
        assertEquals(1, viewModel.uiState.value.suggestions.size)
    }

    @Test
    fun `accepting merges into the older row and the pair leaves the list`() = runTest {
        // The higher id is the older row, so the survivor is NOT simply the lower id.
        seedPair(row(1, firstSeen = 50), row(2, firstSeen = 10))
        val predicted = only().survivor.id

        viewModel.acceptSuggestion(only().key, isKnown = null, confirmed = true)

        assertEquals(2L, predicted)
        assertNotNull(fakes.known.byId(predicted))
        assertNull(fakes.known.byId(1))
        assertTrue(viewModel.uiState.value.suggestions.isEmpty())
        assertNull(viewModel.uiState.value.suggestionMessage)
    }

    @Test
    fun `after a successful merge taps are ignored until the list has settled`() = runTest {
        seedPair(row(1, firstSeen = 10), row(2, firstSeen = 20))
        fakes.known.seed(row(3, firstSeen = 30), row(4, firstSeen = 40))
        fakes.decisions.suggest(DeviceMergeDecisionEntity(3, 4, "SUGGESTED", 60, "model", 0))
        val first = viewModel.uiState.value.suggestions.first { it.pair.deviceIdLow == 1L }
        val second = viewModel.uiState.value.suggestions.first { it.pair.deviceIdLow == 3L }

        viewModel.acceptSuggestion(first.key, isKnown = null, confirmed = true)
        viewModel.acceptSuggestion(second.key, isKnown = null, confirmed = true) // lands on the card that slid up

        assertEquals(first.key, viewModel.uiState.value.suggestionBusyKey)
        assertEquals(1, spy.acceptCalls.size)
        assertNotNull(fakes.known.byId(3))

        advanceTimeBy(DevicesViewModel.SETTLE_MS + 1)
        runCurrent()
        assertNull(viewModel.uiState.value.suggestionBusyKey)
        viewModel.acceptSuggestion(second.key, isKnown = null, confirmed = true)
        assertEquals(2, spy.acceptCalls.size)
    }

    @Test
    fun `while an action is suspended every other accept and reject is ignored`() = runTest {
        seedPair(row(1, firstSeen = 10), row(2, firstSeen = 20))
        fakes.known.seed(row(3, firstSeen = 30), row(4, firstSeen = 40))
        fakes.decisions.suggest(DeviceMergeDecisionEntity(3, 4, "SUGGESTED", 60, "model", 0))
        val first = viewModel.uiState.value.suggestions.first { it.pair.deviceIdLow == 1L }
        val other = viewModel.uiState.value.suggestions.first { it.pair.deviceIdLow == 3L }
        val release = CompletableDeferred<Unit>()
        spy.beforeAccept = { release.await() }

        viewModel.acceptSuggestion(first.key, isKnown = null, confirmed = true)
        assertEquals(first.key, viewModel.uiState.value.suggestionBusyKey) // set synchronously
        viewModel.acceptSuggestion(first.key, isKnown = null, confirmed = true)
        viewModel.acceptSuggestion(other.key, isKnown = null, confirmed = true)
        viewModel.rejectSuggestion(first.key)
        viewModel.rejectSuggestion(other.key)

        assertEquals(1, spy.acceptCalls.size)
        assertTrue(spy.rejectCalls.isEmpty())

        release.complete(Unit)
        advanceTimeBy(DevicesViewModel.SETTLE_MS + 1)
        runCurrent()
        assertNull(viewModel.uiState.value.suggestionBusyKey)
        assertEquals(1, spy.acceptCalls.size)
    }

    @Test
    fun `a field changed between preview and accept is refused and the list refreshed`() = runTest {
        seedPair(row(1, firstSeen = 10, notes = "mine"), row(2, firstSeen = 20))
        val shown = only()
        spy.beforeAccept = { fakes.known.updateUserDetails(2, null, null, "added after preview", null, null, null, null) }

        viewModel.acceptSuggestion(shown.key, isKnown = null, confirmed = true)

        assertEquals(R.string.devices_suggestion_merge_refused, viewModel.uiState.value.suggestionMessage)
        assertNotNull(fakes.known.byId(1))
        assertNotNull(fakes.known.byId(2))
        assertTrue(only().previewHash != shown.previewHash, "the list now reflects the changed row")
    }

    @Test
    fun `an identifier added between preview and accept is refused`() = runTest {
        seedPair(row(1, firstSeen = 10), row(2, firstSeen = 20))
        val shown = only()
        spy.beforeAccept = {
            fakes.identifiers.insertIfNew(
                DeviceIdentifierEntity(deviceId = 2, kind = "UPNP_UDN", value = "uuid:late", scopeKey = "", source = "SSDP", firstSeen = 0, lastSeen = 0),
            )
        }

        viewModel.acceptSuggestion(shown.key, isKnown = null, confirmed = true)

        assertEquals(R.string.devices_suggestion_merge_refused, viewModel.uiState.value.suggestionMessage)
        assertNotNull(fakes.known.byId(2))
        assertEquals(1, only().movedIdentifierCount)
    }

    @Test
    fun `dropping a value needs confirmation and the confirmed merge goes through`() = runTest {
        seedPair(row(1, firstSeen = 10, notes = "mine"), row(2, firstSeen = 20, notes = "theirs"))
        assertEquals(listOf(UserField.NOTES), only().droppedFields)
        assertEquals("theirs", only().droppedValues.single().value)

        viewModel.acceptSuggestion(only().key, isKnown = null, confirmed = false)

        assertTrue(spy.acceptCalls.isEmpty())
        assertEquals(R.string.devices_suggestion_confirm_required, viewModel.uiState.value.suggestionMessage)
        viewModel.acceptSuggestion(only().key, isKnown = null, confirmed = true)
        assertEquals(1, spy.acceptCalls.size)
        assertNull(fakes.known.byId(2))
    }

    @Test
    fun `differing trust needs confirmation even with an explicit choice`() = runTest {
        seedPair(row(1, firstSeen = 10, isKnown = true), row(2, firstSeen = 20, isKnown = false))

        viewModel.acceptSuggestion(only().key, isKnown = true, confirmed = false)

        assertTrue(spy.acceptCalls.isEmpty())
        assertEquals(R.string.devices_suggestion_confirm_required, viewModel.uiState.value.suggestionMessage)
    }

    @Test
    fun `a merge with nothing to lose and agreeing trust needs no confirmation`() = runTest {
        seedPair(row(1, firstSeen = 10), row(2, firstSeen = 20))
        assertFalse(only().requiresConfirmation)

        viewModel.acceptSuggestion(only().key, isKnown = null, confirmed = false)

        assertEquals(1, spy.acceptCalls.size)
    }

    @Test
    fun `differing trust with differing MACs is flagged as a trust escalation`() = runTest {
        seedPair(row(1, firstSeen = 10, isKnown = true, mac = "aa:aa:aa:aa:aa:01"), row(2, firstSeen = 20, isKnown = false, mac = "bb:bb:bb:bb:bb:02"))
        assertTrue(only().escalatesTrust)
    }

    @Test
    fun `an explicit trust of either value is exactly what reaches the repository`() = runTest {
        for (choice in listOf(true, false)) {
            val (a, b) = if (choice) 10L to 11L else 20L to 21L
            fakes.known.seed(row(a, firstSeen = 10, isKnown = true), row(b, firstSeen = 20, isKnown = false))
            fakes.decisions.suggest(DeviceMergeDecisionEntity(a, b, "SUGGESTED", 70, "model", 0))
            viewModel.acceptSuggestion("suggestion:$a:$b", isKnown = choice, confirmed = true)
            assertEquals(choice, spy.acceptCalls.last().second)
            assertEquals(choice, fakes.known.byId(a)!!.isKnown)
            advanceTimeBy(DevicesViewModel.SETTLE_MS + 1)
            runCurrent()
        }
    }

    @Test
    fun `the merge preview matches what the repository really keeps, fills and drops`() = runTest {
        seedPair(
            row(1, firstSeen = 10, customName = "Mine", notes = "survivor note", tags = null),
            row(2, firstSeen = 20, customName = "Other", notes = "loser note", tags = "a,b", location = "Garage"),
        )
        val before = only()
        assertEquals(listOf(UserField.NAME, UserField.NOTES), before.keptFields)
        assertEquals(listOf(UserField.TAGS, UserField.LOCATION), before.filledFields)
        assertEquals(listOf(UserField.NAME, UserField.NOTES), before.droppedFields)

        viewModel.acceptSuggestion(before.key, isKnown = null, confirmed = true)

        val merged = fakes.known.byId(before.survivor.id)!!
        assertEquals("Mine", merged.customName)
        assertEquals("survivor note", merged.notes)
        assertEquals("a,b", merged.tags)
        assertEquals("Garage", merged.location)
    }

    @Test
    fun `identifiers that will move are the loser's, capped for display with the full count`() = runTest {
        seedPair(row(1, firstSeen = 10), row(2, firstSeen = 20))
        repeat(7) { i ->
            fakes.identifiers.insertIfNew(
                DeviceIdentifierEntity(deviceId = 2, kind = "UPNP_UDN", value = "uuid:$i", scopeKey = "", source = "SSDP", firstSeen = 0, lastSeen = 0),
            )
        }
        fakes.decisions.updateSuggestion(1, 2, 71, "model") // re-emit so the identifiers seeded above are read

        val s = only()
        assertEquals(7, s.movedIdentifierCount)
        assertEquals(5, s.movedIdentifiers.size)
        assertEquals("UPNP_UDN", s.movedIdentifiers.first().kind)
    }

    @Test
    fun `differing trust is never sent without an explicit choice`() = runTest {
        seedPair(row(1, firstSeen = 10, isKnown = true), row(2, firstSeen = 20, isKnown = false))
        assertTrue(only().trustDiffers)

        viewModel.acceptSuggestion(only().key, isKnown = null, confirmed = true)

        assertTrue(spy.acceptCalls.isEmpty(), "no merge request may be sent without the user's trust choice")
        assertNotNull(fakes.known.byId(1))
        assertNotNull(fakes.known.byId(2))
        assertEquals(R.string.devices_suggestion_choose_trust, viewModel.uiState.value.suggestionMessage)
    }

    @Test
    fun `an explicit trust choice is passed through, even against the survivor's own trust`() = runTest {
        seedPair(row(1, firstSeen = 10, isKnown = true), row(2, firstSeen = 20, isKnown = false))

        viewModel.acceptSuggestion(only().key, isKnown = false, confirmed = true)

        assertEquals(false, spy.acceptCalls.single().second)
        assertEquals(false, fakes.known.byId(1)!!.isKnown)
        assertNull(fakes.known.byId(2))
    }

    @Test
    fun `agreeing trust needs no choice and sends none`() = runTest {
        seedPair(row(1, firstSeen = 10, isKnown = true), row(2, firstSeen = 20, isKnown = true))

        viewModel.acceptSuggestion(only().key, isKnown = false, confirmed = true) // a stray value must not flip trust

        assertNull(spy.acceptCalls.single().second)
        assertEquals(true, fakes.known.byId(1)!!.isKnown)
    }

    @Test
    fun `a refused accept shows a message, keeps both rows and clears the busy flag`() = runTest {
        // A third row decided DISTINCT against the loser: merging would cascade that decision away.
        seedPair(row(1, firstSeen = 10), row(2, firstSeen = 20))
        fakes.known.seed(row(3, firstSeen = 30))
        fakes.decisions.setDistinct(2, 3, 0)

        viewModel.acceptSuggestion(only().key, isKnown = null, confirmed = true)

        assertEquals(R.string.devices_suggestion_merge_refused, viewModel.uiState.value.suggestionMessage)
        assertNull(viewModel.uiState.value.suggestionBusyKey)
        assertNotNull(fakes.known.byId(1))
        assertNotNull(fakes.known.byId(2))
        assertEquals(1, viewModel.uiState.value.suggestions.size)
    }

    @Test
    fun `a refused accept refreshes the list from the database`() = runTest {
        seedPair(row(1, firstSeen = 10), row(2, firstSeen = 20))
        assertEquals(0, only().movedIdentifierCount)
        spy.refuseAccept = true
        // Changes data the pair list does not observe: only a forced refresh shows it.
        spy.beforeAccept = {
            fakes.identifiers.insertIfNew(
                DeviceIdentifierEntity(deviceId = 2, kind = "UPNP_UDN", value = "uuid:new", scopeKey = "", source = "SSDP", firstSeen = 0, lastSeen = 0),
            )
        }

        viewModel.acceptSuggestion(only().key, isKnown = null, confirmed = true)

        assertEquals(R.string.devices_suggestion_merge_refused, viewModel.uiState.value.suggestionMessage)
        assertEquals(1, only().movedIdentifierCount)
    }

    @Test
    fun `an exception from the repository is a refusal, not a crash`() = runTest {
        seedPair(row(1, firstSeen = 10), row(2, firstSeen = 20))
        spy.throwOnAccept = true

        viewModel.acceptSuggestion(only().key, isKnown = null, confirmed = true)

        assertEquals(R.string.devices_suggestion_merge_refused, viewModel.uiState.value.suggestionMessage)
        assertNull(viewModel.uiState.value.suggestionBusyKey)
    }

    @Test
    fun `accepting a pair that is no longer listed is a refusal`() = runTest {
        viewModel.acceptSuggestion("suggestion:9:10", isKnown = null, confirmed = true)

        assertEquals(R.string.devices_suggestion_merge_refused, viewModel.uiState.value.suggestionMessage)
        assertTrue(spy.acceptCalls.isEmpty())
    }

    @Test
    fun `not the same writes DISTINCT and drops the pair, keeping both rows`() = runTest {
        seedPair(row(1, firstSeen = 10), row(2, firstSeen = 20))

        viewModel.rejectSuggestion(only().key)

        assertEquals("DISTINCT", fakes.decisions.get(1, 2)!!.decision)
        assertNotNull(fakes.known.byId(1))
        assertNotNull(fakes.known.byId(2))
        assertTrue(viewModel.uiState.value.suggestions.isEmpty())
        assertNull(viewModel.uiState.value.suggestionMessage)
    }

    @Test
    fun `rejecting a stale pair shows a message and writes nothing`() = runTest {
        viewModel.rejectSuggestion("suggestion:1:2")

        assertEquals(R.string.devices_suggestion_reject_refused, viewModel.uiState.value.suggestionMessage)
        assertTrue(spy.rejectCalls.isEmpty())
    }

    @Test
    fun `a pair whose row is gone is dropped from the list`() = runTest {
        fakes.known.seed(row(1, firstSeen = 10))
        fakes.decisions.suggest(DeviceMergeDecisionEntity(1, 2, "SUGGESTED", 70, "model", 0))

        assertTrue(viewModel.uiState.value.suggestions.isEmpty())
    }
}
