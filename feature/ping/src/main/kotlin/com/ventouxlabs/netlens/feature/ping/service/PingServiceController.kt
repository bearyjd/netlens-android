package com.ventouxlabs.netlens.feature.ping.service

import kotlinx.coroutines.flow.StateFlow

interface PingServiceController {
    val stopRequested: StateFlow<Boolean>
    fun start(host: String)
    fun stop()
    fun requestStop()
    fun updateNotification(host: String, sent: Int, lossPercent: Float)
}
