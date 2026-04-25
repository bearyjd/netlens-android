package com.ventoux.netlens.feature.monitor.engine

import com.ventoux.netlens.core.data.model.EndpointCheck

class FakeEndpointChecker : EndpointChecker {
    var result: EndpointCheck? = null
    var error: Throwable? = null

    override suspend fun check(url: String): EndpointCheck {
        error?.let { throw it }
        return result ?: throw IllegalStateException("No result configured")
    }
}
