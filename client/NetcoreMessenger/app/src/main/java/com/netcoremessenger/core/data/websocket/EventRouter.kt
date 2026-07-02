package com.netcoremessenger.core.data.websocket

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRouter @Inject constructor(
    private val gson: Gson
) {
    private val _events = MutableSharedFlow<WebSocketEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val events: SharedFlow<WebSocketEvent> = _events

    fun route(envelope: WebSocketEnvelope) {
        val event = when (envelope.event) {
            "connected" -> {
                val data = gson.fromJson(envelope.data.toString(), JsonObject::class.java)
                WebSocketEvent.Connected(data.get("connection_id")?.asString ?: "")
            }
            "message.new" -> {
                val data = gson.fromJson(envelope.data.toString(), MessageNewData::class.java)
                WebSocketEvent.MessageNew(data)
            }
            "message.sent" -> {
                val data = gson.fromJson(envelope.data.toString(), MessageSentData::class.java)
                WebSocketEvent.MessageSent(data)
            }
            "message.delivered" -> {
                val data = gson.fromJson(envelope.data.toString(), MessageDeliveredData::class.java)
                WebSocketEvent.MessageDelivered(data)
            }
            "message.read" -> {
                val data = gson.fromJson(envelope.data.toString(), MessageDeliveredData::class.java)
                WebSocketEvent.MessageRead(data)
            }
            "typing.start" -> {
                val data = gson.fromJson(envelope.data.toString(), JsonObject::class.java)
                WebSocketEvent.TypingStart(
                    chatId = data.get("chat_id")?.asLong ?: return,
                    userId = data.get("user_id")?.asLong ?: return
                )
            }
            "typing.stop" -> {
                val data = gson.fromJson(envelope.data.toString(), JsonObject::class.java)
                WebSocketEvent.TypingStop(
                    chatId = data.get("chat_id")?.asLong ?: return,
                    userId = data.get("user_id")?.asLong ?: return
                )
            }
            "presence.online" -> {
                val data = gson.fromJson(envelope.data.toString(), JsonObject::class.java)
                WebSocketEvent.PresenceOnline(
                    userId = data.get("user_id")?.asLong ?: return,
                    lastSeen = data.get("last_seen")?.asLong
                )
            }
            "presence.offline" -> {
                val data = gson.fromJson(envelope.data.toString(), JsonObject::class.java)
                WebSocketEvent.PresenceOffline(
                    userId = data.get("user_id")?.asLong ?: return,
                    lastSeen = data.get("last_seen")?.asLong
                )
            }
            "chat.new" -> {
                val data = gson.fromJson(envelope.data.toString(), ChatNewData::class.java)
                WebSocketEvent.ChatNew(data)
            }
            "chat.updated" -> {
                val data = gson.fromJson(envelope.data.toString(), JsonObject::class.java)
                WebSocketEvent.ChatUpdated(chatId = data.get("id")?.asLong ?: return)
            }
            "call.start" -> {
                val data = gson.fromJson(envelope.data.toString(), JsonObject::class.java)
                WebSocketEvent.CallStart(
                    callId = data.get("call_id")?.asLong ?: return,
                    calleeId = data.get("callee_id")?.asLong ?: 0L
                )
            }
            "call.ringing" -> {
                val data = gson.fromJson(envelope.data.toString(), JsonObject::class.java)
                WebSocketEvent.CallRinging(
                    callId = data.get("call_id")?.asLong ?: return,
                    callerId = data.get("caller_id")?.asLong ?: return,
                    callType = data.get("call_type")?.asString ?: "audio",
                    chatId = data.get("chat_id")?.takeIf { !it.isJsonNull }?.asLong
                )
            }
            "call.accept" -> {
                val data = gson.fromJson(envelope.data.toString(), JsonObject::class.java)
                WebSocketEvent.CallAccept(callId = data.get("call_id")?.asLong ?: return)
            }
            "call.reject" -> {
                val data = gson.fromJson(envelope.data.toString(), JsonObject::class.java)
                WebSocketEvent.CallReject(callId = data.get("call_id")?.asLong ?: return)
            }
            "call.ended" -> {
                val data = gson.fromJson(envelope.data.toString(), JsonObject::class.java)
                WebSocketEvent.CallEnded(callId = data.get("call_id")?.asLong ?: return)
            }
            "webrtc.offer" -> {
                val data = gson.fromJson(envelope.data.toString(), JsonObject::class.java)
                WebSocketEvent.WebRtcOffer(
                    callId = data.get("call_id")?.asLong ?: 0L,
                    sdp = data.get("sdp")?.asString ?: return,
                    fromUserId = data.get("from_user_id")?.asLong ?: return
                )
            }
            "webrtc.answer" -> {
                val data = gson.fromJson(envelope.data.toString(), JsonObject::class.java)
                WebSocketEvent.WebRtcAnswer(
                    callId = data.get("call_id")?.asLong ?: 0L,
                    sdp = data.get("sdp")?.asString ?: return,
                    fromUserId = data.get("from_user_id")?.asLong ?: return
                )
            }
            "webrtc.ice" -> {
                val data = gson.fromJson(envelope.data.toString(), JsonObject::class.java)
                WebSocketEvent.WebRtcIce(
                    callId = data.get("call_id")?.asLong ?: 0L,
                    sdpMid = data.get("sdpMid")?.takeIf { !it.isJsonNull }?.asString,
                    sdpMLineIndex = data.get("sdpMLineIndex")?.asInt ?: 0,
                    candidate = data.get("candidate")?.asString ?: return,
                    fromUserId = data.get("from_user_id")?.asLong ?: return
                )
            }
            "media.ready" -> {
                val data = gson.fromJson(envelope.data.toString(), JsonObject::class.java)
                WebSocketEvent.MediaReady(
                    mediaId = data.get("media_id")?.asLong ?: return,
                    thumbnailUrl = data.get("thumbnail_url")?.asString,
                    width = data.get("width")?.asInt,
                    height = data.get("height")?.asInt,
                    durationMs = data.get("duration_ms")?.asLong
                )
            }
            "error" -> {
                val data = gson.fromJson(envelope.data.toString(), JsonObject::class.java)
                WebSocketEvent.Error(
                    code = data.get("code")?.asString ?: "UNKNOWN",
                    message = data.get("message")?.asString ?: "Unknown error"
                )
            }
            "sync.messages" -> {
                val data = gson.fromJson(envelope.data.toString(), JsonObject::class.java)
                val messagesArray = data.getAsJsonArray("messages")
                val messages = mutableListOf<MessageNewData>()
                for (element in messagesArray) {
                    messages.add(gson.fromJson(element, MessageNewData::class.java))
                }
                WebSocketEvent.SyncMessages(
                    messages = messages,
                    newLastSortKey = data.get("new_last_sort_key")?.asLong ?: return,
                    hasMore = data.get("has_more")?.asBoolean ?: false
                )
            }
            "pong" -> WebSocketEvent.Pong
            else -> return
        }
        _events.tryEmit(event)
    }
}
