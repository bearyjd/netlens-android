package com.ventouxlabs.netlens.core.scan.engine

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Scripted [DeviceDiscoveryPipeline]: replays [events] in order for every [run].
 *
 * [onRun] runs first, inside the collector, so a test can read state such as
 * `FakeLanNetworkBinder.isBound` at the moment the pipeline actually starts.
 * Set [suspendAfterEvents] to leave the run in flight (until cancelled) once the events are out.
 */
class FakeDeviceDiscoveryPipeline : DeviceDiscoveryPipeline {
    var events: List<DiscoveryEvent> = emptyList()
    var error: Throwable? = null
    var suspendAfterEvents: Boolean = false
    var onRun: suspend () -> Unit = {}

    private val _requests = mutableListOf<DiscoveryRequest>()
    val requests: List<DiscoveryRequest> get() = _requests.toList()

    /** True from the moment a run starts until it completes, fails or is cancelled. */
    var isRunning: Boolean = false
        private set

    override fun run(request: DiscoveryRequest): Flow<DiscoveryEvent> = flow {
        _requests += request
        isRunning = true
        try {
            onRun()
            error?.let { throw it }
            events.forEach { emit(it) }
            if (suspendAfterEvents) awaitCancellation()
        } finally {
            isRunning = false
        }
    }
}
