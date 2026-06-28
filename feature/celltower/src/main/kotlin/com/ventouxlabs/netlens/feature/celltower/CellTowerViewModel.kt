package com.ventouxlabs.netlens.feature.celltower

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventouxlabs.netlens.feature.celltower.engine.CellTowerReader
import com.ventouxlabs.netlens.feature.celltower.model.CellTowerInfo
import com.ventouxlabs.netlens.feature.celltower.model.CellTowerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class CellTowerViewModel @Inject constructor(
    private val cellTowerReader: CellTowerReader,
) : ViewModel() {

    private val _state = MutableStateFlow(CellTowerUiState())
    val state: StateFlow<CellTowerUiState> = _state.asStateFlow()

    private var observeJob: Job? = null

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(hasPermission = granted) }
        if (granted) startObserving()
    }

    fun refresh() {
        if (!_state.value.hasPermission) return
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { cellTowerReader.readOnce() }
            if (snapshot == null) {
                _state.update { it.copy(isRefreshing = false, noCellular = true) }
            } else {
                _state.update {
                    it.copy(
                        connectedTower = snapshot.connected,
                        neighborCells = snapshot.neighbors,
                        isRefreshing = false,
                        noCellular = false,
                    )
                }
            }
        }
    }

    fun buildExportText(): String {
        val current = _state.value
        val sb = StringBuilder()
        sb.appendLine("Cell Tower Info")

        current.connectedTower?.let { tower ->
            sb.appendLine()
            sb.appendLine("Connected Tower:")
            sb.appendLine(formatTowerText(tower))
        }

        if (current.neighborCells.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Neighbor Cells (${current.neighborCells.size}):")
            current.neighborCells.forEach { tower ->
                sb.appendLine(formatTowerText(tower))
            }
        }

        return sb.toString().trimEnd()
    }

    fun buildExportJson(): String {
        val current = _state.value
        val allTowers = listOfNotNull(current.connectedTower) + current.neighborCells
        return Json.encodeToString(allTowers)
    }

    private fun startObserving() {
        observeJob?.cancel()
        _state.update { it.copy(isRefreshing = true, error = null) }

        observeJob = viewModelScope.launch {
            cellTowerReader.observe()
                .catch { e ->
                    _state.update {
                        it.copy(
                            isRefreshing = false,
                            error = e.message ?: "Failed to read cell info",
                        )
                    }
                }
                .collect { towerState ->
                    _state.update {
                        it.copy(
                            connectedTower = towerState.connected,
                            neighborCells = towerState.neighbors,
                            isRefreshing = false,
                            noCellular = towerState.connected == null && towerState.neighbors.isEmpty(),
                        )
                    }
                }
        }
    }

    private fun formatTowerText(tower: CellTowerInfo): String {
        val parts = mutableListOf<String>()
        parts += "  ${tower.networkType}"
        if (tower.operatorName.isNotEmpty()) parts += "Operator: ${tower.operatorName}"
        if (tower.cellId.isNotEmpty()) parts += "CID: ${tower.cellId}"
        if (tower.tac.isNotEmpty()) parts += "TAC: ${tower.tac}"
        if (tower.band.isNotEmpty()) parts += tower.band
        tower.rsrp?.let { parts += "RSRP: ${it}dBm" }
        tower.rsrq?.let { parts += "RSRQ: ${it}dB" }
        tower.sinr?.let { parts += "SINR: ${it}dB" }
        tower.rssi?.let { parts += "RSSI: ${it}dBm" }
        return parts.joinToString(" | ")
    }

    override fun onCleared() {
        super.onCleared()
        observeJob?.cancel()
    }
}
