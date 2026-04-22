package us.beary.netlens.feature.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import us.beary.netlens.core.data.dao.EndpointDao
import us.beary.netlens.core.data.model.MonitoredEndpoint
import us.beary.netlens.feature.monitor.engine.EndpointChecker
import us.beary.netlens.feature.monitor.model.MonitorUiState
import javax.inject.Inject

@HiltViewModel
class MonitorViewModel @Inject constructor(
    private val endpointChecker: EndpointChecker,
    private val endpointDao: EndpointDao,
) : ViewModel() {

    private val _state = MutableStateFlow(MonitorUiState())
    val state: StateFlow<MonitorUiState> = _state.asStateFlow()

    private var checksJob: Job? = null

    init {
        viewModelScope.launch {
            endpointDao.getAllEndpoints().collect { endpoints ->
                _state.update { it.copy(endpoints = endpoints) }
            }
        }
    }

    fun addEndpoint(label: String, url: String, intervalSeconds: Int = 60) {
        viewModelScope.launch {
            val endpoint = MonitoredEndpoint(
                label = label.trim(),
                url = url.trim(),
                intervalSeconds = intervalSeconds,
            )
            endpointDao.insertEndpoint(endpoint)
        }
    }

    fun removeEndpoint(endpoint: MonitoredEndpoint) {
        viewModelScope.launch {
            if (_state.value.selectedEndpoint?.id == endpoint.id) {
                checksJob?.cancel()
                _state.update { it.copy(selectedEndpoint = null, checks = emptyList()) }
            }
            endpointDao.deleteEndpoint(endpoint)
        }
    }

    fun selectEndpoint(endpoint: MonitoredEndpoint) {
        checksJob?.cancel()
        _state.update { it.copy(selectedEndpoint = endpoint, checks = emptyList()) }
        checksJob = viewModelScope.launch {
            endpointDao.getChecksForEndpoint(endpoint.id).collect { checks ->
                _state.update { it.copy(checks = checks) }
            }
        }
    }

    fun deselectEndpoint() {
        checksJob?.cancel()
        _state.update { it.copy(selectedEndpoint = null, checks = emptyList()) }
    }

    fun checkNow(endpoint: MonitoredEndpoint) {
        viewModelScope.launch {
            _state.update { it.copy(isChecking = true) }
            val result = endpointChecker.check(endpoint.url)
            val check = result.copy(endpointId = endpoint.id)
            endpointDao.insertCheck(check)
            _state.update { it.copy(isChecking = false) }
        }
    }
}
