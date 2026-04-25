package com.ventoux.netlens.feature.ping.service

import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PingServiceControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : PingServiceController {

    private val notificationManager = PingNotificationManager(context)
    private val _stopRequested = MutableStateFlow(false)
    override val stopRequested: StateFlow<Boolean> = _stopRequested.asStateFlow()

    override fun start(host: String) {
        _stopRequested.value = false
        notificationManager.createChannel()
        ContinuousPingService.start(context, host)
    }

    override fun stop() {
        ContinuousPingService.stop(context)
        context.getSystemService(NotificationManager::class.java)
            .cancel(PingNotificationManager.NOTIFICATION_ID)
    }

    override fun requestStop() {
        _stopRequested.value = true
    }

    override fun updateNotification(host: String, sent: Int, lossPercent: Float) {
        val notification = notificationManager.buildNotification(host, sent, lossPercent)
        context.getSystemService(NotificationManager::class.java)
            .notify(PingNotificationManager.NOTIFICATION_ID, notification)
    }
}
