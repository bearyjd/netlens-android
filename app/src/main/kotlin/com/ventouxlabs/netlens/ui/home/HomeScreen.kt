package com.ventouxlabs.netlens.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventouxlabs.netlens.R
import com.ventouxlabs.netlens.feature.posture.PostureViewModel
import com.ventouxlabs.netlens.navigation.ToolCategory
import com.ventouxlabs.netlens.navigation.ToolDestination
import com.ventouxlabs.netlens.ui.components.SectionHeader
import com.ventouxlabs.netlens.ui.components.ToolChip
import com.ventouxlabs.netlens.ui.components.ToolGridCard
import com.ventouxlabs.netlens.ui.home.dashboard.MetricsRow
import com.ventouxlabs.netlens.ui.home.dashboard.PostureHeroCard
import com.ventouxlabs.netlens.ui.home.latency.LatencyDetailCard
import com.ventouxlabs.netlens.ui.home.latency.LatencyMonitorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onToolClick: (ToolDestination) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel = hiltViewModel(),
    postureViewModel: PostureViewModel = hiltViewModel(),
    latencyViewModel: LatencyMonitorViewModel = hiltViewModel(),
) {
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val postureState by postureViewModel.uiState.collectAsStateWithLifecycle()

    // Screen-level lifecycle wiring for the latency monitor. Deliberately NOT
    // inside a lazy item: an observer scoped to an item detaches whenever the
    // item scrolls out of composition.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> latencyViewModel.onResume()
                Lifecycle.Event.ON_PAUSE -> latencyViewModel.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val handleToolClick = remember(homeViewModel, onToolClick) {
        { tool: ToolDestination ->
            homeViewModel.recordToolUsage(tool.route)
            onToolClick(tool)
        }
    }

    val allGridTools = remember { ToolDestination.entries.filter { it.isVisibleInGrid } }
    val isSearching = homeState.searchQuery.isNotBlank()
    val filteredTools = remember(homeState.searchQuery, allGridTools) {
        if (homeState.searchQuery.isNotBlank()) {
            allGridTools.filter {
                it.label.contains(homeState.searchQuery, ignoreCase = true) ||
                    it.description.contains(homeState.searchQuery, ignoreCase = true)
            }
        } else {
            allGridTools
        }
    }
    val favoriteTools = remember(homeState.favoriteRoutes) {
        homeState.favoriteRoutes
            .mapNotNull { route -> ToolDestination.entries.find { it.route == route } }
    }
    val recentTools = remember(homeState.recentRoutes) {
        homeState.recentRoutes
            .mapNotNull { route -> ToolDestination.entries.find { it.route == route } }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "posture_hero", span = { GridItemSpan(maxLineSpan) }) {
                PostureHeroCard(
                    postureState = postureState,
                    isConnected = homeState.isConnected,
                    interfaceLabel = homeState.interfaceLabel,
                    onClick = { handleToolClick(ToolDestination.Posture) },
                    onRetry = postureViewModel::refresh,
                )
            }

            item(key = "metrics_row", span = { GridItemSpan(maxLineSpan) }) {
                // latencyState is collected inside this child, not in HomeScreen's body, so
                // the ~1Hz latency tick recomposes only this section and not the whole grid
                // (which would otherwise re-run every tool derivation once per second).
                HomeMetricsSection(
                    latencyViewModel = latencyViewModel,
                    isVpnActive = homeState.isVpnActive,
                    localIp = homeState.localIp,
                    gatewayIp = homeState.gatewayIp,
                    onVpnClick = { handleToolClick(ToolDestination.VpnStatus) },
                    onIpClick = { handleToolClick(ToolDestination.IpInfo) },
                )
            }

            item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                ToolSearchBar(
                    query = homeState.searchQuery,
                    onQueryChange = homeViewModel::onSearchQueryChanged,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (!isSearching) {
                if (favoriteTools.isNotEmpty()) {
                    item(key = "favorites_header", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            title = stringResource(R.string.home_favorites),
                            actionLabel = stringResource(
                                if (homeState.isEditingFavorites) R.string.home_done else R.string.home_edit,
                            ),
                            onAction = {
                                homeViewModel.setEditingFavorites(!homeState.isEditingFavorites)
                            },
                        )
                    }
                    item(key = "favorites_row", span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(favoriteTools, key = { it.route }) { tool ->
                                ToolChip(
                                    icon = tool.icon,
                                    label = tool.label,
                                    onClick = { handleToolClick(tool) },
                                )
                            }
                        }
                    }
                }

                if (recentTools.isNotEmpty()) {
                    item(key = "recents_header", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(title = stringResource(R.string.home_recent))
                    }
                    item(key = "recents_row", span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(recentTools, key = { it.route }) { tool ->
                                ToolChip(
                                    icon = tool.icon,
                                    label = tool.label,
                                    onClick = { handleToolClick(tool) },
                                )
                            }
                        }
                    }
                }

                ToolCategory.entries.forEach { category ->
                    val categoryTools = filteredTools.filter { it.category == category }
                    if (categoryTools.isNotEmpty()) {
                        item(key = "cat_${category.name}", span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(title = category.label)
                        }
                        items(categoryTools, key = { "tool_${it.route}" }) { tool ->
                            ToolGridCard(
                                icon = tool.icon,
                                label = tool.label,
                                description = tool.description,
                                onClick = { handleToolClick(tool) },
                            )
                        }
                    }
                }
            } else {
                if (filteredTools.isEmpty()) {
                    item(key = "no_results", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(R.string.home_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                } else {
                    items(filteredTools, key = { "search_${it.route}" }) { tool ->
                        ToolGridCard(
                            icon = tool.icon,
                            label = tool.label,
                            description = tool.description,
                            onClick = { handleToolClick(tool) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeMetricsSection(
    latencyViewModel: LatencyMonitorViewModel,
    isVpnActive: Boolean,
    localIp: String?,
    gatewayIp: String?,
    onVpnClick: () -> Unit,
    onIpClick: () -> Unit,
) {
    val latencyState by latencyViewModel.state.collectAsStateWithLifecycle()

    Column {
        MetricsRow(
            latestLatencyMs = latencyState.dataPoints.lastOrNull { it.latencyMs != null }?.latencyMs,
            latencyEnabled = latencyState.isEnabled,
            alertThresholdMs = latencyState.alertThresholdMs,
            latencyDataPoints = latencyState.dataPoints,
            isVpnActive = isVpnActive,
            localIp = localIp,
            gatewayIp = gatewayIp,
            onLatencyClick = {
                if (latencyState.isEnabled) {
                    latencyViewModel.toggleExpanded()
                } else {
                    latencyViewModel.toggleEnabled()
                }
            },
            onVpnClick = onVpnClick,
            onIpClick = onIpClick,
        )

        if (latencyState.isEnabled && latencyState.isExpanded) {
            Spacer(modifier = Modifier.height(8.dp))
            LatencyDetailCard(
                state = latencyState,
                onToggleEnabled = latencyViewModel::toggleEnabled,
                onConfigure = latencyViewModel::showConfig,
                onDismissConfig = latencyViewModel::dismissConfig,
                onSaveConfig = latencyViewModel::saveConfig,
            )
        }
    }
}

@Composable
private fun ToolSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.home_search_placeholder)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
    )
}
