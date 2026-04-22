package us.beary.netlens.feature.ping.engine

import kotlinx.coroutines.flow.Flow
import us.beary.netlens.feature.ping.model.PingResult

interface Pinger {
    fun ping(host: String, count: Int = 4): Flow<PingResult>
}
