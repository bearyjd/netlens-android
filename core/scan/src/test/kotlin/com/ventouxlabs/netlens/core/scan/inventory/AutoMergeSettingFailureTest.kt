package com.ventouxlabs.netlens.core.scan.inventory

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.ventouxlabs.netlens.core.data.preferences.UserPreferencesRepository
import com.ventouxlabs.netlens.core.data.testing.FakeKeyValueStore
import com.ventouxlabs.netlens.core.scan.DefaultAutoMergeSetting
import com.ventouxlabs.netlens.core.scan.RecordingNewDeviceNotifier
import com.ventouxlabs.netlens.core.scan.identity.IdentityHarness
import com.ventouxlabs.netlens.core.scan.identity.MAC1
import com.ventouxlabs.netlens.core.scan.identity.lan
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** A preference read that fails must not abort a scan: the safe direction is the legacy path (off). */
class AutoMergeSettingFailureTest {
    private val reads = AtomicInteger()
    private val broken = AtomicBoolean(false)
    private val store = object : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow {
            reads.incrementAndGet()
            if (broken.get()) throw IllegalStateException("datastore unavailable")
            emit(emptyPreferences())
        }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            throw IllegalStateException("datastore unavailable")
    }
    private val setting = DefaultAutoMergeSetting(UserPreferencesRepository(store, FakeKeyValueStore())).also {
        // The repository's init reads the store once on another thread; let it finish before breaking the store.
        while (reads.get() < 1) Thread.sleep(1)
        broken.set(true)
    }

    @Test
    fun `a failing preference read means off`() = runTest {
        assertFalse(setting.isEnabled())
    }

    @Test
    fun `persistScan still completes on the legacy path when the preference cannot be read`() = runTest {
        val h = IdentityHarness()
        val repo = h.fakes.repository(RecordingNewDeviceNotifier(), autoMerge = false, autoMergeSetting = setting)

        repo.persistScan(listOf(lan("10.0.0.3", MAC1)), networkId = null)

        assertEquals(1, h.known.allDevices.size)
    }
}
