package com.ventoux.netlens.feature.httptester.engine

import com.ventoux.netlens.feature.httptester.model.HttpRequestConfig
import com.ventoux.netlens.feature.httptester.model.HttpResponseResult

interface HttpRequester {
    suspend fun execute(config: HttpRequestConfig): HttpResponseResult
}
