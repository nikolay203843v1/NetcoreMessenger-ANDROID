package com.netcoremessenger.core.call

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.netcoremessenger.MainActivity
import com.netcoremessenger.R
import com.netcoremessenger.core.notification.NotificationChannels
import com.netcoremessenger.core.notification.NotificationIds
import com.netcoremessenger.core.notification.ensureNotificationChannels

class CallForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        ensureNotificationChannels(this)
        val isVideo = intent?.getBooleanExtra(EXTRA_IS_VIDEO, false) == true
        val openApp = android.app.PendingIntent.getActivity(
            this,
            NotificationIds.ACTIVE_CALL,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, NotificationChannels.CALLS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (isVideo) "Идет видеозвонок" else "Идет звонок")
            .setContentText("Нажмите, чтобы вернуться к звонку")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (isVideo) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NotificationIds.ACTIVE_CALL, notification, type)
        } else {
            startForeground(NotificationIds.ACTIVE_CALL, notification)
        }
        return START_STICKY
    }

    companion object {
        const val ACTION_STOP = "com.netcoremessenger.action.CALL_SERVICE_STOP"
        const val EXTRA_IS_VIDEO = "extra_is_video"
    }
}
