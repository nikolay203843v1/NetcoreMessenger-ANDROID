package com.netcoremessenger.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(entity = ChatEntity::class, parentColumns = ["id"], childColumns = ["chat_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("chat_id"), Index("sort_key"), Index("client_id")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long? = null,
    @ColumnInfo(name = "client_id") val clientId: String?,
    @ColumnInfo(name = "chat_id") val chatId: Long,
    @ColumnInfo(name = "sender_id") val senderId: Long,
    val type: String,
    val content: String,
    @ColumnInfo(name = "album_id") val albumId: String? = null,
    @ColumnInfo(name = "reply_to_msg_id") val replyToMsgId: Long?,
    @ColumnInfo(name = "sort_key") val sortKey: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val status: String,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "next_retry_at") val nextRetryAt: Long = 0
)
