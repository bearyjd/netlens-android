package com.ventoux.netlens.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolDestinationTest {

    @Test
    fun `all tool routes are unique`() {
        val routes = ToolDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun `byCategory includes all tools`() {
        val allFromMap = ToolDestination.byCategory.values.flatten().toSet()
        assertEquals(ToolDestination.entries.toSet(), allFromMap)
    }

    @Test
    fun `every category has at least one tool`() {
        ToolCategory.entries.forEach { category ->
            val tools = ToolDestination.byCategory[category]
            assertTrue(
                tools != null && tools.isNotEmpty(),
                "Category ${category.name} has no tools",
            )
        }
    }
}
