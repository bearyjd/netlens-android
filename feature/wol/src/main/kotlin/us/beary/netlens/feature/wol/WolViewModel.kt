package us.beary.netlens.feature.wol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import us.beary.netlens.core.data.dao.WolTargetDao
import us.beary.netlens.core.data.model.WolTarget
import us.beary.netlens.feature.wol.engine.WolSender
import us.beary.netlens.feature.wol.model.WolUiState
import javax.inject.Inject

@HiltViewModel
class WolViewModel @Inject constructor(
    private val wolSender: WolSender,
    private val wolTargetDao: WolTargetDao,
) : ViewModel() {

    private val _state = MutableStateFlow(WolUiState())
    val state: StateFlow<WolUiState> = _state.asStateFlow()

    init {
        wolTargetDao.getAll()
            .onEach { targets -> _state.update { it.copy(savedTargets = targets) } }
            .launchIn(viewModelScope)
    }

    fun onMacInputChanged(mac: String) {
        _state.update { it.copy(macInput = mac) }
    }

    fun onBroadcastIpChanged(ip: String) {
        _state.update { it.copy(broadcastIp = ip) }
    }

    fun onPortChanged(port: Int) {
        _state.update { it.copy(port = port) }
    }

    fun sendWol(macAddress: String, broadcastIp: String, port: Int) {
        viewModelScope.launch {
            wolSender.sendMagicPacket(macAddress, broadcastIp, port)
                .onSuccess {
                    _state.update { it.copy(lastSentStatus = "Magic packet sent to $macAddress") }
                    delay(SNACKBAR_DURATION_MS)
                    _state.update { it.copy(lastSentStatus = null) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = error.message ?: "Failed to send magic packet")
                    }
                }
        }
    }

    fun sendWolToTarget(target: WolTarget) {
        sendWol(target.macAddress, target.broadcastIp, target.port)
    }

    fun showAddDialog() {
        _state.update { it.copy(showAddDialog = true, addLabel = "", addMac = "") }
    }

    fun hideAddDialog() {
        _state.update { it.copy(showAddDialog = false, addLabel = "", addMac = "") }
    }

    fun onAddLabelChanged(label: String) {
        _state.update { it.copy(addLabel = label) }
    }

    fun onAddMacChanged(mac: String) {
        _state.update { it.copy(addMac = mac) }
    }

    fun saveTarget(label: String, macAddress: String, broadcastIp: String, port: Int) {
        viewModelScope.launch {
            runCatching {
                wolTargetDao.insert(
                    WolTarget(
                        label = label,
                        macAddress = macAddress,
                        broadcastIp = broadcastIp,
                        port = port,
                    ),
                )
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Failed to save target") }
            }
            hideAddDialog()
        }
    }

    fun deleteTarget(target: WolTarget) {
        viewModelScope.launch {
            runCatching {
                wolTargetDao.delete(target)
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Failed to delete target") }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearStatus() {
        _state.update { it.copy(lastSentStatus = null) }
    }

    private companion object {
        const val SNACKBAR_DURATION_MS = 3_000L
    }
}
