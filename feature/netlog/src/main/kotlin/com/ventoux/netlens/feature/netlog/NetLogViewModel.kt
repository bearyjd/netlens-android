package com.ventoux.netlens.feature.netlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ventoux.netlens.core.data.dao.NetworkEventDao
import com.ventoux.netlens.feature.netlog.engine.NetworkMonitor
import com.ventoux.netlens.feature.netlog.model.NetLogUiState
import javax.inject.Inject

@HiltViewModel
class NetLogViewModel @Inject constructor(
    private val networkMonitor: NetworkMonitor,
    private val networkEventDao: NetworkEventDao,
) : ViewModel() {

    private val _state = MutableStateFlow(NetLogUiState())
    val state: StateFlow<NetLogUiState> = _state.asStateFlow()

    private var monitorJob: Job? = null

    init {
        networkEventDao.getRecent(MAX_DISPLAYED_EVENTS)
            .onEach { events -> _state.update { it.copy(events = events) } }
            .catch { e -> _state.update { it.copy(error = e.message ?: "Failed to load events") } }
            .launchIn(viewModelScope)
    }

    companion object {
        private const val MAX_DISPLAYED_EVENTS = 500
    }

    fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        _state.update { it.copy(isMonitoring = true) }
        monitorJob = networkMonitor.observeNetworkChanges()
            .onEach { event ->
                networkEventDao.insert(event)
            }
            .catch { error ->
                _state.update {
                    it.copy(
                        isMonitoring = false,
                        error = error.message ?: "Monitoring failed",
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        _state.update { it.copy(isMonitoring = false) }
    }

    fun showClearConfirmation() {
        _state.update { it.copy(showClearConfirmation = true) }
    }

    fun hideClearConfirmation() {
        _state.update { it.copy(showClearConfirmation = false) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            runCatching {
                networkEventDao.deleteAll()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Failed to clear history") }
            }
        }
        hideClearConfirmation()
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
