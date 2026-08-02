package com.ventouxlabs.netlens.ui.home

import app.cash.paparazzi.Paparazzi
import com.ventouxlabs.netlens.feature.posture.model.PostureUiState
import org.junit.Rule
import org.junit.Test

/** Composition smoke tests for Home's state-driven grid; no golden images are recorded. */
class HomeContentRenderTest {

    @get:Rule
    val paparazzi = Paparazzi()

    private fun render(state: HomeUiState) = paparazzi.snapshot {
        HomeContent(
            state = state,
            postureState = PostureUiState.Loading,
            onToolClick = {},
            onSettingsClick = {},
            onSearchQueryChanged = {},
            onToggleFavoritesEditing = {},
            onPostureRetry = {},
            metricsContent = {},
        )
    }

    @Test
    fun `the standard tool grid renders`() {
        render(HomeUiState())
    }

    @Test
    fun `favorites and recents render with the tool grid`() {
        render(
            HomeUiState(
                favoriteRoutes = setOf("ping", "dns"),
                recentRoutes = listOf("traceroute", "portscan"),
            ),
        )
    }

    @Test
    fun `a no-results search renders`() {
        render(HomeUiState(searchQuery = "not-a-tool"))
    }
}
