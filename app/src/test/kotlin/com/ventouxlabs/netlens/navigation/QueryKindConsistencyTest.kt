package com.ventouxlabs.netlens.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QueryKindConsistencyTest {

    @Test
    fun `every destination agrees across sanitizer, deep link router and nav pattern`() {
        ToolDestination.entries.forEach { dest ->
            val takesQuery = dest.queryKind != QueryKind.None
            assertEquals(takesQuery, dest.route in ROUTES_WITH_QUERY, "${dest.route} deep link")
            assertEquals(takesQuery, dest.navPattern.endsWith("?query={query}"), "${dest.route} nav")
            if (!takesQuery) {
                assertEquals("", ToolQuery.sanitize(dest.route, "192.168.1.1"), "${dest.route} sanitize")
            } else {
                assertEquals(
                    dest.queryKind != QueryKind.Host && dest.queryKind != QueryKind.Url,
                    ToolQuery.sanitize(dest.route, "x@evil/../?") == "x@evil/../?",
                    "${dest.route} sanitize kind",
                )
            }
        }
    }

    @Test
    fun `routes taking no query are the expected ones`() {
        listOf("ipinfo", "mdns", "wol").forEach {
            assertTrue(!ToolDestination.fromRoute(it)!!.acceptsQuery, it)
        }
    }

    @Test
    fun `unknown route fails closed`() {
        assertEquals("", ToolQuery.sanitize("nonexistent", "192.168.1.1"))
    }
}
