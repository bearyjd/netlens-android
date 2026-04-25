package com.ventoux.netlens.feature.ping.engine

import com.ventoux.netlens.feature.ping.model.PingResult
import com.ventoux.netlens.feature.ping.model.PingSummary

object PingOutputParser {

    private val REPLY_REGEX =
        Regex("""from\s+(\S+).*icmp_seq=(\d+).*ttl=(\d+).*time=(\d+\.?\d*)\s*ms""")

    private val TIMEOUT_REGEX =
        Regex("""icmp_seq=(\d+).*(?:timed?\s*out|no answer|Destination Host Unreachable)""", RegexOption.IGNORE_CASE)

    private val STATS_PACKETS_REGEX =
        Regex("""(\d+)\s+packets?\s+transmitted.*?(\d+)\s+received.*?(\d+\.?\d*)%\s+packet\s+loss""")

    private val STATS_RTT_REGEX =
        Regex("""min/avg/max/mdev\s*=\s*(\d+\.?\d*)/(\d+\.?\d*)/(\d+\.?\d*)/(\d+\.?\d*)\s*ms""")

    fun parseReplyLine(line: String): PingResult? {
        REPLY_REGEX.find(line)?.let { match ->
            val (ip, seq, ttl, time) = match.destructured
            return PingResult(
                sequenceNumber = seq.toInt(),
                latencyMs = time.toFloat(),
                ttl = ttl.toInt(),
                ip = ip,
            )
        }

        TIMEOUT_REGEX.find(line)?.let { match ->
            val seq = match.groupValues[1].toIntOrNull() ?: return null
            return PingResult(
                sequenceNumber = seq,
                isTimeout = true,
            )
        }

        return null
    }

    fun parseSummary(lines: List<String>): PingSummary? {
        var transmitted = 0
        var received = 0
        var lossPercent = 0f
        var minMs = 0f
        var avgMs = 0f
        var maxMs = 0f
        var foundPackets = false

        for (line in lines) {
            STATS_PACKETS_REGEX.find(line)?.let { match ->
                val (tx, rx, loss) = match.destructured
                transmitted = tx.toInt()
                received = rx.toInt()
                lossPercent = loss.toFloat()
                foundPackets = true
            }

            STATS_RTT_REGEX.find(line)?.let { match ->
                val (min, avg, max, _) = match.destructured
                minMs = min.toFloat()
                avgMs = avg.toFloat()
                maxMs = max.toFloat()
            }
        }

        return if (foundPackets) {
            PingSummary(
                transmitted = transmitted,
                received = received,
                lossPercent = lossPercent,
                minMs = minMs,
                avgMs = avgMs,
                maxMs = maxMs,
            )
        } else {
            null
        }
    }
}
