package com.ventouxlabs.netlens.feature.monitor.model

import com.ventouxlabs.netlens.core.data.model.EndpointCheck

/**
 * Three-state reachability status for a monitored endpoint, derived from its
 * latest check and its per-endpoint latency threshold.
 */
enum class EndpointStatus {
    Up,
    Slow,
    Down,
    NoData,
}

/**
 * [EndpointCheckerImpl][com.ventouxlabs.netlens.feature.monitor.engine.EndpointCheckerImpl]
 * always measures elapsed wall-clock time before returning, on both the
 * success and failure paths, so `latencyMs` is never negative or absent on a
 * real check — a failed check still carries a meaningful (if irrelevant)
 * latency. Because of that, latency is only compared against the threshold
 * when the check succeeded; a failure is always [EndpointStatus.Down]
 * regardless of its latency value.
 */
fun endpointStatus(latestCheck: EndpointCheck?, thresholdMs: Int): EndpointStatus = when {
    latestCheck == null -> EndpointStatus.NoData
    !latestCheck.isSuccess -> EndpointStatus.Down
    latestCheck.latencyMs > thresholdMs -> EndpointStatus.Slow
    else -> EndpointStatus.Up
}
