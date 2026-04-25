package com.ventoux.netlens.feature.monitor.dao

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import com.ventoux.netlens.core.data.dao.EndpointDao
import com.ventoux.netlens.core.data.model.EndpointCheck
import com.ventoux.netlens.core.data.model.MonitoredEndpoint

class FakeEndpointDao : EndpointDao {
    private val endpoints = MutableStateFlow<List<MonitoredEndpoint>>(emptyList())
    private val checks = MutableStateFlow<List<EndpointCheck>>(emptyList())
    private var nextId = 1L

    override fun getAllEndpoints(): Flow<List<MonitoredEndpoint>> = endpoints

    override suspend fun getEndpointById(id: Long): MonitoredEndpoint? =
        endpoints.value.find { it.id == id }

    override suspend fun insertEndpoint(endpoint: MonitoredEndpoint): Long {
        val id = nextId++
        val withId = endpoint.copy(id = id)
        endpoints.value = endpoints.value + withId
        return id
    }

    override suspend fun deleteEndpoint(endpoint: MonitoredEndpoint) {
        endpoints.value = endpoints.value.filter { it.id != endpoint.id }
    }

    override fun getChecksForEndpoint(endpointId: Long, limit: Int): Flow<List<EndpointCheck>> =
        checks.map { list -> list.filter { it.endpointId == endpointId }.takeLast(limit) }

    override suspend fun insertCheck(check: EndpointCheck) {
        checks.value = checks.value + check
    }

    override suspend fun deleteChecksOlderThan(before: Long) {
        checks.value = checks.value.filter { it.timestamp >= before }
    }
}
