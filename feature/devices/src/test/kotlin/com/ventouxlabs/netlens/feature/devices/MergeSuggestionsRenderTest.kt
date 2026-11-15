package com.ventouxlabs.netlens.feature.devices

import app.cash.paparazzi.Paparazzi
import com.ventouxlabs.netlens.core.data.model.DeviceIdentifierEntity
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import com.ventouxlabs.netlens.core.scan.identity.MergeSuggestion
import com.ventouxlabs.netlens.feature.devices.model.MergeSuggestionUi
import com.ventouxlabs.netlens.feature.devices.model.buildMergeSuggestionUi
import org.junit.Rule
import org.junit.Test

/**
 * Composition smoke tests: the render throwing is the failure (see
 * `.omc/skills/paparazzi-composition-smoke-expertise.md`). They prove the sheet composes, not that it
 * looks right. The sheet body is rendered directly because a `ModalBottomSheet` window is not composed here.
 */
class MergeSuggestionsRenderTest {

    @get:Rule
    val paparazzi = Paparazzi()

    private fun entity(
        id: Long,
        known: Boolean = false,
        name: String? = null,
        hostname: String? = "host$id",
        notes: String? = null,
        mac: String? = "aa:bb:cc:dd:ee:0$id",
        firstSeen: Long = id,
    ) = KnownDeviceEntity(
        id = id, macAddress = mac, hostname = hostname, ip = "192.168.1.$id", vendor = "Acme", firstSeen = firstSeen,
        isKnown = known, customName = name, notes = notes, tags = "iot",
    )

    private fun suggestion(
        low: KnownDeviceEntity,
        high: KnownDeviceEntity,
        reasons: List<String> = listOf("model", "hostname"),
        identifiers: Int = 2,
    ): MergeSuggestionUi = buildMergeSuggestionUi(
        pair = MergeSuggestion.BetweenRows(low.id, high.id, 70, reasons),
        survivor = low,
        loser = high,
        loserIdentifiers = (1..identifiers).map {
            DeviceIdentifierEntity(deviceId = high.id, kind = "UPNP_UDN", value = "uuid:$it", scopeKey = "", source = "SSDP", firstSeen = 0, lastSeen = 0)
        },
    )

    private fun render(suggestions: List<MergeSuggestionUi>, busyKey: String? = null, message: Int? = null) = paparazzi.snapshot {
        MergeSuggestionsContent(suggestions, busyKey, message, onClose = {}, onMerge = { _, _, _ -> }, onNotTheSame = {})
    }

    @Test
    fun `the banner renders singular and plural`() {
        paparazzi.snapshot { MergeSuggestionsBanner(count = 1, onClick = {}) }
        paparazzi.snapshot { MergeSuggestionsBanner(count = 12, onClick = {}) }
    }

    @Test
    fun `an empty sheet renders`() {
        render(emptyList())
    }

    @Test
    fun `one pair renders with its preview`() {
        render(listOf(suggestion(entity(1, name = "Mine", notes = "n"), entity(2, name = "Other", notes = "m"))))
    }

    @Test
    fun `a differing-trust pair renders with the explicit choice`() {
        render(listOf(suggestion(entity(1, known = true), entity(2, known = false, mac = "aa:bb:cc:dd:ee:ff"))), message = R.string.devices_suggestion_choose_trust)
    }

    @Test
    fun `long and hostile names and many identifiers render`() {
        val long = "x".repeat(400)
        val hostile = "nas\n192.168.1.1 (Router)\u202Eevil"
        render(
            listOf(
                suggestion(entity(1, name = long, mac = null), entity(2, name = hostile, hostname = null), reasons = listOf("???", "shared_identifier"), identifiers = 9),
            ),
            busyKey = "suggestion:1:2",
        )
    }

    @Test
    fun `pairs sharing a device id do not collide on keys`() {
        // (1,2) and (1,3): a key built from the low id alone would be duplicated and throw here.
        render(
            listOf(
                suggestion(entity(1), entity(2)),
                suggestion(entity(1), entity(3)),
            ),
        )
    }

    @Test
    fun `the confirmation body renders dropped values, the trust choice and the MAC warning`() {
        val rlo = Char(0x202E)
        val ui = suggestion(
            entity(1, known = true, name = "Mine", notes = "keep"),
            entity(2, known = false, name = "Other", notes = "lost${rlo}text\n" + "y".repeat(500), mac = "aa:bb:cc:dd:ee:ff"),
        )
        paparazzi.snapshot { MergeConfirmContent(ui, trustChoice = true) }
        paparazzi.snapshot { MergeConfirmContent(ui, trustChoice = false) }
        paparazzi.snapshot { MergeConfirmContent(ui, trustChoice = null) }
    }

    @Test
    fun `a trust-escalating pair renders the warning above the choice`() {
        render(listOf(suggestion(entity(1, known = true), entity(2, known = false, mac = "aa:bb:cc:dd:ee:ff"))))
    }
}
