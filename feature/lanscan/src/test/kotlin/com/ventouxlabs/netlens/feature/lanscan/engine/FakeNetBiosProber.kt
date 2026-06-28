package com.ventouxlabs.netlens.feature.lanscan.engine

import com.ventouxlabs.netlens.feature.lanscan.model.NetBiosInfo

class FakeNetBiosProber : NetBiosProber {
    var results: Map<String, NetBiosInfo> = emptyMap()

    override suspend fun probe(ip: String): NetBiosInfo? = results[ip]
}
