package com.ventoux.netlens.feature.whois

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ventoux.netlens.core.data.dao.WhoisHistoryDao
import com.ventoux.netlens.core.data.model.WhoisHistoryEntry
import com.ventoux.netlens.feature.whois.engine.DomainResolver
import com.ventoux.netlens.feature.whois.engine.RdnsResolver
import com.ventoux.netlens.feature.whois.engine.WhoisClient
import com.ventoux.netlens.feature.whois.model.RdnsResult
import com.ventoux.netlens.feature.whois.model.WhoisResult
import com.ventoux.netlens.feature.whois.model.WhoisUiState
import javax.inject.Inject

@HiltViewModel
class WhoisViewModel @Inject constructor(
    private val whoisClient: WhoisClient,
    private val rdnsResolver: RdnsResolver,
    private val domainResolver: DomainResolver,
    private val whoisHistoryDao: WhoisHistoryDao,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<WhoisUiState>(WhoisUiState.Idle)
    val state: StateFlow<WhoisUiState> = _state.asStateFlow()

    fun onQueryChanged(value: String) {
        _query.value = value
    }

    fun buildExportText(): String {
        val sb = StringBuilder()
        sb.appendLine("WHOIS for ${_query.value}:")
        val current = _state.value
        if (current is WhoisUiState.Success) {
            current.whois?.let { w ->
                w.registrar?.let { sb.appendLine("Registrar: $it") }
                w.createdDate?.let { sb.appendLine("Created: $it") }
                w.expiryDate?.let { sb.appendLine("Expires: $it") }
                if (w.nameServers.isNotEmpty()) {
                    sb.appendLine("Name Servers: ${w.nameServers.joinToString(", ")}")
                }
                sb.appendLine("--- Raw ---")
                sb.appendLine(w.rawResponse)
            }
            current.rdns?.let { r ->
                sb.appendLine("Reverse DNS for ${r.ip}:")
                r.hostnames.forEach { sb.appendLine("  $it") }
            }
        }
        return sb.toString().trimEnd()
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
                    saveToHistory(trimmed, null, rdns)
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
                        saveToHistory(trimmed, whoisResult.getOrNull(), rdnsResult)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = WhoisUiState.Error(e.message ?: "Lookup failed")
            }
        }
    }

    private fun saveToHistory(query: String, whois: WhoisResult?, rdns: RdnsResult?) {
        val response = whois?.rawResponse ?: rdns?.hostnames?.joinToString("\n") ?: return
        if (response.isBlank()) return
        viewModelScope.launch {
            whoisHistoryDao.insert(
                WhoisHistoryEntry(
                    query = query,
                    rawResponse = response,
                ),
            )
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
