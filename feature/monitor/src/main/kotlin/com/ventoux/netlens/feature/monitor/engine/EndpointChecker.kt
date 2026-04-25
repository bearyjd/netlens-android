package com.ventoux.netlens.feature.monitor.engine

import com.ventoux.netlens.core.data.model.EndpointCheck

interface EndpointChecker {
    suspend fun check(url: String): EndpointCheck
}
