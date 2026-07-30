package com.ventouxlabs.netlens.core.scan.engine

import com.ventouxlabs.netlens.core.scan.model.NetBiosInfo

class FakeNetBiosProber : NetBiosProber {
    var results: Map<String, NetBiosInfo> = emptyMap()

    override suspend fun probe(ip: String): NetBiosInfo? = results[ip]
}
