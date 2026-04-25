package com.ventoux.netlens.feature.ipinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ventoux.netlens.core.data.dao.IpInfoHistoryDao
import com.ventoux.netlens.core.data.model.IpInfoHistoryEntry
import com.ventoux.netlens.feature.ipinfo.data.IpInfoRepository
import com.ventoux.netlens.feature.ipinfo.model.IpInfoUiState
import javax.inject.Inject

@HiltViewModel
class IpInfoViewModel @Inject constructor(
    private val repository: IpInfoRepository,
    private val ipInfoHistoryDao: IpInfoHistoryDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow<IpInfoUiState>(IpInfoUiState.Loading)
    val uiState: StateFlow<IpInfoUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = IpInfoUiState.Loading
            repository.fetchIpInfo()
                .onSuccess { data ->
                    _uiState.value = IpInfoUiState.Success(data)
                    ipInfoHistoryDao.insert(
                        IpInfoHistoryEntry(
                            ip = data.query,
                            isp = data.isp,
                            org = data.org,
                            countryCode = data.countryCode,
                            city = data.city,
                            isVpn = data.proxy,
                        ),
                    )
                }
                .onFailure { error ->
                    _uiState.value = IpInfoUiState.Error(
                        error.localizedMessage ?: "Unknown error",
                    )
                }
        }
    }
}
