package com.ventoux.netlens.feature.posture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventoux.netlens.core.data.dao.LanScanHistoryDao
import com.ventoux.netlens.core.network.NetworkMonitor
import com.ventoux.netlens.feature.posture.engine.PostureScoreEngine
import com.ventoux.netlens.feature.posture.engine.EncryptionTypeProvider
import com.ventoux.netlens.feature.posture.model.PostureUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import android.util.Log
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

@HiltViewModel
class PostureViewModel @Inject constructor(
    private val networkMonitor: NetworkMonitor,
    private val encryptionTypeProvider: EncryptionTypeProvider,
    private val lanScanHistoryDao: LanScanHistoryDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PostureUiState>(PostureUiState.Loading)
    val uiState: StateFlow<PostureUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        observeNetwork()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val online = networkMonitor.isOnline.first()
            if (!online) {
                _uiState.value = PostureUiState.Disconnected
            } else {
                recalculate()
            }
        }
    }

    private fun observeNetwork() {
        viewModelScope.launch {
            combine(
                networkMonitor.isOnline,
                networkMonitor.isVpnActive,
            ) { online, vpn -> online to vpn }
                .collectLatest { (online, vpn) ->
                    refreshJob?.cancel()
                    if (!online) {
                        _uiState.value = PostureUiState.Disconnected
                    } else {
                        recalculate(isOnline = true, isVpnActive = vpn)
                    }
                }
        }
    }

    private suspend fun recalculate(
        isOnline: Boolean = true,
        isVpnActive: Boolean? = null,
    ) {
        try {
            val vpn = isVpnActive ?: networkMonitor.isVpnActive.first()
            val encryption = encryptionTypeProvider.currentEncryptionType()
            val latestScan = lanScanHistoryDao.getRecent(1)
                .map { it.firstOrNull()?.deviceCount }
                .first()

            val score = PostureScoreEngine.compute(
                encryptionType = encryption,
                isConnected = isOnline,
                deviceCount = latestScan,
                isVpnActive = vpn,
            )

            _uiState.value = if (score != null) {
                PostureUiState.Scored(score)
            } else {
                PostureUiState.Error("Unable to evaluate network security")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("PostureViewModel", "Score calculation failed", e)
            _uiState.value = PostureUiState.Error(
                "Unable to evaluate network security. Try again later.",
            )
        }
    }
}
