package com.ventouxlabs.netlens.feature.tls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ventouxlabs.netlens.core.data.dao.TlsHistoryDao
import com.ventouxlabs.netlens.core.data.model.TlsHistoryEntry
import com.ventouxlabs.netlens.feature.tls.engine.TlsInspector
import com.ventouxlabs.netlens.feature.tls.model.TlsUiState
import javax.inject.Inject

@HiltViewModel
class TlsViewModel @Inject constructor(
    private val inspector: TlsInspector,
    private val tlsHistoryDao: TlsHistoryDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TlsUiState>(TlsUiState.Idle)
    val uiState: StateFlow<TlsUiState> = _uiState.asStateFlow()

    fun buildExportText(): String {
        val sb = StringBuilder()
        val current = _uiState.value
        if (current is TlsUiState.Success) {
            val r = current.result
            sb.appendLine("TLS Inspector for ${r.host}:${r.port}:")
            sb.appendLine("Protocol: ${r.protocol}")
            sb.appendLine("Cipher: ${r.cipherSuite}")
            r.certificates.forEachIndexed { i, cert ->
                sb.appendLine("--- Certificate ${i + 1} ---")
                sb.appendLine("Subject: ${cert.subjectCN}")
                sb.appendLine("Issuer: ${cert.issuerCN}")
                sb.appendLine("Valid: ${cert.notBefore} to ${cert.notAfter}")
                sb.appendLine("Expired: ${cert.isExpired} (${cert.daysUntilExpiry} days)")
                sb.appendLine("Algorithm: ${cert.signatureAlgorithm}")
            }
        }
        return sb.toString().trimEnd()
    }

    fun inspect(host: String, port: Int = 443) {
        viewModelScope.launch {
            _uiState.value = TlsUiState.Loading
            try {
                val result = inspector.inspect(host, port)
                _uiState.value = TlsUiState.Success(result)
                saveToHistory(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = TlsUiState.Error(
                    e.localizedMessage ?: "TLS inspection failed",
                )
            }
        }
    }

    private suspend fun saveToHistory(result: com.ventouxlabs.netlens.feature.tls.model.TlsInspectResult) {
        val cert = result.certificates.firstOrNull() ?: return
        tlsHistoryDao.insert(
            TlsHistoryEntry(
                host = result.host,
                port = result.port,
                issuer = cert.issuerCN,
                subject = cert.subjectCN,
                expiresAt = cert.notAfter,
                protocol = result.protocol,
                isValid = !cert.isExpired,
            ),
        )
    }
}
