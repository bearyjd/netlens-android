package com.ventouxlabs.netlens.feature.httptester.engine

import com.ventouxlabs.netlens.feature.httptester.model.HttpRequestConfig
import com.ventouxlabs.netlens.feature.httptester.model.HttpResponseResult

class FakeHttpRequester : HttpRequester {
    var result: HttpResponseResult? = null
    var error: Throwable? = null

    override suspend fun execute(config: HttpRequestConfig): HttpResponseResult {
        error?.let { throw it }
        return result ?: throw IllegalStateException("No result configured")
    }
}
