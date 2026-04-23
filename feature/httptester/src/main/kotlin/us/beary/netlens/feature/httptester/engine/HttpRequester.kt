package us.beary.netlens.feature.httptester.engine

import us.beary.netlens.feature.httptester.model.HttpRequestConfig
import us.beary.netlens.feature.httptester.model.HttpResponseResult

interface HttpRequester {
    suspend fun execute(config: HttpRequestConfig): HttpResponseResult
}
