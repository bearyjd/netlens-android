package com.ventouxlabs.netlens.feature.devices

import app.cash.paparazzi.Paparazzi
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.scan.identity.MergeSuggestion
import com.ventouxlabs.netlens.feature.devices.model.DevicesUiState
import com.ventouxlabs.netlens.feature.devices.model.buildMergeSuggestionUi
import org.junit.Rule
import org.junit.Test

/** Composition smoke tests — see [AndroidScreenshotConventionPlugin]. */
class DevicesContentRenderTest {

    @get:Rule
    val paparazzi = Paparazzi()

    private fun device(id: Long, known: Boolean) = KnownDeviceEntity(
        id = id,
        macAddress = "aa:bb:cc:dd:ee:${id.toString().padStart(2, '0')}",
        hostname = if (known) "Known device $id" else "New device $id",
        ip = "192.168.1.$id",
        vendor = "Example vendor",
        isKnown = known,
        tags = if (known) "office, printer" else null,
    )

    private fun render(state: DevicesUiState) = paparazzi.snapshot {
        DevicesContent(
            state = state,
            isPro = true,
            onBack = {},
            onCopyResults = {},
            onShareResults = {},
            watchSection = {},
            onAutoMergeToggle = {},
            onOpenSuggestions = {},
            onCloseSuggestions = {},
            onMergeSuggestion = { _, _, _ -> },
            onRejectSuggestion = {},
            onSearchQueryChanged = {},
            onToggleTag = {},
            onClearTags = {},
            onSelectDevice = {},
            onSaveDetails = { _, _, _ -> },
            onToggleKnown = {},
            onDelete = {},
        )
    }

    @Test
    fun `the empty inventory renders`() {
        render(DevicesUiState())
    }

    @Test
    fun `new and known devices sharing an id render together`() {
        // The sections are separately keyed. Rendering both is the regression check: a raw-id
        // key would throw as soon as two data sources happen to overlap.
        render(
            DevicesUiState(
                devices = listOf(device(id = 1, known = false), device(id = 1, known = true)),
                availableTags = listOf("office", "printer"),
                activeTags = setOf("office"),
            ),
        )
    }

    @Test
    fun `a search with no matches renders`() {
        render(DevicesUiState(searchQuery = "router"))
    }

    @Test
    fun `the auto-merge switch renders both on and off`() {
        render(DevicesUiState(autoMergeEnabled = null))
        render(DevicesUiState(autoMergeEnabled = true))
        render(DevicesUiState(autoMergeEnabled = false))
    }

    @Test
    fun `the duplicates banner renders above the list`() {
        val a = device(id = 1, known = false)
        val b = device(id = 2, known = true)
        val pair = MergeSuggestion.BetweenRows(1, 2, 70, listOf("model"))
        render(DevicesUiState(devices = listOf(a, b), suggestions = listOf(buildMergeSuggestionUi(pair, a, b, emptyList()))))
    }
}
