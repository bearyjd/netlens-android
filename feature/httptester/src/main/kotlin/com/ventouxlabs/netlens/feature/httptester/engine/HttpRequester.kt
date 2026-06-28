package com.ventouxlabs.netlens.feature.httptester.engine

import com.ventouxlabs.netlens.feature.httptester.model.HttpRequestConfig
import com.ventouxlabs.netlens.feature.httptester.model.HttpResponseResult

interface HttpRequester {
    suspend fun execute(config: HttpRequestConfig): HttpResponseResult
}
