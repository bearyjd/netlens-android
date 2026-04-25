package com.ventoux.netlens.feature.tls.engine

import com.ventoux.netlens.feature.tls.model.TlsInspectResult

interface TlsInspector {
    suspend fun inspect(host: String, port: Int = 443): TlsInspectResult
}
