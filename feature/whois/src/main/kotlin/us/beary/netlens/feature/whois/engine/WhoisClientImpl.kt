package us.beary.netlens.feature.whois.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import us.beary.netlens.feature.whois.model.WhoisResult
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import javax.inject.Inject

class WhoisClientImpl @Inject constructor() : WhoisClient {

    override suspend fun query(domain: String): WhoisResult = withContext(Dispatchers.IO) {
        val trimmed = domain.trim().lowercase()
        validateDomain(trimmed)

        val ianaResponse = rawQuery(IANA_HOST, trimmed)
        val referServer = parseRefer(ianaResponse)

        val registrarResponse = if (referServer != null) {
            rawQuery(referServer, trimmed)
        } else {
            ianaResponse
        }

        parseWhoisResponse(trimmed, registrarResponse)
    }

    private fun validateDomain(domain: String) {
        require(domain.isNotBlank()) { "Domain must not be blank" }
        require(!domain.startsWith("-")) { "Domain must not start with a dash" }
        require(HOSTNAME_REGEX.matches(domain)) {
            "Invalid domain format: $domain"
        }
    }

    private fun rawQuery(host: String, query: String): String {
        return Socket(host, WHOIS_PORT).use { socket ->
            socket.soTimeout = TIMEOUT_MS
            val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
            writer.write("$query\r\n")
            writer.flush()
            BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                .readText()
                .take(MAX_RESPONSE_BYTES)
        }
    }

    private fun parseRefer(response: String): String? {
        val raw = response.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("refer:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return validateReferServer(raw)
    }

    private fun parseWhoisResponse(domain: String, raw: String): WhoisResult {
        val registrar = extractField(raw, REGISTRAR_PATTERNS)
        val createdDate = extractField(raw, CREATED_PATTERNS)
        val expiryDate = extractField(raw, EXPIRY_PATTERNS)
        val nameServers = extractAllFields(raw, NAME_SERVER_PATTERNS)

        return WhoisResult(
            domain = domain,
            registrar = registrar,
            createdDate = createdDate,
            expiryDate = expiryDate,
            nameServers = nameServers,
            rawResponse = raw,
        )
    }

    private fun extractField(raw: String, patterns: List<Regex>): String? {
        for (pattern in patterns) {
            val match = raw.lineSequence()
                .firstNotNullOfOrNull { line -> pattern.find(line) }
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return null
    }

    private fun extractAllFields(raw: String, patterns: List<Regex>): List<String> {
        val results = mutableSetOf<String>()
        for (pattern in patterns) {
            raw.lineSequence().forEach { line ->
                pattern.find(line)?.let { match ->
                    val value = match.groupValues[1].trim().lowercase()
                    if (value.isNotBlank()) {
                        results.add(value)
                    }
                }
            }
        }
        return results.toList()
    }

    private companion object {
        const val IANA_HOST = "whois.iana.org"
        const val WHOIS_PORT = 43
        const val TIMEOUT_MS = 10_000
        const val MAX_RESPONSE_BYTES = 65_536

        val REFER_HOSTNAME_REGEX = Regex(
            "^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$"
        )

        private val BLOCKED_IP_PATTERNS = listOf(
            Regex("^127\\."),
            Regex("^10\\."),
            Regex("^192\\.168\\."),
            Regex("^172\\.(1[6-9]|2\\d|3[01])\\."),
            Regex("^0\\."),
            Regex("^localhost$", RegexOption.IGNORE_CASE),
            Regex("^\\[?::1]?$"),
            Regex("^\\[?fd[0-9a-fA-F]{2}:"),
            Regex("^\\[?fe80:"),
        )

        fun validateReferServer(server: String): String {
            require(REFER_HOSTNAME_REGEX.matches(server)) {
                "Invalid refer server hostname: $server"
            }
            require(BLOCKED_IP_PATTERNS.none { it.containsMatchIn(server) }) {
                "Refer server points to a blocked address: $server"
            }
            return server
        }

        val HOSTNAME_REGEX = Regex(
            "^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$"
        )

        val REGISTRAR_PATTERNS = listOf(
            Regex("^\\s*Registrar:\\s*(.+)", RegexOption.IGNORE_CASE),
            Regex("^\\s*Registrar Name:\\s*(.+)", RegexOption.IGNORE_CASE),
            Regex("^\\s*Sponsoring Registrar:\\s*(.+)", RegexOption.IGNORE_CASE),
        )

        val CREATED_PATTERNS = listOf(
            Regex("^\\s*Creation Date:\\s*(.+)", RegexOption.IGNORE_CASE),
            Regex("^\\s*Created Date:\\s*(.+)", RegexOption.IGNORE_CASE),
            Regex("^\\s*Registration Date:\\s*(.+)", RegexOption.IGNORE_CASE),
            Regex("^\\s*Created:\\s*(.+)", RegexOption.IGNORE_CASE),
        )

        val EXPIRY_PATTERNS = listOf(
            Regex("^\\s*Registry Expiry Date:\\s*(.+)", RegexOption.IGNORE_CASE),
            Regex("^\\s*Expiration Date:\\s*(.+)", RegexOption.IGNORE_CASE),
            Regex("^\\s*Expiry Date:\\s*(.+)", RegexOption.IGNORE_CASE),
            Regex("^\\s*Expires:\\s*(.+)", RegexOption.IGNORE_CASE),
        )

        val NAME_SERVER_PATTERNS = listOf(
            Regex("^\\s*Name Server:\\s*(.+)", RegexOption.IGNORE_CASE),
            Regex("^\\s*nserver:\\s*(.+)", RegexOption.IGNORE_CASE),
        )
    }
}
