package com.ventouxlabs.netlens.feature.ping.engine

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.ventouxlabs.netlens.feature.ping.model.PingResult

class FakePinger : Pinger {
    var results: List<PingResult> = emptyList()
    var continuousResults: List<PingResult> = emptyList()
    var error: Throwable? = null

    /**
     * Keep the flow open after emitting instead of completing, as a real ping process does
     * between replies. Needed by watchdog tests: completion cancels the watchdog, so a fake
     * that completes immediately can never observe silence.
     */
    var holdOpen: Boolean = false

    /**
     * Virtual-time delay before each emission. Watchdog-reset tests need a reply that arrives
     * *later* than t=0 — with instant emission, "the reply reset the silence clock" and "the
     * clock was never reset" produce identical timelines and the test passes either way.
     */
    var emitDelayMs: Long = 0L

    override fun ping(host: String, count: Int): Flow<PingResult> = flow {
        error?.let { throw it }
        results.forEach {
            if (emitDelayMs > 0) delay(emitDelayMs)
            emit(it)
        }
        if (holdOpen) awaitCancellation()
    }

    override fun pingContinuous(host: String): Flow<PingResult> = flow {
        error?.let { throw it }
        continuousResults.forEach {
            if (emitDelayMs > 0) delay(emitDelayMs)
            emit(it)
        }
        if (holdOpen) awaitCancellation()
    }
}
