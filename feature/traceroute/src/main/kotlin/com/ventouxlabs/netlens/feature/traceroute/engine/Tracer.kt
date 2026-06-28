package com.ventouxlabs.netlens.feature.traceroute.engine

import kotlinx.coroutines.flow.Flow
import com.ventouxlabs.netlens.feature.traceroute.model.TracerouteHop

interface Tracer {
    fun trace(host: String, maxHops: Int = 30): Flow<TracerouteHop>
}
