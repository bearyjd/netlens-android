package us.beary.netlens.feature.tls.engine

import us.beary.netlens.feature.tls.model.TlsInspectResult

class FakeTlsInspector : TlsInspector {
    var result: TlsInspectResult? = null
    var error: Throwable? = null

    override suspend fun inspect(host: String, port: Int): TlsInspectResult {
        error?.let { throw it }
        return result ?: throw IllegalStateException("No result configured")
    }
}
