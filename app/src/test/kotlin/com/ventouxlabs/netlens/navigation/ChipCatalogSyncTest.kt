package com.ventouxlabs.netlens.navigation

import com.ventouxlabs.netlens.widget.util.ChipCatalog
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `:widget` cannot depend on `:app`, so `ChipCatalog.ELIGIBLE` duplicates its route strings from
 * [ToolDestination.route] by hand rather than sharing them — see `ChipCatalog`'s own KDoc. `:app`
 * is the one module allowed to know about both, so this is where a rename or removal in
 * [ToolDestination] gets caught instead of silently producing a widget chip that deep-links
 * nowhere.
 */
class ChipCatalogSyncTest {

    @Test
    fun `every ChipCatalog route exists in ToolDestination`() {
        val validRoutes = ToolDestination.entries.map { it.route }.toSet()
        val stale = ChipCatalog.ELIGIBLE.map { it.route } - validRoutes

        assertTrue(stale.isEmpty(), "Stale ChipCatalog routes not in ToolDestination: $stale")
    }
}
