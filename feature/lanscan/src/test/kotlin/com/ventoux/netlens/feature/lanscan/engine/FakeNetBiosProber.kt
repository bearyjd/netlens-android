package com.ventoux.netlens.feature.lanscan.engine

class FakeNetBiosProber : NetBiosProber {
    var results: Map<String, NetBiosInfo> = emptyMap()

    override suspend fun probe(ip: String): NetBiosInfo? = results[ip]
}
