package us.beary.netlens.feature.lanscan.engine

import java.io.File

/**
 * Reads the system ARP table from /proc/net/arp and returns a map of IP to MAC address.
 * Filters out incomplete entries (00:00:00:00:00:00).
 */
object ArpTableReader {

    private const val ARP_PATH = "/proc/net/arp"
    private const val INCOMPLETE_MAC = "00:00:00:00:00:00"

    fun read(): Map<String, String> {
        val arpFile = File(ARP_PATH)
        if (!arpFile.exists()) return emptyMap()

        return arpFile.useLines { lines ->
            lines
                .drop(1) // skip header line
                .mapNotNull { line -> parseLine(line) }
                .toMap()
        }
    }

    private fun parseLine(line: String): Pair<String, String>? {
        val columns = line.split("\\s+".toRegex())
        if (columns.size < 4) return null

        val ip = columns[0]
        val mac = columns[3]

        if (mac == INCOMPLETE_MAC || mac.isBlank()) return null

        return ip to mac
    }
}
