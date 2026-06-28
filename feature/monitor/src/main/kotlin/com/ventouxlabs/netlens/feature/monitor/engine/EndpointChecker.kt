package com.ventouxlabs.netlens.feature.monitor.engine

import com.ventouxlabs.netlens.core.data.model.EndpointCheck

interface EndpointChecker {
    suspend fun check(url: String): EndpointCheck
}
