package com.netcoremessenger.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: Long,
    val type: String,
    val title: String?,
    val description: String?,
    @ColumnInfo(name = "avatar_media_id") val avatarMediaId: Long?,
    @ColumnInfo(name = "creator_id") val creatorId: Long?,
    @ColumnInfo(name = "invite_link") val inviteLink: String?,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false
)
