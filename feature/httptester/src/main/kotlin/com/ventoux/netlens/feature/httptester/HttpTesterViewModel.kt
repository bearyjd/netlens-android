package com.ventoux.netlens.feature.httptester

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.ventoux.netlens.core.data.dao.HttpTesterHistoryDao
import com.ventoux.netlens.core.data.model.HttpTesterHistoryEntry
import com.ventoux.netlens.feature.httptester.engine.HttpRequester
import com.ventoux.netlens.feature.httptester.model.HttpRequestConfig
import com.ventoux.netlens.feature.httptester.model.HttpResponseResult
import com.ventoux.netlens.feature.httptester.model.HttpTesterUiState
import javax.inject.Inject

@HiltViewModel
class HttpTesterViewModel @Inject constructor(
    private val httpRequester: HttpRequester,
    private val httpTesterHistoryDao: HttpTesterHistoryDao,
) : ViewModel() {

    private val _state = MutableStateFlow<HttpTesterUiState>(HttpTesterUiState.Idle)
    val state: StateFlow<HttpTesterUiState> = _state.asStateFlow()

    fun sendRequest(config: HttpRequestConfig) {
        _state.value = HttpTesterUiState.Loading

        viewModelScope.launch {
            try {
                val result = httpRequester.execute(config)
                _state.value = HttpTesterUiState.Success(result)
                saveToHistory(config, result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _state.value = HttpTesterUiState.Error(e.message ?: "Invalid request")
            } catch (e: Exception) {
                _state.value = HttpTesterUiState.Error(
                    e.message ?: "Request failed",
                )
            }
        }
    }

    private suspend fun saveToHistory(config: HttpRequestConfig, result: HttpResponseResult) {
        httpTesterHistoryDao.insert(
            HttpTesterHistoryEntry(
                url = config.url,
                method = config.method.name,
                statusCode = result.statusCode,
                durationMs = result.latencyMs,
                responseSize = result.contentLength ?: result.body.length.toLong(),
            ),
        )
    }
}
