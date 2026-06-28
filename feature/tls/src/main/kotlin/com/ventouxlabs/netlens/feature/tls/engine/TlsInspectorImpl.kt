package com.ventouxlabs.netlens.feature.tls.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ventouxlabs.netlens.feature.tls.model.TlsCertInfo
import com.ventouxlabs.netlens.feature.tls.model.TlsInspectResult
import java.security.cert.X509Certificate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class TlsInspectorImpl @Inject constructor() : TlsInspector {

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        val HOST_PATTERN = Regex(
            "^(?:[a-zA-Z0-9](?:[a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)*" +
                "[a-zA-Z0-9](?:[a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?$",
        )
        private val DATE_PATTERN = "yyyy-MM-dd HH:mm:ss z"

        fun dateFormat(): DateTimeFormatter = DateTimeFormatter
            .ofPattern(DATE_PATTERN, Locale.US)
            .withZone(ZoneId.systemDefault())
    }

    override suspend fun inspect(host: String, port: Int): TlsInspectResult =
        withContext(Dispatchers.IO) {
            validateHost(host)
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            var socket: SSLSocket? = null
            try {
                socket = factory.createSocket() as SSLSocket
                socket.soTimeout = CONNECT_TIMEOUT_MS
                socket.connect(
                    java.net.InetSocketAddress(host, port),
                    CONNECT_TIMEOUT_MS,
                )
                socket.startHandshake()

                val session = socket.session
                val protocol = session.protocol
                val cipherSuite = session.cipherSuite
                val peerCerts = session.peerCertificates

                val certificates = peerCerts
                    .filterIsInstance<X509Certificate>()
                    .map { cert -> mapCertificate(cert) }

                TlsInspectResult(
                    host = host,
                    port = port,
                    protocol = protocol,
                    cipherSuite = cipherSuite,
                    certificates = certificates,
                )
            } finally {
                socket?.close()
            }
        }

    private fun validateHost(host: String) {
        require(
            host.isNotBlank() &&
                host.length <= 253 &&
                !host.startsWith("-") &&
                HOST_PATTERN.matches(host),
        ) {
            "Invalid host: must be a valid hostname"
        }
    }

    private fun mapCertificate(cert: X509Certificate): TlsCertInfo {
        val now = Date()
        val isExpired = now.after(cert.notAfter)
        val diffMs = cert.notAfter.time - now.time
        val daysUntilExpiry = if (isExpired) {
            -TimeUnit.MILLISECONDS.toDays(-diffMs)
        } else {
            TimeUnit.MILLISECONDS.toDays(diffMs)
        }
        val formatter = dateFormat()

        return TlsCertInfo(
            subjectCN = extractCN(cert.subjectX500Principal.name),
            issuerCN = extractCN(cert.issuerX500Principal.name),
            serialNumber = cert.serialNumber.toString(16),
            notBefore = formatter.format(cert.notBefore.toInstant()),
            notAfter = formatter.format(cert.notAfter.toInstant()),
            signatureAlgorithm = cert.sigAlgName,
            isExpired = isExpired,
            daysUntilExpiry = daysUntilExpiry,
        )
    }

    private fun extractCN(dn: String): String {
        return dn.split(",")
            .map { it.trim() }
            .firstOrNull { it.startsWith("CN=", ignoreCase = true) }
            ?.substringAfter("=")
            ?: dn
    }
}
