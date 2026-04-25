package com.ventoux.netlens.feature.ping.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ventoux.netlens.feature.ping.R

class PingNotificationManager(private val context: Context) {

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.ping_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.description = context.getString(R.string.ping_notification_channel_desc)
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun buildNotification(host: String, sent: Int, lossPercent: Float): Notification {
        val stopIntent = Intent(context, ContinuousPingService::class.java)
            .setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            context, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ping_notification)
            .setContentTitle(context.getString(R.string.ping_notification_title, host))
            .setContentText(
                context.getString(R.string.ping_notification_text, sent, "%.0f".format(lossPercent)),
            )
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                context.getString(R.string.ping_notification_stop),
                stopPendingIntent,
            )
            .build()
    }

    companion object {
        const val CHANNEL_ID = "continuous_ping"
        const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "com.ventoux.netlens.ACTION_STOP_PING"
    }
}
