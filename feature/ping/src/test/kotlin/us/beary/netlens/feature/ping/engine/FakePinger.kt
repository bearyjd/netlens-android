package us.beary.netlens.feature.ping.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import us.beary.netlens.feature.ping.model.PingResult

class FakePinger : Pinger {
    var results: List<PingResult> = emptyList()
    var error: Throwable? = null

    override fun ping(host: String, count: Int): Flow<PingResult> = flow {
        error?.let { throw it }
        results.forEach { emit(it) }
    }
}
