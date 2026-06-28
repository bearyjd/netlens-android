package com.ventouxlabs.netlens.feature.dnsleak.engine

import com.ventouxlabs.netlens.feature.dnsleak.model.DnsLeakResult

interface DnsLeakDetector {
    suspend fun detect(vpnActive: Boolean): DnsLeakResult
}
