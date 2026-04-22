package us.beary.netlens.feature.whois

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import us.beary.netlens.feature.whois.engine.RdnsResolver
import us.beary.netlens.feature.whois.engine.WhoisClient
import us.beary.netlens.feature.whois.model.RdnsResult
import us.beary.netlens.feature.whois.model.WhoisResult
import us.beary.netlens.feature.whois.model.WhoisUiState
import java.net.InetAddress
import javax.inject.Inject

@HiltViewModel
class WhoisViewModel @Inject constructor(
    private val whoisClient: WhoisClient,
    private val rdnsResolver: RdnsResolver,
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
                    val whoisDeferred = async { runCatching { whoisClient.query(trimmed) } }
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
            } catch (e: Exception) {
                _state.value = WhoisUiState.Error(e.message ?: "Lookup failed")
            }
        }
    }

    private suspend fun resolveAndReverseDns(domain: String): RdnsResult? {
        return runCatching {
            val address = InetAddress.getByName(domain)
            val ip = address.hostAddress ?: return null
            rdnsResolver.resolve(ip)
        }.getOrNull()
    }

    private companion object {
        val IP_REGEX = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

        fun looksLikeIp(input: String): Boolean = IP_REGEX.matches(input)
    }
}
