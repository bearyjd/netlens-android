package com.ventouxlabs.netlens.feature.lanscan.engine

import com.ventouxlabs.netlens.feature.lanscan.model.ScanCoordinates

/**
 * Private to `:feature:lanscan` because it is the only consumer today. If a second module ever
 * needs it, move it to a shared `-testing` module rather than copying — every copied fake in
 * this repo has drifted weaker than the original.
 *
 * [error] exists because the real provider must swallow `SecurityException` (permission revoked
 * mid-read) and `IllegalArgumentException` (provider absent) and report "no location" instead.
 * A fake that could only return null could not tell a swallowed throw from a missing fix.
 */
class FakeScanLocationProvider : ScanLocationProvider {
    var result: ScanCoordinates? = null
    var error: Throwable? = null
    var callCount: Int = 0

    override suspend fun current(): ScanCoordinates? {
        callCount++
        error?.let { throw it }
        return result
    }
}
