package com.ventouxlabs.netlens.core.scan.engine

/**
 * Records whether the process was "bound" while a block ran.
 *
 * The real binding cannot be exercised on the JVM, so what a test can assert is the wiring: that
 * scan work happens *inside* [withLanNetwork] rather than around it. Read [isBound] from inside a
 * fake engine to check that — see `LanScanVpnBindingTest`.
 */
class FakeLanNetworkBinder : LanNetworkBinder {

    /** True only while a [withLanNetwork] block is running. */
    var isBound: Boolean = false
        private set

    /** How many times [withLanNetwork] has been entered. */
    var bindCount: Int = 0
        private set

    /**
     * Whether a local network exists to bind to. Set to false to reproduce the VPN-with-no-local-
     * route case, where the real binder falls back to running the block unbound.
     */
    var lanNetworkAvailable: Boolean = true

    override suspend fun <T> withLanNetwork(block: suspend (bound: Boolean) -> T): T {
        bindCount++
        isBound = lanNetworkAvailable
        return try {
            block(lanNetworkAvailable)
        } finally {
            isBound = false
        }
    }
}
