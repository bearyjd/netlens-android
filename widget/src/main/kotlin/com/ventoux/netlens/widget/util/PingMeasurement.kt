package com.ventoux.netlens.widget.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object PingMeasurement {
    private val recentPings = ArrayDeque<Int>(4)
    private val PING_REGEX = Regex("time=(\\d+\\.?\\d*)")

    suspend fun measure(host: String = "8.8.8.8"): Int? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(3000L) {
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("ping", "-c", "1", "-W", "2", host))
                val output = proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                parsePingOutput(output)
            } catch (_: Exception) { null }
        }
    }

    fun parsePingOutput(output: String): Int? {
        val match = PING_REGEX.find(output) ?: return null
        return match.groupValues[1].toFloatOrNull()?.toInt()
    }

    fun record(ms: Int) {
        if (recentPings.size >= 3) recentPings.removeFirst()
        recentPings.addLast(ms)
    }

    fun smoothed(): Int? {
        if (recentPings.isEmpty()) return null
        return recentPings.average().toInt()
    }

    fun clear() { recentPings.clear() }
}
