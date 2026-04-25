package us.beary.netlens.feature.ping.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ContinuousPingService : Service() {

    @Inject lateinit var serviceController: PingServiceController

    private lateinit var notificationManager: PingNotificationManager
    private var currentHost = ""

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = PingNotificationManager(this)
        notificationManager.createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == PingNotificationManager.ACTION_STOP) {
            serviceController.requestStop()
            stopSelf()
            return START_NOT_STICKY
        }

        val host = intent?.getStringExtra(EXTRA_HOST) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }

        if (currentHost == host) return START_NOT_STICKY
        currentHost = host

        val notification = notificationManager.buildNotification(host, 0, 0f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                PingNotificationManager.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(PingNotificationManager.NOTIFICATION_ID, notification)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        currentHost = ""
    }

    companion object {
        const val EXTRA_HOST = "host"

        fun start(context: Context, host: String) {
            val intent = Intent(context, ContinuousPingService::class.java)
                .putExtra(EXTRA_HOST, host)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ContinuousPingService::class.java))
        }
    }
}
