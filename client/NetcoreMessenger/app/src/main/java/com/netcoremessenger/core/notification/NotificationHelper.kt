package com.netcoremessenger.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.content.getSystemService

object NotificationChannels {
    const val MESSAGES = "messages"
    const val CALLS = "calls"
}

object NotificationIds {
    const val INCOMING_CALL = 1001
    const val ACTIVE_CALL = 1002
    fun forChat(chatId: Long): Int = (2_000_000 + chatId).toInt()
}

fun ensureNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val nm = context.getSystemService<NotificationManager>() ?: return

    if (nm.getNotificationChannel(NotificationChannels.MESSAGES) == null) {
        val ch = NotificationChannel(
            NotificationChannels.MESSAGES,
            "Сообщения",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Новые сообщения"
            enableLights(true)
            enableVibration(true)
            val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            setSound(
                sound,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
            )
        }
        nm.createNotificationChannel(ch)
    }

    if (nm.getNotificationChannel(NotificationChannels.CALLS) == null) {
        val ch = NotificationChannel(
            NotificationChannels.CALLS,
            "Звонки",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Входящие звонки"
            enableLights(true)
            enableVibration(true)
            setBypassDnd(true)
            val ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            setSound(
                ringtone,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        nm.createNotificationChannel(ch)
    }
}
