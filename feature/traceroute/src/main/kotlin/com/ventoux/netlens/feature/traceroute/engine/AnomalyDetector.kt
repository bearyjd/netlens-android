package com.ventoux.netlens.feature.traceroute.engine

import com.ventoux.netlens.feature.traceroute.model.HopAnomaly
import com.ventoux.netlens.feature.traceroute.model.HopLocation
import com.ventoux.netlens.feature.traceroute.model.TracerouteHop
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

object AnomalyDetector {

    fun detect(hops: List<TracerouteHop>): List<List<HopAnomaly>> {
        return hops.mapIndexed { index, hop ->
            val anomalies = mutableListOf<HopAnomaly>()

            if (hop.isTimeout && index > 0 && hops[index - 1].isTimeout) {
                anomalies += HopAnomaly.ConsecutiveTimeout
            }

            if (!hop.isTimeout) {
                detectLatencySpike(index, hops)?.let { anomalies += it }
                detectGeoJump(index, hops)?.let { anomalies += it }
            }

            anomalies
        }
    }

    private fun detectLatencySpike(index: Int, hops: List<TracerouteHop>): HopAnomaly? {
        val currentRtt = hops[index].rttMs.firstOrNull() ?: return null
        val previousRtt = (index - 1 downTo 0)
            .firstNotNullOfOrNull { hops[it].rttMs.firstOrNull() }
            ?: return null

        val jump = currentRtt - previousRtt
        if (jump > LATENCY_SPIKE_THRESHOLD_MS && currentRtt > previousRtt * LATENCY_SPIKE_MULTIPLIER) {
            return HopAnomaly.LatencySpike
        }
        return null
    }

    private fun detectGeoJump(index: Int, hops: List<TracerouteHop>): HopAnomaly? {
        val currentLoc = hops[index].location ?: return null
        val previousLoc = (index - 1 downTo 0)
            .firstNotNullOfOrNull { hops[it].location }
            ?: return null

        if (currentLoc.latitude == 0.0 && currentLoc.longitude == 0.0) return null
        if (previousLoc.latitude == 0.0 && previousLoc.longitude == 0.0) return null

        val distanceKm = haversineKm(
            previousLoc.latitude, previousLoc.longitude,
            currentLoc.latitude, currentLoc.longitude,
        )

        if (distanceKm > GEO_JUMP_THRESHOLD_KM) {
            return HopAnomaly.GeoJump
        }
        return null
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val lon1Rad = Math.toRadians(lon1)
        val lon2Rad = Math.toRadians(lon2)
        val cosAngle = sin(lat1Rad) * sin(lat2Rad) +
            cos(lat1Rad) * cos(lat2Rad) * cos(lon2Rad - lon1Rad)
        return r * acos(cosAngle.coerceIn(-1.0, 1.0))
    }

    private const val LATENCY_SPIKE_THRESHOLD_MS = 50f
    private const val LATENCY_SPIKE_MULTIPLIER = 2.0f
    private const val GEO_JUMP_THRESHOLD_KM = 2000.0
}
