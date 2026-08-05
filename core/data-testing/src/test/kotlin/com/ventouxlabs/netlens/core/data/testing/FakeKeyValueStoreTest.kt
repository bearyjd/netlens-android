package com.ventouxlabs.netlens.core.data.testing

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins the blank handling, the one part of this fake that could drift weaker than
 * `EncryptedKeyValueStore`. Three identical private copies existed before it moved here; the
 * naive version stores `""` and reads it back as a present-but-empty value, at which point a
 * test asserting "unset" passes against a fake that production would have failed.
 */
class FakeKeyValueStoreTest {

    @Test
    fun `a stored value reads back`() = runTest {
        val store = FakeKeyValueStore()
        store.putString("k", "v")

        assertEquals("v", store.getString("k"))
    }

    @Test
    fun `an unset key is null`() = runTest {
        assertNull(FakeKeyValueStore().getString("missing"))
    }

    @Test
    fun `writing null removes the key`() = runTest {
        val store = FakeKeyValueStore()
        store.putString("k", "v")
        store.putString("k", null)

        assertNull(store.getString("k"))
    }

    @Test
    fun `writing blank removes the key rather than storing an empty string`() = runTest {
        val store = FakeKeyValueStore()
        store.putString("k", "v")
        store.putString("k", "   ")

        assertNull(store.getString("k"))
    }

    @Test
    fun `a blank value never reads back as present`() = runTest {
        // The weak-copy behaviour asserted as a non-behaviour: if this ever returns "  ",
        // the fake has become looser than the store it stands in for.
        val store = FakeKeyValueStore()
        store.putString("k", "  ")

        assertNull(store.getString("k"))
    }
}
