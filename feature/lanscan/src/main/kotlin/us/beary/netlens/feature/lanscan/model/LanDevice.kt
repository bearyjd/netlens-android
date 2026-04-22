package us.beary.netlens.feature.lanscan.model

data class LanDevice(
    val ip: String,
    val mac: String? = null,
    val vendor: String? = null,
    val hostname: String? = null,
    val isReachable: Boolean = true,
    val latencyMs: Long = 0,
)
