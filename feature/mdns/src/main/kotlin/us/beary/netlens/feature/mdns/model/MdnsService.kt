package us.beary.netlens.feature.mdns.model

data class MdnsService(
    val serviceName: String,
    val serviceType: String,
    val host: String? = null,
    val port: Int = 0,
    val attributes: Map<String, String> = emptyMap(),
)
