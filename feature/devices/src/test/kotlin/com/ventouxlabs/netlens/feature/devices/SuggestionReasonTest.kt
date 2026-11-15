package com.ventouxlabs.netlens.feature.devices

import com.ventouxlabs.netlens.feature.devices.model.SuggestionReason
import com.ventouxlabs.netlens.feature.devices.model.suggestionKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SuggestionReasonTest {

    @Test
    fun `every code the resolver writes maps to its own reason`() {
        val codes = listOf(
            "model", "hostname", "vendor", "device_type", "shared_identifier", "weak_link",
            "contradictory_identifier", "distinct_decision", "user_details", "trust_differs", "changed_since_read",
        )
        val mapped = codes.map(SuggestionReason::fromCode)
        assertEquals(codes.size, mapped.toSet().size)
        assertEquals(false, SuggestionReason.UNKNOWN in mapped)
    }

    @Test
    fun `an unknown or hostile code falls back to a generic reason`() {
        assertEquals(SuggestionReason.UNKNOWN, SuggestionReason.fromCode("future_code"))
        assertEquals(SuggestionReason.UNKNOWN, SuggestionReason.fromCode("model\nforged"))
        assertEquals(SuggestionReason.UNKNOWN, SuggestionReason.fromCode(""))
    }

    @Test
    fun `suggestion keys are namespaced and include both ids`() {
        assertEquals("suggestion:1:2", suggestionKey(1, 2))
    }
}
