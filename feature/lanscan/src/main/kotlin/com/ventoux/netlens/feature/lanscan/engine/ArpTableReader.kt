package com.ventoux.netlens.feature.lanscan.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface ArpTableReader {
    suspend fun getMacForIp(ip: String): String?
    suspend fun getAll(): Map<String, String>
    fun invalidateCache()
}

@Singleton
class ArpTableReaderImpl @Inject constructor() : ArpTableReader {

    private val mutex = Mutex()
    private var cache: Map<String, String>? = null

    override suspend fun getMacForIp(ip: String): String? = getAll()[ip]

    override suspend fun getAll(): Map<String, String> {
        cache?.let { return it }
        return mutex.withLock {
            cache?.let { return it }
            val result = readArpTable()
            cache = result
            result
        }
    }

    override fun invalidateCache() {
        cache = null
    }

    private suspend fun readArpTable(): Map<String, String> = withContext(Dispatchers.IO) {
        val file = File("/proc/net/arp")
        if (!file.exists()) return@withContext emptyMap()
        parseArpTable(file.readLines())
    }

    companion object {
        internal fun parseArpTable(lines: List<String>): Map<String, String> {
            if (lines.size <= 1) return emptyMap()
            val result = mutableMapOf<String, String>()
            for (line in lines.drop(1)) {
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size < 4) continue
                val ip = parts[0]
                val mac = parts[3].uppercase()
                if (mac == "00:00:00:00:00:00" || !mac.contains(":")) continue
                result[ip] = mac
            }
            return result
        }
    }
}
