package com.ventouxlabs.netlens.core.scan.engine

class FakeArpTableReader : ArpTableReader {
    var table: Map<String, String> = emptyMap()

    override suspend fun getMacForIp(ip: String): String? = table[ip]

    override suspend fun getAll(): Map<String, String> = table

    override fun invalidateCache() {}
}
