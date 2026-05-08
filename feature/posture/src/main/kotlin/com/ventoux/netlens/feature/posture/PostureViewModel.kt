package com.ventoux.netlens.feature.posture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventoux.netlens.core.data.dao.LanScanHistoryDao
import com.ventoux.netlens.core.data.preferences.UserPreferencesRepository
import com.ventoux.netlens.core.network.NetworkMonitor
import com.ventoux.netlens.core.network.VpnState
import com.ventoux.netlens.feature.posture.engine.PostureScoreEngine
import com.ventoux.netlens.feature.posture.engine.EncryptionTypeProvider
import com.ventoux.netlens.feature.posture.model.PostureUiState
import com.ventoux.netlens.feature.posture.model.Severity
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
    private val preferences: UserPreferencesRepository,
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
                networkMonitor.vpnState,
            ) { online, vpn -> online to vpn }
                .collectLatest { (online, vpn) ->
                    refreshJob?.cancel()
                    if (!online) {
                        _uiState.value = PostureUiState.Disconnected
                    } else {
                        recalculate(isOnline = true, vpnState = vpn)
                    }
                }
        }
    }

    private suspend fun recalculate(
        isOnline: Boolean = true,
        vpnState: VpnState? = null,
    ) {
        try {
            val vpn = vpnState ?: networkMonitor.vpnState.first()
            val encryption = encryptionTypeProvider.currentEncryptionType()
            val latestScan = lanScanHistoryDao.getRecent(1)
                .map { it.firstOrNull()?.deviceCount }
                .first()

            // TODO: integrate network trust model — for now treat all networks as untrusted.
            val untrustedNetwork = true

            val score = PostureScoreEngine.compute(
                encryptionType = encryption,
                isConnected = isOnline,
                deviceCount = latestScan,
                vpnState = vpn,
                untrustedNetwork = untrustedNetwork,
            )

            if (score != null) {
                _uiState.value = PostureUiState.Scored(score)
                val issues = score.factors.filter {
                    it.severity == Severity.Critical || it.severity == Severity.Poor
                }
                // Persist for the home-screen widget. Non-fatal: the in-app Scored state
                // is already published; a write failure means the widget may serve the
                // previously cached score until its next refresh.
                try {
                    preferences.setPostureScore(
                        grade = score.grade,
                        numericScore = score.numericScore,
                        issueCount = issues.size,
                        topIssue = issues.firstOrNull()?.detail,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("PostureViewModel", "Failed to persist posture score", e)
                }
            } else {
                _uiState.value = PostureUiState.Error("Unable to evaluate network security")
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
