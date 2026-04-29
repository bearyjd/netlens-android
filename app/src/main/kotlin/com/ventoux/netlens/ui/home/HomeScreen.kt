package com.ventoux.netlens.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ventoux.netlens.R
import com.ventoux.netlens.feature.posture.PostureViewModel
import com.ventoux.netlens.feature.posture.gradeColor
import com.ventoux.netlens.feature.posture.model.PostureUiState
import com.ventoux.netlens.navigation.ToolCategory
import com.ventoux.netlens.navigation.ToolDestination
import com.ventoux.netlens.ui.components.NetworkStatusCard
import com.ventoux.netlens.ui.components.SectionHeader
import com.ventoux.netlens.ui.components.ToolChip
import com.ventoux.netlens.ui.components.ToolGridCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onToolClick: (ToolDestination) -> Unit,
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel = hiltViewModel(),
    postureViewModel: PostureViewModel = hiltViewModel(),
) {
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val postureState by postureViewModel.uiState.collectAsStateWithLifecycle()

    val handleToolClick = remember(homeViewModel, onToolClick) {
        { tool: ToolDestination ->
            homeViewModel.recordToolUsage(tool.route)
            onToolClick(tool)
        }
    }

    val disabledColor = MaterialTheme.colorScheme.onSurfaceVariant
    val (postureGrade, postureColor) = when (val p = postureState) {
        is PostureUiState.Scored -> p.score.grade to gradeColor(p.score.grade)
        is PostureUiState.Error -> "!" to MaterialTheme.colorScheme.error
        PostureUiState.Disconnected -> "—" to disabledColor
        PostureUiState.Loading -> "…" to disabledColor
    }

    val allGridTools = ToolDestination.entries.filter { it.isVisibleInGrid }
    val isSearching = homeState.searchQuery.isNotBlank()
    val filteredTools = if (isSearching) {
        allGridTools.filter {
            it.label.contains(homeState.searchQuery, ignoreCase = true) ||
                it.description.contains(homeState.searchQuery, ignoreCase = true)
        }
    } else {
        allGridTools
    }
    val favoriteTools = homeState.favoriteRoutes
        .mapNotNull { route -> ToolDestination.entries.find { it.route == route } }
    val recentTools = homeState.recentRoutes
        .mapNotNull { route -> ToolDestination.entries.find { it.route == route } }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "network_status") {
                NetworkStatusCard(
                    isConnected = homeState.isConnected,
                    ssid = homeState.interfaceLabel,
                    localIp = homeState.localIp,
                    gatewayIp = homeState.gatewayIp,
                    postureGrade = postureGrade,
                    postureColor = postureColor,
                    onClick = { handleToolClick(ToolDestination.Posture) },
                )
            }

            item(key = "search") {
                ToolSearchBar(
                    query = homeState.searchQuery,
                    onQueryChange = homeViewModel::onSearchQueryChanged,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (!isSearching) {
                if (favoriteTools.isNotEmpty()) {
                    item(key = "favorites_header") {
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
                    item(key = "favorites_row") {
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
                    item(key = "recents_header") {
                        SectionHeader(title = stringResource(R.string.home_recent))
                    }
                    item(key = "recents_row") {
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
                        item(key = "cat_${category.name}") {
                            SectionHeader(title = category.label)
                        }
                        val pairs = categoryTools.chunked(2)
                        items(
                            count = pairs.size,
                            key = { "grid_${category.name}_$it" },
                        ) { index ->
                            ToolGridRow(
                                tools = pairs[index],
                                onToolClick = handleToolClick,
                            )
                        }
                    }
                }
            } else {
                if (filteredTools.isEmpty()) {
                    item(key = "no_results") {
                        Text(
                            text = stringResource(R.string.home_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                } else {
                    val pairs = filteredTools.chunked(2)
                    items(
                        count = pairs.size,
                        key = { "search_$it" },
                    ) { index ->
                        ToolGridRow(
                            tools = pairs[index],
                            onToolClick = handleToolClick,
                        )
                    }
                }
            }
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

@Composable
private fun ToolGridRow(
    tools: List<ToolDestination>,
    onToolClick: (ToolDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tools.forEach { tool ->
            ToolGridCard(
                icon = tool.icon,
                label = tool.label,
                description = tool.description,
                onClick = { onToolClick(tool) },
                modifier = Modifier.weight(1f),
            )
        }
        if (tools.size == 1) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
