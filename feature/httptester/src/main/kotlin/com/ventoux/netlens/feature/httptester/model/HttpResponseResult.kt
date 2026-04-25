package com.ventoux.netlens.feature.httptester.model

data class HttpResponseResult(
    val statusCode: Int,
    val statusDescription: String,
    val headers: Map<String, List<String>>,
    val body: String,
    val latencyMs: Long,
    val contentLength: Long?,
)
