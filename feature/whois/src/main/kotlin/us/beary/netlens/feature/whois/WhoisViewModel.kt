package us.beary.netlens.feature.whois

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import us.beary.netlens.feature.whois.engine.DomainResolver
import us.beary.netlens.feature.whois.engine.RdnsResolver
import us.beary.netlens.feature.whois.engine.WhoisClient
import us.beary.netlens.feature.whois.model.RdnsResult
import us.beary.netlens.feature.whois.model.WhoisResult
import us.beary.netlens.feature.whois.model.WhoisUiState
import javax.inject.Inject

@HiltViewModel
class WhoisViewModel @Inject constructor(
    private val whoisClient: WhoisClient,
    private val rdnsResolver: RdnsResolver,
    private val domainResolver: DomainResolver,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<WhoisUiState>(WhoisUiState.Idle)
    val state: StateFlow<WhoisUiState> = _state.asStateFlow()

    fun onQueryChanged(value: String) {
        _query.value = value
    }

    fun lookup(input: String = _query.value) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            _state.value = WhoisUiState.Error("Please enter a domain or IP address")
            return
        }

        viewModelScope.launch {
            _state.value = WhoisUiState.Loading

            try {
                if (looksLikeIp(trimmed)) {
                    val rdns = rdnsResolver.resolve(trimmed)
                    _state.value = WhoisUiState.Success(whois = null, rdns = rdns)
                } else {
                    val whoisDeferred = async {
                        try {
                            Result.success(whoisClient.query(trimmed))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    }
                    val rdnsDeferred = async { resolveAndReverseDns(trimmed) }

                    val whoisResult = whoisDeferred.await()
                    val rdnsResult = rdnsDeferred.await()

                    if (whoisResult.isFailure && rdnsResult == null) {
                        _state.value = WhoisUiState.Error(
                            whoisResult.exceptionOrNull()?.message ?: "WHOIS lookup failed",
                        )
                    } else {
                        _state.value = WhoisUiState.Success(
                            whois = whoisResult.getOrNull(),
                            rdns = rdnsResult,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = WhoisUiState.Error(e.message ?: "Lookup failed")
            }
        }
    }

    private suspend fun resolveAndReverseDns(domain: String): RdnsResult? {
        val ip = domainResolver.resolve(domain) ?: return null
        return try {
            rdnsResolver.resolve(ip)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        val IP_REGEX = Regex("^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)$")

        fun looksLikeIp(input: String): Boolean = IP_REGEX.matches(input)
    }
}
