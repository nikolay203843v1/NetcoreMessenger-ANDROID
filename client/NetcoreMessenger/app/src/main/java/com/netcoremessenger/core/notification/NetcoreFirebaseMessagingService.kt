package com.netcoremessenger.core.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.app.Person
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.netcoremessenger.MainActivity
import com.netcoremessenger.R
import com.netcoremessenger.core.call.CallManager
import com.netcoremessenger.core.data.remote.api.AuthApi
import com.netcoremessenger.core.data.remote.dto.PushTokenRequest
import com.netcoremessenger.core.data.store.TokenDataStore
import com.netcoremessenger.core.data.websocket.ConnectionState
import com.netcoremessenger.core.data.websocket.WebSocketManager
import com.netcoremessenger.core.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URL
import javax.inject.Inject

@AndroidEntryPoint
class NetcoreFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var authApi: AuthApi
    @Inject lateinit var tokenDataStore: TokenDataStore
    @Inject lateinit var callManager: CallManager
    @Inject lateinit var webSocketManager: WebSocketManager

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onNewToken(token: String) {
        scope.launch {
            if (tokenDataStore.accessToken.first().isNullOrBlank()) return@launch
            runCatching { authApi.updatePushToken(PushTokenRequest(token)) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        ensureNotificationChannels(this)
        val data = message.data
        when (data["type"]) {
            "incoming_call" -> {
                if (!webSocketManager.isAppForeground() || webSocketManager.connectionState.value != ConnectionState.CONNECTED) {
                    showIncomingCallPush(data)
                }
            }
            "call_cancelled" -> {
                val callId = data["call_id"]?.toLongOrNull()
                if (callId != null && callManager.current.value?.callId == callId) {
                    callManager.endCall(notifyPeer = false)
                }
                NotificationManagerCompat.from(this).cancel(NotificationIds.INCOMING_CALL)
            }
            "message" -> {
                if (!webSocketManager.isAppForeground() || webSocketManager.connectionState.value != ConnectionState.CONNECTED) {
                    showMessagePush(data)
                }
            }
        }
    }

    private fun hasPostPermission(): Boolean {
        return android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun showMessagePush(data: Map<String, String>) {
        if (!hasPostPermission()) return
        val chatId = data["chat_id"]?.toLongOrNull() ?: return
        val senderName = data["sender_name"] ?: data["title"] ?: "Новое сообщение"
        val text = when (data["message_kind"]) {
            "image" -> "Фото"
            "voice" -> "Голосовое сообщение"
            "video" -> "Видео"
            "circle" -> "Видеокружок"
            else -> data["content"] ?: data["body"] ?: ""
        }
        val avatar = loadAvatar(data["sender_avatar_media_id"])
        val sender = Person.Builder()
            .setName(senderName)
            .apply { avatar?.let { setIcon(IconCompat.createWithBitmap(it)) } }
            .build()

        val openChat = PendingIntent.getActivity(
            this,
            chatId.toInt(),
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("chat_id", chatId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NotificationChannels.MESSAGES)
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
            .setContentIntent(openChat)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(this).notify(NotificationIds.forChat(chatId), notification)
    }

    private fun showIncomingCallPush(data: Map<String, String>) {
        if (!hasPostPermission()) return
        val callId = data["call_id"]?.toLongOrNull() ?: return
        val callerId = data["caller_id"]?.toLongOrNull() ?: return
        val isVideo = data["call_type"] == "video"
        val chatId = data["chat_id"]?.toLongOrNull()?.takeIf { it > 0L }
        val callerName = data["caller_name"] ?: data["body"] ?: "Входящий звонок"
        val avatar = loadAvatar(data["caller_avatar_media_id"])

        callManager.restoreIncomingCall(callId, callerId, isVideo, chatId)

        val reject = Intent(this, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_REJECT
            putExtra(CallActionReceiver.EXTRA_CALL_ID, callId)
            putExtra(CallActionReceiver.EXTRA_CALLER_ID, callerId)
            putExtra(CallActionReceiver.EXTRA_IS_VIDEO, isVideo)
            putExtra(CallActionReceiver.EXTRA_CHAT_ID, chatId ?: 0L)
        }
        val piAccept = PendingIntent.getActivity(
            this,
            callId.toInt(),
            Intent(this, MainActivity::class.java).apply {
                action = CallActionReceiver.ACTION_ACCEPT
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(CallActionReceiver.EXTRA_CALL_ID, callId)
                putExtra(CallActionReceiver.EXTRA_CALLER_ID, callerId)
                putExtra(CallActionReceiver.EXTRA_IS_VIDEO, isVideo)
                putExtra(CallActionReceiver.EXTRA_CHAT_ID, chatId ?: 0L)
                putExtra(CallActionReceiver.EXTRA_OPEN_CALL, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val piReject = PendingIntent.getBroadcast(
            this, callId.toInt() + 1, reject, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openApp = PendingIntent.getActivity(
            this,
            callId.toInt(),
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("call_id", callId)
                putExtra("caller_id", callerId)
                putExtra("is_video", isVideo)
                putExtra("chat_id", chatId ?: 0L)
                putExtra("open_call", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NotificationChannels.CALLS)
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

        NotificationManagerCompat.from(this).notify(NotificationIds.INCOMING_CALL, notification)
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
