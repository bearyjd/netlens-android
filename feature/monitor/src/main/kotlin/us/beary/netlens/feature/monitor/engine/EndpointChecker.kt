package us.beary.netlens.feature.monitor.engine

import us.beary.netlens.core.data.model.EndpointCheck

interface EndpointChecker {
    suspend fun check(url: String): EndpointCheck
}
