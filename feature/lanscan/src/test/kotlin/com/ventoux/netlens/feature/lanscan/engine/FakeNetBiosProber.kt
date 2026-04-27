package com.ventoux.netlens.feature.lanscan.engine

import com.ventoux.netlens.feature.lanscan.model.NetBiosInfo

class FakeNetBiosProber : NetBiosProber {
    var results: Map<String, NetBiosInfo> = emptyMap()

    override suspend fun probe(ip: String): NetBiosInfo? = results[ip]
}
