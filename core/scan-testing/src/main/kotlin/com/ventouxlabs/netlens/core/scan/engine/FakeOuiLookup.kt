package com.ventouxlabs.netlens.core.scan.engine

import com.ventouxlabs.netlens.core.oui.OuiLookup

/**
 * Vendor lookup keyed by **OUI prefix**, the way the real one is.
 *
 * [table] is keyed on the first three octets, uppercase and colon-separated (`"AA:BB:CC"`), and
 * [lookup] normalises whatever MAC it is handed down to that shape before matching — because that
 * is the whole job of a real OUI database.
 *
 * The body below mirrors `OuiLookupImpl.normalizePrefix` exactly
 * (`mac.take(8).uppercase().replace('-', ':')`), which is also how that class keys the map it
 * parses out of `oui.txt`. **If that function changes, change this with it** — nothing enforces
 * the pairing, and a fake that normalises differently from production is the problem this whole
 * module exists to remove, one layer up.
 *
 * This matters more than it looks. A version keyed on the *full* MAC used to live in
 * `:feature:devices`, and under it a caller that passed an unnormalised MAC looked identical to
 * one that got it right, as long as the test wrote its key in the same form the caller happened to
 * use. A double that is looser than production turns a red test green, which is the failure mode
 * this source set exists to prevent.
 */
class FakeOuiLookup : OuiLookup {
    val table = mutableMapOf<String, String>()

    override suspend fun lookup(mac: String): String? {
        val prefix = mac.take(8).uppercase().replace('-', ':')
        return table[prefix]
    }
}
