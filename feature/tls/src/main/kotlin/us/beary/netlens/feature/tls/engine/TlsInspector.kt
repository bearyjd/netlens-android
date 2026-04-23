package us.beary.netlens.feature.tls.engine

import us.beary.netlens.feature.tls.model.TlsInspectResult

interface TlsInspector {
    suspend fun inspect(host: String, port: Int = 443): TlsInspectResult
}
