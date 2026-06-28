package com.ventouxlabs.netlens.feature.tls.engine

import kotlinx.coroutines.CompletableDeferred
import com.ventouxlabs.netlens.feature.tls.model.TlsInspectResult

class FakeTlsInspector : TlsInspector {
    var result: TlsInspectResult? = null
    var error: Throwable? = null
    private var gate: CompletableDeferred<Unit>? = null

    fun enableSuspend() { gate = CompletableDeferred() }
    fun resume() { gate?.complete(Unit) }

    override suspend fun inspect(host: String, port: Int): TlsInspectResult {
        gate?.await()
        error?.let { throw it }
        return result ?: throw IllegalStateException("No result configured")
    }
}
