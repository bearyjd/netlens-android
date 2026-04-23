package us.beary.netlens.feature.traceroute.engine

import kotlinx.coroutines.flow.Flow
import us.beary.netlens.feature.traceroute.model.TracerouteHop

interface Tracer {
    fun trace(host: String, maxHops: Int = 30): Flow<TracerouteHop>
}
