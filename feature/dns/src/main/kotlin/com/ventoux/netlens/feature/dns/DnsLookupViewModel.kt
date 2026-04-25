package com.ventoux.netlens.feature.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.ventoux.netlens.core.data.dao.DnsHistoryDao
import com.ventoux.netlens.core.data.model.DnsHistoryEntry
import com.ventoux.netlens.feature.dns.engine.DnsResolver
import com.ventoux.netlens.feature.dns.model.DnsError
import com.ventoux.netlens.feature.dns.model.DnsLookupUiState
import com.ventoux.netlens.feature.dns.model.DnsRecordType
import javax.inject.Inject

@HiltViewModel
class DnsLookupViewModel @Inject constructor(
    private val dnsResolver: DnsResolver,
    private val dnsHistoryDao: DnsHistoryDao,
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
                    try {
                        dnsHistoryDao.insert(
                            DnsHistoryEntry(
                                query = domain,
                                recordType = current.selectedTypes.joinToString(",") { it.name },
                                resultsJson = Json.encodeToString(results.map { "${it.type.name}: ${it.value}" }),
                            ),
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
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
