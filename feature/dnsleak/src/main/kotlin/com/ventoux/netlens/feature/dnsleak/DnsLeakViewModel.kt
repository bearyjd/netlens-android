package com.ventoux.netlens.feature.dnsleak

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ventoux.netlens.core.data.dao.NetworkEventDao
import com.ventoux.netlens.core.data.model.NetworkEvent
import com.ventoux.netlens.core.network.NetworkMonitor
import com.ventoux.netlens.core.network.VpnState
import com.ventoux.netlens.feature.dnsleak.engine.DnsLeakDetector
import com.ventoux.netlens.feature.dnsleak.model.DnsLeakResult
import com.ventoux.netlens.feature.dnsleak.model.DnsLeakUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DnsLeakViewModel @Inject constructor(
    private val dnsLeakDetector: DnsLeakDetector,
    private val networkMonitor: NetworkMonitor,
    private val networkEventDao: NetworkEventDao,
) : ViewModel() {

    private val _state = MutableStateFlow(DnsLeakUiState())
    val state: StateFlow<DnsLeakUiState> = _state.asStateFlow()

    private var testJob: Job? = null

    fun runTest() {
        testJob?.cancel()
        _state.update { it.copy(isLoading = true, result = null) }
        testJob = viewModelScope.launch {
            val vpnActive = networkMonitor.vpnState.first() !is VpnState.None
            _state.update { it.copy(vpnActive = vpnActive) }

            val result = dnsLeakDetector.detect(vpnActive)

            val systemServers = when (result) {
                is DnsLeakResult.NoLeak -> result.resolvers.map { it.ip }
                is DnsLeakResult.LeakDetected ->
                    (result.leakedResolvers + result.expectedResolvers).map { it.ip }
                is DnsLeakResult.Error -> emptyList()
            }

            _state.update {
                it.copy(
                    isLoading = false,
                    result = result,
                    systemDnsServers = systemServers,
                )
            }

            logResult(result, vpnActive)
        }
    }

    fun buildExportText(): String {
        val current = _state.value
        val sb = StringBuilder()
        sb.appendLine("DNS Leak Test Results")
        sb.appendLine("=====================")
        sb.appendLine("VPN Active: ${if (current.vpnActive) "Yes" else "No"}")
        sb.appendLine()

        when (val result = current.result) {
            is DnsLeakResult.NoLeak -> {
                sb.appendLine("Status: No DNS Leak Detected")
                sb.appendLine()
                sb.appendLine("DNS Resolvers:")
                result.resolvers.forEach { r ->
                    sb.appendLine("  ${r.ip} - ${r.name}")
                }
            }
            is DnsLeakResult.LeakDetected -> {
                sb.appendLine("Status: DNS LEAK DETECTED")
                sb.appendLine()
                sb.appendLine("Leaked Resolvers:")
                result.leakedResolvers.forEach { r ->
                    sb.appendLine("  ${r.ip} - ${r.name}")
                }
                if (result.expectedResolvers.isNotEmpty()) {
                    sb.appendLine()
                    sb.appendLine("Expected Resolvers:")
                    result.expectedResolvers.forEach { r ->
                        sb.appendLine("  ${r.ip} - ${r.name}")
                    }
                }
            }
            is DnsLeakResult.Error -> {
                sb.appendLine("Status: Error")
                sb.appendLine("Details: ${result.message}")
            }
            null -> {
                sb.appendLine("Status: Not tested")
            }
        }
        return sb.toString().trimEnd()
    }

    private suspend fun logResult(result: DnsLeakResult, vpnActive: Boolean) {
        try {
            val (eventType, details) = when (result) {
                is DnsLeakResult.NoLeak -> "dns_leak_pass" to
                    "No DNS leak detected. Resolvers: ${result.resolvers.joinToString { "${it.ip} (${it.name})" }}"
                is DnsLeakResult.LeakDetected -> "dns_leak_fail" to
                    "DNS leak detected. Leaked: ${result.leakedResolvers.joinToString { "${it.ip} (${it.name})" }}"
                is DnsLeakResult.Error -> "dns_leak_error" to "Error: ${result.message}"
            }
            networkEventDao.insert(
                NetworkEvent(
                    eventType = eventType,
                    transportType = if (vpnActive) "VPN" else "Default",
                    networkDetails = details,
                    isVpn = vpnActive,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Logging failure should not affect test result
        }
    }
}
