package com.ventouxlabs.netlens.feature.lanscan.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.ventouxlabs.netlens.core.data.di.DefaultDispatcher
import com.ventouxlabs.netlens.feature.lanscan.model.ScanCoordinates
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Reads the newest cached fix from the platform's own providers.
 *
 * **AOSP `LocationManager`, deliberately not `FusedLocationProviderClient`.** Fused lives in
 * Google Play Services, which does not exist in the `foss` flavor and would disqualify the app
 * from F-Droid. Do not "upgrade" this.
 *
 * `getLastKnownLocation` rather than `requestLocationUpdates`: tagging a scan needs one
 * position, not a stream, and subscribing to updates for a single read is a battery regression.
 */
class ScanLocationProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : ScanLocationProvider {

    override suspend fun current(): ScanCoordinates? = withContext(dispatcher) {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext null
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        // runCatching, not a bare call: getLastKnownLocation throws SecurityException if the
        // permission is revoked between the check above and the read, and IllegalArgumentException
        // if a provider is absent on the device. Both mean "no location", not "fail the scan".
        val location = runCatching {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { manager.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
        }.getOrNull() ?: return@withContext null

        ScanCoordinates(location.latitude, location.longitude)
    }
}
