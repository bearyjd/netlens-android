package us.beary.netlens.feature.tls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import us.beary.netlens.feature.tls.engine.TlsInspector
import us.beary.netlens.feature.tls.model.TlsUiState
import javax.inject.Inject

@HiltViewModel
class TlsViewModel @Inject constructor(
    private val inspector: TlsInspector,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TlsUiState>(TlsUiState.Idle)
    val uiState: StateFlow<TlsUiState> = _uiState.asStateFlow()

    fun inspect(host: String, port: Int = 443) {
        viewModelScope.launch {
            _uiState.value = TlsUiState.Loading
            try {
                val result = inspector.inspect(host, port)
                _uiState.value = TlsUiState.Success(result)
            } catch (e: Exception) {
                _uiState.value = TlsUiState.Error(
                    e.localizedMessage ?: "TLS inspection failed",
                )
            }
        }
    }
}
