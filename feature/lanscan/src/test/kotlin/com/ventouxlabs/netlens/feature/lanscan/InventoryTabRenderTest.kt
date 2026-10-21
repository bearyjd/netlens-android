package com.ventouxlabs.netlens.feature.lanscan

import app.cash.paparazzi.Paparazzi
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.feature.lanscan.model.DeviceSortField
import org.junit.Rule
import org.junit.Test

/**
 * Composition smoke tests — no golden images, appearance is not asserted. Lazy item keys are
 * checked at composition time, so the expanded case exercises device/header key uniqueness for
 * the first visible items only (LazyColumn composes just what fits the viewport).
 */
class InventoryTabRenderTest {

    @get:Rule
    val paparazzi = Paparazzi()

    private val devices: List<KnownDeviceEntity> by lazy {
        listOf(KnownDeviceEntity(id = 1, macAddress = null, hostname = "router", ip = "10.0.0.1", vendor = null)) +
            (2L..31L).map { KnownDeviceEntity(id = it, macAddress = null, hostname = null, ip = "10.0.0.$it", vendor = null) }
    }

    @Test
    fun `many unidentified devices collapse behind one group row`() = render(expanded = false)

    @Test
    fun `expanded group composes unidentified rows alongside the header`() = render(expanded = true)

    private fun render(expanded: Boolean) {
        paparazzi.snapshot {
            InventoryTabContent(
                devices = devices,
                searchQuery = "",
                sortField = DeviceSortField.IP,
                sortAscending = true,
                onSearchChanged = {},
                onSortFieldChanged = {},
                onToggleSortOrder = {},
                onToggleKnown = {},
                onDeleteDevice = {},
                onClearInventory = {},
                onEditDeviceDetails = {},
                initialUnidentifiedExpanded = expanded,
            )
        }
    }
}
