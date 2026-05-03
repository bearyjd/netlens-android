package com.ventoux.netlens.feature.traceroute.model

data class TracerouteUiState(
    val host: String = "",
    val hops: List<TracerouteHop> = emptyList(),
    val isTracing: Boolean = false,
    val isGeoLoading: Boolean = false,
    val error: String? = null,
)
