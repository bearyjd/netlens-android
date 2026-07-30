package com.ventouxlabs.netlens.core.scan.engine

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * A test double is only useful if it is no weaker than the thing it stands in for. This one lost
 * that property once already — a copy in `:feature:devices` matched the full MAC instead of the
 * OUI prefix — so the normalising behaviour is pinned here rather than left to a comment.
 */
class FakeOuiLookupTest {

    @Test
    fun `a full mac resolves through its oui prefix`() = runTest {
        val oui = FakeOuiLookup().apply { table["11:22:33"] = "Acme" }

        assertEquals("Acme", oui.lookup("11:22:33:44:55:66"))
    }

    @Test
    fun `case and hyphen separators are normalised the way a real database does`() = runTest {
        val oui = FakeOuiLookup().apply { table["AA:BB:CC"] = "Globex" }

        assertEquals("Globex", oui.lookup("aa:bb:cc:dd:ee:ff"))
        assertEquals("Globex", oui.lookup("aa-bb-cc-dd-ee-ff"))
    }

    @Test
    fun `an unknown prefix is null rather than a stand-in vendor`() = runTest {
        val oui = FakeOuiLookup().apply { table["11:22:33"] = "Acme" }

        assertNull(oui.lookup("99:88:77:66:55:44"))
    }

    @Test
    fun `keying the table on a full mac does not match, because the real one would not either`() =
        runTest {
            // The weak copy's behaviour, asserted as a non-behaviour: if this ever passes, the
            // fake has stopped requiring callers to hand over something resolvable.
            val oui = FakeOuiLookup().apply { table["11:22:33:44:55:66"] = "Acme" }

            assertNull(oui.lookup("11:22:33:44:55:66"))
        }
}
