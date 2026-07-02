package com.netcoremessenger.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.netcoremessenger.core.call.CallManager
import com.netcoremessenger.core.data.repository.AuthRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CallActionReceiver : BroadcastReceiver() {

    @Inject lateinit var callManager: CallManager
    @Inject lateinit var authRepository: AuthRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val callId = intent.getLongExtra(EXTRA_CALL_ID, 0L)
        val callerId = intent.getLongExtra(EXTRA_CALLER_ID, 0L)
        val isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
        val chatId = intent.getLongExtra(EXTRA_CHAT_ID, 0L).takeIf { it > 0L }
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                if (callId > 0 && callerId > 0) {
                    callManager.restoreIncomingCall(
                        callId = callId,
                        callerUserId = callerId,
                        isVideo = isVideo,
                        chatId = chatId
                    )
                }
                authRepository.connectWebSocket()
                when (intent.action) {
                    ACTION_ACCEPT -> {
                        callManager.acceptIncoming()
                        context.startActivity(Intent(context, com.netcoremessenger.MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra(EXTRA_CALL_ID, callId)
                            putExtra(EXTRA_CALLER_ID, callerId)
                            putExtra(EXTRA_IS_VIDEO, isVideo)
                            putExtra(EXTRA_CHAT_ID, chatId ?: 0L)
                            putExtra(EXTRA_OPEN_CALL, true)
                        })
                    }
                    ACTION_REJECT -> callManager.rejectIncoming()
                }
                NotificationManagerCompat.from(context).cancel(NotificationIds.INCOMING_CALL)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_ACCEPT = "com.netcoremessenger.action.CALL_ACCEPT"
        const val ACTION_REJECT = "com.netcoremessenger.action.CALL_REJECT"
        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_CALLER_ID = "extra_caller_id"
        const val EXTRA_IS_VIDEO = "extra_is_video"
        const val EXTRA_CHAT_ID = "extra_chat_id"
        const val EXTRA_OPEN_CALL = "extra_open_call"
    }
}
