package com.ventoux.netlens.feature.wol.engine

class FakeWolSender : WolSender {
    var result: Result<Unit> = Result.success(Unit)

    override suspend fun sendMagicPacket(macAddress: String, broadcastIp: String, port: Int): Result<Unit> = result
}
