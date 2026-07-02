package com.netcoremessenger.core.data.websocket

sealed class WebSocketEvent {
    data class Connected(val connectionId: String) : WebSocketEvent()
    data class MessageNew(val data: MessageNewData) : WebSocketEvent()
    data class MessageSent(val data: MessageSentData) : WebSocketEvent()
    data class MessageDelivered(val data: MessageDeliveredData) : WebSocketEvent()
    data class MessageRead(val data: MessageDeliveredData) : WebSocketEvent()
    data class TypingStart(val chatId: Long, val userId: Long) : WebSocketEvent()
    data class TypingStop(val chatId: Long, val userId: Long) : WebSocketEvent()
    data class PresenceOnline(val userId: Long, val lastSeen: Long?) : WebSocketEvent()
    data class PresenceOffline(val userId: Long, val lastSeen: Long?) : WebSocketEvent()
    data class ChatNew(val data: ChatNewData) : WebSocketEvent()
    data class ChatUpdated(val chatId: Long) : WebSocketEvent()
    data class CallRinging(val callId: Long, val callerId: Long, val callType: String, val chatId: Long?) : WebSocketEvent()
    data class CallStart(val callId: Long, val calleeId: Long) : WebSocketEvent()
    data class CallAccept(val callId: Long) : WebSocketEvent()
    data class CallReject(val callId: Long) : WebSocketEvent()
    data class CallEnded(val callId: Long) : WebSocketEvent()
    data class WebRtcOffer(val callId: Long, val sdp: String, val fromUserId: Long) : WebSocketEvent()
    data class WebRtcAnswer(val callId: Long, val sdp: String, val fromUserId: Long) : WebSocketEvent()
    data class WebRtcIce(val callId: Long, val sdpMid: String?, val sdpMLineIndex: Int, val candidate: String, val fromUserId: Long) : WebSocketEvent()
    data class MediaReady(val mediaId: Long, val thumbnailUrl: String?, val width: Int?, val height: Int?, val durationMs: Long?) : WebSocketEvent()
    data class SyncMessages(val messages: List<MessageNewData>, val newLastSortKey: Long, val hasMore: Boolean) : WebSocketEvent()
    data class Error(val code: String, val message: String) : WebSocketEvent()
    data object Pong : WebSocketEvent()
}

data class MessageNewData(
    val id: Long,
    val client_id: String?,
    val chat_id: Long,
    val sender_id: Long,
    val type: String,
    val content: String,
    val album_id: String?,
    val reply_to_msg_id: Long? = null,
    val sort_key: Long,
    val created_at: Long,
    val status: String? = null
)

data class MessageSentData(
    val client_id: String,
    val id: Long,
    val sort_key: Long
)

data class MessageDeliveredData(
    val message_id: Long,
    val chat_id: Long,
    val user_id: Long,
    val status_at: Long
)

data class ChatNewData(
    val id: Long,
    val type: String,
    val participants: List<Long>,
    val created_at: Long
)
