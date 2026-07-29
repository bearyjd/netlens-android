package com.ventouxlabs.netlens.feature.wifi

import com.ventouxlabs.netlens.feature.wifi.model.surveyPointKey
import com.ventouxlabs.netlens.feature.wifi.model.surveySessionKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Points and sessions are rendered by two `items()` calls in one `LazyColumn`, from two tables
 * with independent autoincrement sequences. Keying both on the raw id crashed the app on the
 * first capture — the moment a survey first holds both a point (id 1) and a session (id 1).
 */
class SurveyListKeysTest {

    @Test
    fun `point and session keys never collide across overlapping id ranges`() {
        val ids = 1L..100L
        val pointKeys = ids.map(::surveyPointKey)
        val sessionKeys = ids.map(::surveySessionKey)

        // The exact case that crashed: same row id in both lists.
        assertTrue(surveyPointKey(1) != surveySessionKey(1))

        val overlap = pointKeys.intersect(sessionKeys.toSet())
        assertTrue(overlap.isEmpty(), "keys must be disjoint, but these collide: $overlap")
    }

    @Test
    fun `keys are unique within each list and stable for a given id`() {
        val ids = 1L..100L
        assertEquals(ids.count(), ids.map(::surveyPointKey).distinct().size)
        assertEquals(ids.count(), ids.map(::surveySessionKey).distinct().size)
        // Stable across calls — Compose relies on the key surviving recomposition.
        assertEquals(surveyPointKey(7), surveyPointKey(7))
    }
}
