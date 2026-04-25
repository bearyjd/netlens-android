package com.ventoux.netlens.feature.traceroute.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.ventoux.netlens.feature.traceroute.model.TracerouteHop

class FakeTracer : Tracer {
    var hops: List<TracerouteHop> = emptyList()
    var error: Throwable? = null

    override fun trace(host: String, maxHops: Int): Flow<TracerouteHop> = flow {
        error?.let { throw it }
        hops.forEach { emit(it) }
    }
}
