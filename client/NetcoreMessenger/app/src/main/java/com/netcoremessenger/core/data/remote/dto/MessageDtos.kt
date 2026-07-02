package com.netcoremessenger.core.data.remote.dto

import com.google.gson.annotations.SerializedName

data class MessageDto(
    @SerializedName("id") val id: Long,
    @SerializedName("client_id") val clientId: String?,
    @SerializedName("chat_id") val chatId: Long,
    @SerializedName("sender_id") val senderId: Long,
    @SerializedName("type") val type: String,
    @SerializedName("content") val content: String,
    @SerializedName("album_id") val albumId: String?,
    @SerializedName("reply_to_msg_id") val replyToMsgId: Long?,
    @SerializedName("sort_key") val sortKey: Long,
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("status") val status: String?
)

data class SendMessageRequest(
    @SerializedName("chat_id") val chatId: Long,
    @SerializedName("client_id") val clientId: String,
    @SerializedName("type") val type: String,
    @SerializedName("content") val content: String,
    @SerializedName("album_id") val albumId: String? = null,
    @SerializedName("reply_to_msg_id") val replyToMsgId: Long? = null
)

data class EditMessageRequest(
    @SerializedName("content") val content: String
)

data class ForwardMessageRequest(
    @SerializedName("chat_id") val chatId: Long,
    @SerializedName("type") val type: String = "text",
    @SerializedName("content") val content: String = "",
    @SerializedName("client_id") val clientId: String? = null,
    @SerializedName("album_id") val albumId: String? = null,
    @SerializedName("reply_to_msg_id") val replyToMsgId: Long? = null
)
