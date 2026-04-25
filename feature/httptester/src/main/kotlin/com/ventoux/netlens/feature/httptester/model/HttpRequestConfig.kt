package com.ventoux.netlens.feature.httptester.model

data class HttpRequestConfig(
    val url: String,
    val method: HttpMethod,
    val headers: Map<String, String>,
    val body: String?,
)
