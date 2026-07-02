package com.netcoremessenger.core.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import androidx.core.app.Person
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.netcoremessenger.MainActivity
import com.netcoremessenger.R
import com.netcoremessenger.core.call.CallManager
import com.netcoremessenger.core.call.CallState
import com.netcoremessenger.core.data.remote.api.UsersApi
import com.netcoremessenger.core.data.store.TokenDataStore
import com.netcoremessenger.core.data.websocket.WebSocketEvent
import com.netcoremessenger.core.data.websocket.WebSocketManager
import com.netcoremessenger.core.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val webSocketManager: WebSocketManager,
    private val callManager: CallManager,
    private val tokenDataStore: TokenDataStore,
    private val usersApi: UsersApi
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var ringtonePlayer: MediaPlayer? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        ensureNotificationChannels(context)

        // Сообщения
        scope.launch {
            webSocketManager.events.collect { ev ->
                if (ev is WebSocketEvent.MessageNew) {
                    val myId = tokenDataStore.userId.first()
                    if (ev.data.sender_id != myId && !webSocketManager.isAppForeground()) {
                        showMessageNotification(ev.data.chat_id, ev.data.sender_id, ev.data.content, ev.data.type)
                    }
                }
            }
        }

        // Входящие звонки + рингтон
        scope.launch {
            callManager.current.collect { info ->
                when (info?.state) {
                    CallState.RINGING_IN -> {
                        showIncomingCallNotification(info.callId, info.peerUserId, info.isVideo)
                        startRingtone()
                    }
                    CallState.RINGING_OUT -> {
                        startRingtone()
                    }
                    CallState.ACTIVE, CallState.CONNECTING, CallState.ENDED, null -> {
                        stopRingtone()
                        NotificationManagerCompat.from(context).cancel(NotificationIds.INCOMING_CALL)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun hasPostPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU
    }

    private fun showMessageNotification(chatId: Long, senderId: Long, content: String, type: String) {
        if (!hasPostPermission()) return
        scope.launch(Dispatchers.IO) {
            val user = runCatching { usersApi.getUser(senderId) }.getOrNull()
            val senderName = user?.displayName ?: user?.username ?: "Новое сообщение"
            val avatar = loadAvatar(user?.avatarMediaId?.toString())
            val text = when (type) {
                "image" -> "Фото"
                "voice" -> "Голосовое сообщение"
                "video" -> "Видео"
                "circle" -> "Видеокружок"
                else -> content
            }
            val sender = Person.Builder()
                .setName(senderName)
                .apply { avatar?.let { setIcon(IconCompat.createWithBitmap(it)) } }
                .build()
            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("chat_id", chatId)
            }
            val pending = PendingIntent.getActivity(
                context, chatId.toInt(), tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = NotificationCompat.Builder(context, NotificationChannels.MESSAGES)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(senderName)
                .setContentText(text)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setStyle(
                    NotificationCompat.MessagingStyle(sender)
                        .setConversationTitle(senderName)
                        .addMessage(text, System.currentTimeMillis(), sender)
                )
                .setLargeIcon(avatar)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            NotificationManagerCompat.from(context).notify(NotificationIds.forChat(chatId), notif)
        }
    }

    private fun showIncomingCallNotification(callId: Long, callerId: Long, isVideo: Boolean) {
        if (!hasPostPermission()) return
        scope.launch(Dispatchers.IO) {
            val user = runCatching { usersApi.getUser(callerId) }.getOrNull()
            val callerName = user?.displayName ?: user?.username ?: "Входящий звонок"
            val avatar = loadAvatar(user?.avatarMediaId?.toString())
            val chatId = callManager.current.value?.chatId ?: 0L

            val reject = Intent(context, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_REJECT
                putExtra(CallActionReceiver.EXTRA_CALL_ID, callId)
                putExtra(CallActionReceiver.EXTRA_CALLER_ID, callerId)
                putExtra(CallActionReceiver.EXTRA_IS_VIDEO, isVideo)
                putExtra(CallActionReceiver.EXTRA_CHAT_ID, chatId)
            }
            val piAccept = PendingIntent.getActivity(
                context,
                callId.toInt(),
                Intent(context, MainActivity::class.java).apply {
                    action = CallActionReceiver.ACTION_ACCEPT
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(CallActionReceiver.EXTRA_CALL_ID, callId)
                    putExtra(CallActionReceiver.EXTRA_CALLER_ID, callerId)
                    putExtra(CallActionReceiver.EXTRA_IS_VIDEO, isVideo)
                    putExtra(CallActionReceiver.EXTRA_CHAT_ID, chatId)
                    putExtra(CallActionReceiver.EXTRA_OPEN_CALL, true)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val piReject = PendingIntent.getBroadcast(
                context, 2, reject, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val openApp = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(CallActionReceiver.EXTRA_CALL_ID, callId)
                    putExtra(CallActionReceiver.EXTRA_CALLER_ID, callerId)
                    putExtra(CallActionReceiver.EXTRA_IS_VIDEO, isVideo)
                    putExtra(CallActionReceiver.EXTRA_CHAT_ID, chatId)
                    putExtra(CallActionReceiver.EXTRA_OPEN_CALL, true)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notif = NotificationCompat.Builder(context, NotificationChannels.CALLS)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(avatar)
                .setContentTitle(if (isVideo) "Входящий видеозвонок" else "Входящий звонок")
                .setContentText(callerName)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setAutoCancel(false)
                .setFullScreenIntent(openApp, true)
                .setContentIntent(openApp)
                .addAction(android.R.drawable.ic_menu_call, "Принять", piAccept)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отклонить", piReject)
                .build()
            NotificationManagerCompat.from(context).notify(NotificationIds.INCOMING_CALL, notif)
        }
    }

    private fun startRingtone() {
        stopRingtone()
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtonePlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                setLooping(true)
                prepare()
                start()
            }
        } catch (_: Throwable) {}
    }

    private fun stopRingtone() {
        try {
            ringtonePlayer?.stop()
            ringtonePlayer?.release()
        } catch (_: Throwable) {}
        ringtonePlayer = null
    }

    private fun loadAvatar(mediaId: String?): Bitmap? {
        val id = mediaId?.toLongOrNull() ?: return null
        return runCatching {
            val token = kotlinx.coroutines.runBlocking { tokenDataStore.accessToken.first() }
            val conn = URL("${Constants.BASE_URL}/api/v1/media/$id").openConnection() as java.net.HttpURLConnection
            if (!token.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.inputStream.use(BitmapFactory::decodeStream)?.toCircle()
        }.getOrNull()
    }

    private fun Bitmap.toCircle(): Bitmap {
        val size = minOf(width, height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val src = Rect((width - size) / 2, (height - size) / 2, (width + size) / 2, (height + size) / 2)
        val dst = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawOval(dst, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(this, src, dst, paint)
        return output
    }
}
