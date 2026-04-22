package us.beary.netlens.feature.httptester

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import us.beary.netlens.feature.httptester.engine.HttpRequester
import us.beary.netlens.feature.httptester.model.HttpRequestConfig
import us.beary.netlens.feature.httptester.model.HttpTesterUiState
import javax.inject.Inject

@HiltViewModel
class HttpTesterViewModel @Inject constructor(
    private val httpRequester: HttpRequester,
) : ViewModel() {

    private val _state = MutableStateFlow<HttpTesterUiState>(HttpTesterUiState.Idle)
    val state: StateFlow<HttpTesterUiState> = _state.asStateFlow()

    fun sendRequest(config: HttpRequestConfig) {
        _state.value = HttpTesterUiState.Loading

        viewModelScope.launch {
            try {
                val result = httpRequester.execute(config)
                _state.value = HttpTesterUiState.Success(result)
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
}
