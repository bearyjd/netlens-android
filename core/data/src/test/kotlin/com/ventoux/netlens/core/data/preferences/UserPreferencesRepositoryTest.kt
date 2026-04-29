package com.ventoux.netlens.core.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesRepositoryTest {

    @TempDir
    lateinit var tempDir: File

    private val testScope = TestScope(UnconfinedTestDispatcher())
    private lateinit var repository: UserPreferencesRepository

    @AfterEach
    fun tearDown() {
        testScope.cancel()
    }

    @BeforeEach
    fun setUp() {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope.backgroundScope,
            produceFile = { File(tempDir, "test_prefs.preferences_pb") },
        )
        repository = UserPreferencesRepository(dataStore)
    }

    @Test
    fun `default favorites are ping, lanscan, dns`() = testScope.runTest {
        val favorites = repository.favoriteToolRoutes.first()
        assertEquals(UserPreferencesRepository.DEFAULT_FAVORITES, favorites)
    }

    @Test
    fun `default recents are empty`() = testScope.runTest {
        val recents = repository.recentToolRoutes.first()
        assertTrue(recents.isEmpty())
    }

    @Test
    fun `setFavorites replaces all favorites`() = testScope.runTest {
        val newFavorites = setOf("whois", "tls")
        repository.setFavorites(newFavorites)
        val favorites = repository.favoriteToolRoutes.first()
        assertEquals(newFavorites, favorites)
    }

    @Test
    fun `toggleFavorite adds when absent`() = testScope.runTest {
        repository.toggleFavorite("whois")
        val favorites = repository.favoriteToolRoutes.first()
        assertTrue("whois" in favorites)
        assertTrue("ping" in favorites)
    }

    @Test
    fun `toggleFavorite removes when present`() = testScope.runTest {
        repository.toggleFavorite("ping")
        val favorites = repository.favoriteToolRoutes.first()
        assertFalse("ping" in favorites)
        assertTrue("lanscan" in favorites)
    }

    @Test
    fun `recordToolUsage adds to recents`() = testScope.runTest {
        repository.recordToolUsage("ping")
        val recents = repository.recentToolRoutes.first()
        assertEquals(listOf("ping"), recents)
    }

    @Test
    fun `recordToolUsage moves duplicate to front`() = testScope.runTest {
        repository.recordToolUsage("ping")
        repository.recordToolUsage("dns")
        repository.recordToolUsage("ping")
        val recents = repository.recentToolRoutes.first()
        assertEquals(listOf("ping", "dns"), recents)
    }

    @Test
    fun `recordToolUsage evicts oldest when exceeding max`() = testScope.runTest {
        repository.recordToolUsage("a")
        repository.recordToolUsage("b")
        repository.recordToolUsage("c")
        repository.recordToolUsage("d")
        repository.recordToolUsage("e")
        repository.recordToolUsage("f")
        val recents = repository.recentToolRoutes.first()
        assertEquals(5, recents.size)
        assertEquals("f", recents.first())
        assertFalse("a" in recents)
    }
}
