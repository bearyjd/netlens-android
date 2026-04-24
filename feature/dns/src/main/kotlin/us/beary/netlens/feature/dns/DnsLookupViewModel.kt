package us.beary.netlens.feature.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import us.beary.netlens.feature.dns.engine.DnsResolver
import us.beary.netlens.feature.dns.model.DnsError
import us.beary.netlens.feature.dns.model.DnsLookupUiState
import us.beary.netlens.feature.dns.model.DnsRecordType
import javax.inject.Inject

@HiltViewModel
class DnsLookupViewModel @Inject constructor(
    private val dnsResolver: DnsResolver,
) : ViewModel() {

    private val _state = MutableStateFlow(DnsLookupUiState())
    val state: StateFlow<DnsLookupUiState> = _state.asStateFlow()

    private var lookupJob: Job? = null

    fun onDomainChanged(domain: String) {
        _state.update { it.copy(domain = domain) }
    }

    fun onTypeToggled(type: DnsRecordType) {
        _state.update { current ->
            val updated = if (type in current.selectedTypes) {
                current.selectedTypes - type
            } else {
                current.selectedTypes + type
            }
            current.copy(selectedTypes = updated)
        }
    }

    fun lookup() {
        val current = _state.value
        val domain = current.domain.trim()
        if (domain.isBlank()) {
            _state.update { it.copy(error = DnsError.EmptyDomain) }
            return
        }
        if (current.selectedTypes.isEmpty()) {
            _state.update { it.copy(error = DnsError.NoTypes) }
            return
        }

        lookupJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null, results = emptyList()) }
        lookupJob = viewModelScope.launch {
            dnsResolver.lookup(domain, current.selectedTypes)
                .onSuccess { results ->
                    _state.update { it.copy(isLoading = false, results = results) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = DnsError.LookupFailed(throwable.message),
                        )
                    }
                }
        }
    }
}
