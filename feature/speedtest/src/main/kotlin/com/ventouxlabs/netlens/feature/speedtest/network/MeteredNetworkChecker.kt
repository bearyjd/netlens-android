package com.ventouxlabs.netlens.feature.speedtest.network

/** Seam over [android.net.ConnectivityManager] so ViewModel tests can fake metered state. */
interface MeteredNetworkChecker {
    fun isMetered(): Boolean
}
