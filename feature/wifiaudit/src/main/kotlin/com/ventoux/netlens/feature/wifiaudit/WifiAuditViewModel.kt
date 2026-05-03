package com.ventoux.netlens.feature.wifiaudit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventoux.netlens.core.data.dao.NetworkEventDao
import com.ventoux.netlens.core.data.model.NetworkEvent
import com.ventoux.netlens.core.data.model.NetworkEventType
import com.ventoux.netlens.feature.wifiaudit.engine.WifiAuditEngine
import com.ventoux.netlens.feature.wifiaudit.engine.WifiInfoReader
import com.ventoux.netlens.feature.wifiaudit.model.AuditSeverity
import com.ventoux.netlens.feature.wifiaudit.model.WifiAuditUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WifiAuditViewModel @Inject constructor(
    private val wifiInfoReader: WifiInfoReader,
    private val networkEventDao: NetworkEventDao,
) : ViewModel() {

    private val _state = MutableStateFlow(WifiAuditUiState())
    val state: StateFlow<WifiAuditUiState> = _state.asStateFlow()

    init {
        runAudit()
    }

    fun runAudit() {
        _state.update { it.copy(isAuditing = true, error = null, findings = emptyList(), dismissedIds = emptySet()) }

        viewModelScope.launch {
            runCatching {
                val info = wifiInfoReader.readConnected()
                if (info == null) {
                    _state.update {
                        it.copy(
                            isAuditing = false,
                            error = "Not connected to a Wi-Fi network",
                        )
                    }
                    return@launch
                }

                val findings = WifiAuditEngine.audit(info)
                _state.update {
                    it.copy(
                        isAuditing = false,
                        ssid = info.ssid,
                        findings = findings,
                    )
                }

                logAuditEvent(info.ssid, findings.count { it.severity == AuditSeverity.Critical || it.severity == AuditSeverity.Warning })
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isAuditing = false,
                        error = error.message ?: "Audit failed",
                    )
                }
            }
        }
    }

    fun dismissFinding(id: String) {
        _state.update { it.copy(dismissedIds = it.dismissedIds + id) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private suspend fun logAuditEvent(ssid: String, issueCount: Int) {
        val detail = if (issueCount == 0) "No issues found" else "$issueCount issue${if (issueCount != 1) "s" else ""} found"
        networkEventDao.insert(
            NetworkEvent(
                timestamp = System.currentTimeMillis(),
                eventType = NetworkEventType.SECURITY_AUDIT,
                transportType = "Wi-Fi",
                networkDetails = "Audit: $ssid — $detail",
                isVpn = false,
            ),
        )
    }
}
