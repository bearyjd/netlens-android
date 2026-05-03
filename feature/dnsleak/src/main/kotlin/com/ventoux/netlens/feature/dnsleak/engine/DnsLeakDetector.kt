package com.ventoux.netlens.feature.dnsleak.engine

import com.ventoux.netlens.feature.dnsleak.model.DnsLeakResult

interface DnsLeakDetector {
    suspend fun detect(vpnActive: Boolean): DnsLeakResult
}
