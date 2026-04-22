package us.beary.netlens.feature.wol.engine

interface WolSender {

    suspend fun sendMagicPacket(
        macAddress: String,
        broadcastIp: String = "255.255.255.255",
        port: Int = 9,
    ): Result<Unit>
}
