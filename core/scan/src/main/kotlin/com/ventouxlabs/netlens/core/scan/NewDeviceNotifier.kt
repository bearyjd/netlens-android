package com.ventouxlabs.netlens.core.scan

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface NewDeviceNotifier {
    fun createChannel()
    fun notify(device: KnownDeviceEntity)
}

@Singleton
class NewDeviceNotifierImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : NewDeviceNotifier {

    override fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.lanscan_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.lanscan_notification_channel_desc)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    override fun notify(device: KnownDeviceEntity) {
        createChannel()
        val title = context.getString(R.string.lanscan_notification_new_device_title)
        val text = context.getString(
            R.string.lanscan_notification_new_device_text,
            device.hostname ?: device.ip,
            device.vendor ?: context.getString(R.string.lanscan_notification_unknown_vendor),
        )
        val deepLink = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("netlens://feature/devices"),
        ).setPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context,
            device.id.hashCode(),
            deepLink,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_new_device_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context)
                .notify(device.id.hashCode(), notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "new_device_detected"
    }
}
