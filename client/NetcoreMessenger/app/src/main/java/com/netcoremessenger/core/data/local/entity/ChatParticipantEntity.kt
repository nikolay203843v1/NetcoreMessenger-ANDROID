package com.netcoremessenger.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "chat_participants",
    primaryKeys = ["chat_id", "user_id"],
    foreignKeys = [
        ForeignKey(entity = ChatEntity::class, parentColumns = ["id"], childColumns = ["chat_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["user_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("chat_id"), Index("user_id")]
)
data class ChatParticipantEntity(
    @ColumnInfo(name = "chat_id") val chatId: Long,
    @ColumnInfo(name = "user_id") val userId: Long,
    val role: String,
    @ColumnInfo(name = "joined_at") val joinedAt: Long,
    @ColumnInfo(name = "is_typing") val isTyping: Boolean = false
)
