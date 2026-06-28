package com.ventouxlabs.netlens.feature.tls.engine

import com.ventouxlabs.netlens.feature.tls.model.TlsInspectResult

interface TlsInspector {
    suspend fun inspect(host: String, port: Int = 443): TlsInspectResult
}
